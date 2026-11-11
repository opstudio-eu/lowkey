package com.rheerkens.androidkeyboard.dictionary

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.io.Writer
import java.util.zip.GZIPInputStream

/**
 * Converts an AOSP LatinIME `<locale>_wordlist.combined.gz` into the keyboard's `.dict` text
 * (`word<TAB>frequency` lines, then `#whitelist` and `from<TAB>to` lines). This is the same
 * conversion as `scripts/build-dictionaries.py`, which builds the bundled lists; a unit test and
 * `scripts/test_build_dictionaries.py` check both against one fixture. Pure Kotlin.
 *
 * Offensive and "not a word" entries are dropped. From the whitelist only contractions of
 * non-words (`im` → `I'm`) and corrections of non-words are kept, never a swap of a valid word.
 * At most [MAX_WORDS] words are kept, the most frequent ones.
 *
 * The source is read in passes rather than held in memory: Romanian's list unpacks to 50 MB and
 * 1.1 million words, which would not fit next to the keyboard in the app's heap. The first pass
 * counts entries per frequency and collects the whitelist; the next keeps only words frequent
 * enough to make the cut, so memory stays near [MAX_WORDS] entries.
 */
object AospWordList {
    /** Romanian's list has 1.1 million inflected forms; keep the lexicon's memory bounded. */
    const val MAX_WORDS = 250_000

    fun convertGzip(gz: ByteArray): String = StringWriter().also { convertGzip(gz, it) }.toString()

    /**
     * Converts the gzipped source [gz] into [out], streaming it once per pass. [checkCancelled]
     * runs every few thousand lines and may throw to stop.
     */
    fun convertGzip(gz: ByteArray, out: Writer, checkCancelled: () -> Unit = {}) =
        convert({ GZIPInputStream(ByteArrayInputStream(gz)).bufferedReader(Charsets.UTF_8) }, out, checkCancelled)

    fun convert(combined: String): String =
        StringWriter().also { out -> convert({ combined.reader().buffered() }, out) }.toString()

    private class Shortcut(val source: String, val target: String, val sourceNotAWord: Boolean)

    /** Converts the source that [open] reads (from the start, each call) into [out]. */
    fun convert(open: () -> BufferedReader, out: Writer, checkCancelled: () -> Unit = {}) {
        // Pass 1: entries per frequency, and whitelist shortcuts in file order.
        val perFrequency = LongArray(256)
        val shortcuts = ArrayList<Shortcut>()
        forEachEntry(open, checkCancelled, onWord = { _, fields, _ ->
            if (keeps(fields)) perFrequency[frequency(fields).coerceIn(0, 255)]++
        }) { current, fields ->
            val source = current["word"]
            val target = fields["shortcut"]
            if (fields["f"] == "whitelist" && source != null && target != null) {
                shortcuts += Shortcut(source, target, current["not_a_word"] == "true")
            }
        }
        // A whitelist entry is dropped if its source is also a valid word (`ill` → `I'll`).
        val shortcutSources = shortcuts.mapTo(HashSet()) { it.source.lowercase() }
        val validSources = HashSet<String>()
        // Pass 2 (repeated only if duplicates leave too few words): words at or above a cutoff.
        var cutoff = cutoff(perFrequency, MAX_WORDS.toLong())
        var words: HashMap<String, Int>
        while (true) {
            val kept = HashMap<String, Int>()
            val minimum = cutoff
            forEachEntry(open, checkCancelled, onWord = { raw, fields, word ->
                if ("not_a_word=true" !in raw && "offensive" !in raw) {
                    val lower = word.lowercase()
                    if (lower in shortcutSources) validSources += lower
                }
                if (keeps(fields)) {
                    val f = frequency(fields)
                    if (minimum <= 0 || f >= minimum) {
                        val prev = kept[word]
                        if (prev == null || prev < f) kept[word] = f
                    }
                }
            })
            words = kept
            // Every word with a frequency at or above the cutoff is kept, so once there are at
            // least MAX_WORDS of them no word below it can make the cut.
            if (words.size >= MAX_WORDS || minimum <= 0) break
            cutoff = minimum - 1
        }
        val whitelist = LinkedHashMap<String, String>()
        for (s in shortcuts) {
            if (s.sourceNotAWord || ('\'' in s.target && s.source.lowercase() !in validSources)) whitelist[s.source] = s.target
        }
        words.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenComparing({ it.key }, CODE_POINTS))
            .take(MAX_WORDS)
            .forEach { out.append(it.key).append('\t').append(it.value.toString()).append('\n') }
        out.append("#whitelist\n")
        whitelist.entries.sortedWith(compareBy(CODE_POINTS) { it.key })
            .forEach { out.append(it.key).append('\t').append(it.value).append('\n') }
        out.flush()
    }

    /** The highest frequency at which at least [limit] entries are at or above it, else 0. */
    private fun cutoff(perFrequency: LongArray, limit: Long): Int {
        var total = 0L
        for (f in 255 downTo 0) {
            total += perFrequency[f]
            if (total >= limit) return f
        }
        return 0
    }

    private fun keeps(fields: Map<String, String>) =
        fields["not_a_word"] != "true" && "offensive" !in fields["flags"].orEmpty()

    private fun frequency(fields: Map<String, String>) = fields["f"]?.takeIf { it.isNotEmpty() }?.toInt() ?: 0

    /** Reads each ` word=` line (and the `  shortcut=` lines under it) of the source once. */
    private inline fun forEachEntry(
        open: () -> BufferedReader,
        checkCancelled: () -> Unit,
        onWord: (raw: String, fields: Map<String, String>, word: String) -> Unit,
        onShortcut: (current: Map<String, String>, fields: Map<String, String>) -> Unit = { _, _ -> },
    ) {
        open().use { reader ->
            var current: Map<String, String>? = null
            var lines = 0
            while (true) {
                val raw = reader.readLine() ?: break
                if (++lines % CANCEL_CHECK_LINES == 0) checkCancelled()
                if (raw.startsWith(WORD)) {
                    val fields = fields(raw)
                    current = fields
                    val word = fields["word"] ?: continue
                    onWord(raw, fields, word)
                } else if (raw.startsWith(SHORTCUT) && current != null) {
                    onShortcut(current, fields(raw))
                }
            }
        }
    }

    /** `word=x,f=12,flags=` → its fields, as Python's `dict(p.split("=", 1) ...)` reads them. */
    private fun fields(raw: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (part in raw.trim().split(',')) {
            val eq = part.indexOf('=')
            if (eq >= 0) out[part.substring(0, eq)] = part.substring(eq + 1)
        }
        return out
    }

    /** Orders strings by Unicode code point, as Python's `sorted` does (not by UTF-16 unit). */
    private val CODE_POINTS = Comparator<String> { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a.codePointAt(i)
            val cb = b.codePointAt(j)
            if (ca != cb) return@Comparator ca.compareTo(cb)
            i += Character.charCount(ca)
            j += Character.charCount(cb)
        }
        (a.length - i).compareTo(b.length - j)
    }

    private const val CANCEL_CHECK_LINES = 10_000
    private const val WORD = " word="
    private const val SHORTCUT = "  shortcut="
}
