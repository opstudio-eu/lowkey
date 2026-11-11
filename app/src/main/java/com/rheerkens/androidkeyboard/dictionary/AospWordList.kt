package com.rheerkens.androidkeyboard.dictionary

import java.io.ByteArrayInputStream
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
 */
object AospWordList {
    /** Romanian's list has 1.1 million inflected forms; keep the lexicon's memory bounded. */
    const val MAX_WORDS = 250_000

    fun convertGzip(gz: ByteArray): String =
        convert(GZIPInputStream(ByteArrayInputStream(gz)).use { String(it.readBytes(), Charsets.UTF_8) })

    fun convert(combined: String): String {
        val lines = combined.lines()
        val validLower = HashSet<String>()
        for (raw in lines) {
            if (raw.startsWith(WORD) && "not_a_word=true" !in raw && "offensive" !in raw) {
                validLower += raw.substringBefore(',').substring(WORD.length).lowercase()
            }
        }
        val words = HashMap<String, Int>()
        val whitelist = HashMap<String, String>()
        var current: Map<String, String>? = null
        for (raw in lines) {
            if (raw.startsWith(WORD)) {
                val fields = fields(raw)
                current = fields
                val word = fields["word"] ?: continue
                if (fields["not_a_word"] == "true" || "offensive" in fields["flags"].orEmpty()) continue
                val freq = fields["f"]?.takeIf { it.isNotEmpty() }?.toInt() ?: 0
                val prev = words[word]
                if (prev == null || prev < freq) words[word] = freq
            } else if (raw.startsWith(SHORTCUT) && current != null) {
                val fields = fields(raw)
                if (fields["f"] != "whitelist") continue
                val source = current["word"] ?: continue
                val target = fields["shortcut"] ?: continue
                if (current["not_a_word"] == "true" || ('\'' in target && source.lowercase() !in validLower)) {
                    whitelist[source] = target
                }
            }
        }
        return buildString {
            words.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenComparing({ it.key }, CODE_POINTS))
                .take(MAX_WORDS)
                .forEach { append(it.key).append('\t').append(it.value).append('\n') }
            append("#whitelist\n")
            whitelist.entries.sortedWith(compareBy(CODE_POINTS) { it.key })
                .forEach { append(it.key).append('\t').append(it.value).append('\n') }
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

    private const val WORD = " word="
    private const val SHORTCUT = "  shortcut="
}
