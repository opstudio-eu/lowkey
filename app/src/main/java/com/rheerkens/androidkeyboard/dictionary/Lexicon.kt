package com.rheerkens.androidkeyboard.dictionary

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

/** Autocorrect rules that differ between languages (`languages.txt`). */
data class SpellingRules(
    /**
     * Frequency lead (0–255 scale) at which a known word typed without accents is still
     * corrected to its accented form (Polish `juz` → `już`); 0 turns this off.
     */
    val accentMargin: Int = 0,
    /**
     * Nouns are capitalized (German): a lowercase word whose only dictionary spelling is
     * capitalized gets that spelling (`zeit` → `Zeit`), and typos may correct to capitalized
     * words. Elsewhere a lowercase word never becomes a capitalized one (`lol` → `Lil`).
     */
    val capitalizedNouns: Boolean = false,
)

/**
 * A language's word list with frequencies (0–255), built from the AOSP LatinIME dictionaries by
 * `scripts/build-dictionaries.py` (bundled) or [AospWordList] (downloaded). Supports exact lookups,
 * frequency-ranked prefix completion that ignores accents the user left out, and candidate lookup
 * by length for spelling correction. Pure Kotlin.
 *
 * Stored compactly, as a phone keeps two or three of these in memory: the lowercase forms of all
 * words, sorted, in one character block with offsets; a byte per frequency; the spelling only of
 * words that differ from their lowercase form (names, German nouns); and ready-made strings only
 * for the [CORRECTION_POOL] words the spelling search scans on every keystroke. Lookups are binary
 * searches over the block.
 */
class Lexicon private constructor(
    /** Lowercase forms, sorted, back to back: word `i` is `lowerChars[lowerStart[i] until lowerStart[i + 1]]`. */
    private val lowerChars: CharArray,
    private val lowerStart: IntArray,
    /** Frequency of word `i`, 0–255 as an unsigned byte. */
    private val freqBytes: ByteArray,
    /** Words whose spelling differs from their lowercase form: their indices, ascending, and spellings. */
    private val spelledAt: IntArray,
    private val spelled: Array<String>,
    /** Safe whitelist corrections such as `dont` -> `don't` (keys lowercase). */
    val whitelist: Map<String, String>,
    /** The language's letter layout: key neighbours and accents without their own key. */
    val folding: AccentFolding,
    /** Language-specific autocorrect rules from the catalog. */
    val rules: SpellingRules = SpellingRules(),
    /** [poolOrder] and [foldedOrder] as a saved copy stored them, to skip sorting ([read]). */
    savedPool: IntArray? = null,
    savedFolded: IntArray? = null,
    hasSavedFolded: Boolean = false,
) {
    val size: Int get() = freqBytes.size

    private fun freq(i: Int): Int = freqBytes[i].toInt() and 0xFF

    private fun lowerString(i: Int): String = String(lowerChars, lowerStart[i], lowerStart[i + 1] - lowerStart[i])

    private fun word(i: Int): String {
        val k = spelledAt.binarySearch(i)
        return if (k >= 0) spelled[k] else lowerString(i)
    }

    /** The spelling search's candidates of one length, most frequent first. */
    private class Pool(val indices: IntArray, val words: Array<String>, val lower: Array<String>)

    /** The [CORRECTION_POOL] most frequent words, most frequent first (equal ones in list order). */
    private val poolOrder: IntArray = savedPool ?: (0 until size).sortedByDescending { freq(it) }.take(CORRECTION_POOL).toIntArray()

    /** Built alongside the word list on its loader thread, never lazily on a Space press. */
    private val byLength: Map<Int, Pool> = run {
        poolOrder.asList()
            .groupBy { lowerStart[it + 1] - lowerStart[it] }
            .mapValues { (_, v) ->
                val lower = Array(v.size) { lowerString(v[it]) }
                val words = Array(v.size) { word(v[it]).let { w -> if (w == lower[it]) lower[it] else w } }
                Pool(v.toIntArray(), words, lower)
            }
    }

    /**
     * Word indices sorted by their accent-folded lowercase form (`żółw` → `zolw`), so `zol`
     * completes `żółw`. Null when folding changes no word (English).
     */
    private val foldedOrder: IntArray? = if (hasSavedFolded) savedFolded else run {
        if (lowerChars.all { folding.fold(it) == it }) return@run null
        val folded = Array(size) { folding.fold(lowerString(it)) }
        (0 until size).sortedBy { folded[it] }.toIntArray()
    }

    /** Compares word [i]'s lowercase form, accent-folded if [fold], with [p], as `String.compareTo` would. */
    private fun compare(i: Int, p: String, fold: Boolean): Int {
        val start = lowerStart[i]
        val length = lowerStart[i + 1] - start
        for (k in 0 until minOf(length, p.length)) {
            val c = lowerChars[start + k].let { if (fold) folding.fold(it) else it }
            if (c != p[k]) return c - p[k]
        }
        return length - p.length
    }

    /** True if word [i]'s (folded) lowercase form starts with [p]. */
    private fun startsWith(i: Int, p: String, fold: Boolean): Boolean {
        val start = lowerStart[i]
        if (lowerStart[i + 1] - start < p.length) return false
        for (k in p.indices) {
            val c = lowerChars[start + k].let { if (fold) folding.fold(it) else it }
            if (c != p[k]) return false
        }
        return true
    }

    /** The first position in [order] (word indices; all words in order when null) whose form is not below [p]. */
    private fun lowerBound(p: String, order: IntArray?): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compare(order?.get(mid) ?: mid, p, fold = order != null) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The most frequent word whose lowercase form is [lower] (the first on a tie), or -1. */
    private fun find(lower: String): Int {
        var i = lowerBound(lower, null)
        var best = -1
        while (i < size && compare(i, lower, fold = false) == 0) {
            if (best < 0 || freq(best) < freq(i)) best = i
            i++
        }
        return best
    }

    /** True if a word is stored with exactly the spelling [word]. */
    private fun hasSpelling(word: String): Boolean {
        val lower = word.lowercase()
        var i = lowerBound(lower, null)
        while (i < size && compare(i, lower, fold = false) == 0) {
            if (word(i) == word) return true
            i++
        }
        return false
    }

    /** Frequency of [word] (case-insensitive), or null if unknown. */
    fun frequency(word: String): Int? = find(word.lowercase()).takeIf { it >= 0 }?.let(::freq)

    /** The dictionary's spelling of [word] (e.g. `android` -> `Android` if stored capitalized). */
    fun spelling(word: String): String? = find(word.lowercase()).takeIf { it >= 0 }?.let(::word)

    fun contains(word: String): Boolean = find(word.lowercase()) >= 0

    /** True if [word] is stored with exactly this spelling, or as a lowercase word it capitalizes. */
    fun containsExact(word: String): Boolean =
        hasSpelling(word) || (word.firstOrNull()?.isUpperCase() == true && hasSpelling(word.lowercase()))

    /**
     * Up to [limit] words starting with [prefix] (case-insensitive), most frequent first. Letters
     * typed without their accent also match accented ones (`dzie` → `dziękuję`, `żól` → `żółw`);
     * a letter typed with an accent only matches that accent.
     */
    fun completions(prefix: String, limit: Int): List<Pair<String, Int>> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val best = ArrayList<Int>()
        val order = foldedOrder
        if (order != null) {
            // Search without accents, then keep the accents the user did type (`żól` → `żółw`).
            val fp = folding.fold(p)
            var lo = lowerBound(fp, order)
            while (lo < size && startsWith(order[lo], fp, fold = true)) {
                val i = order[lo]
                val start = lowerStart[i]
                if (lowerStart[i + 1] - start > p.length && p.indices.all { k -> p[k] == fp[k] || lowerChars[start + k] == p[k] }) best += i
                lo++
            }
        } else {
            var lo = lowerBound(p, null)
            while (lo < size && startsWith(lo, p, fold = false)) {
                if (lowerStart[lo + 1] - lowerStart[lo] > p.length) best += lo
                lo++
            }
        }
        return best.sortedByDescending { freq(it) }.take(limit).map { word(it) to freq(it) }
    }

    /** Visits every word whose accent-folded lowercase form is exactly [folded]. */
    internal fun forEachAccentedForm(folded: String, visit: (String, String, Int) -> Unit) {
        val order = foldedOrder ?: return
        var i = lowerBound(folded, order)
        while (i < size && compare(order[i], folded, fold = true) == 0) {
            val w = order[i]
            visit(word(w), lowerString(w), freq(w))
            i++
        }
    }

    /**
     * The accented spelling to use for [typed], a known word typed without accents, when that
     * spelling is at least [SpellingRules.accentMargin] more frequent (`juz` → `już`), or null.
     * Off unless the language enables it: in Spanish or Italian the plain form is often a
     * different everyday word (`presento`, `trovo`).
     */
    fun accentRestoration(typed: String): String? {
        val accentMargin = rules.accentMargin
        if (accentMargin <= 0 || typed.length <= 2) return null
        val t = typed.lowercase()
        if (folding.fold(t) != t) return null // the user typed accents on purpose
        val own = frequency(t) ?: return null
        var best: String? = null
        var bestFreq = own + accentMargin - 1
        forEachAccentedForm(t) { word, lower, freq ->
            // Never turn a lowercase word into a capitalized name (`odra`, measles, is not the river `Odrą`).
            val capitalizes = word[0].isUpperCase() && typed[0].isLowerCase()
            if (lower != t && freq > bestFreq && !capitalizes) {
                best = word
                bestFreq = freq
            }
        }
        return best
    }

    /** Visits eligible candidates without per-word Sequence/Pair/boxed-index allocations. */
    internal fun forEachCandidate(range: IntRange, checkActive: () -> Unit = {},
        accept: (String) -> Boolean, visit: (String, String, Int) -> Unit) {
        var visited = 0
        for (len in range) {
            val pool = byLength[len] ?: continue
            for (k in pool.indices.indices) {
                if (visited++ % 128 == 0) checkActive()
                if (accept(pool.lower[k])) visit(pool.words[k], pool.lower[k], freq(pool.indices[k]))
            }
        }
    }

    /**
     * Saves this list in the binary form [read] loads in a few milliseconds, instead of parsing
     * and sorting the text list again after every start of the keyboard. [tag] names the source
     * it was built from; [read] returns null when asked for another.
     */
    fun write(out: OutputStream, tag: String) {
        val data = DataOutputStream(BufferedOutputStream(out, 1 shl 16))
        data.writeInt(MAGIC)
        data.writeUTF(tag)
        fun ints(a: IntArray) { data.writeInt(a.size); val b = ByteBuffer.allocate(a.size * 4); b.asIntBuffer().put(a); data.write(b.array()) }
        fun text(s: String) { val b = s.toByteArray(Charsets.UTF_8); data.writeInt(b.size); data.write(b) }
        text(String(lowerChars))
        ints(lowerStart)
        data.writeInt(freqBytes.size); data.write(freqBytes)
        ints(spelledAt)
        text(spelled.joinToString("\n"))
        text(whitelist.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        ints(poolOrder)
        data.writeBoolean(foldedOrder != null)
        foldedOrder?.let(::ints)
        data.flush()
    }

    /** This lexicon plus [extra] words (frequency [frequency]) that it doesn't already know. */
    fun withExtraWords(extra: Collection<String>, frequency: Int): Lexicon {
        val added = extra.distinctBy { it.lowercase() }.filter { !contains(it) }
        if (added.isEmpty()) return this
        val entries = (0 until size).map { word(it) to freq(it) } + added.map { it to frequency }
        return build(entries, whitelist, folding, rules)
    }

    companion object {
        /** Words considered as correction targets: the most frequent ones. */
        const val CORRECTION_POOL = 60_000

        val EMPTY = fromWords(emptyList())

        /** Parses the `word<TAB>freq` + `#whitelist` format (optionally gzipped by the caller). */
        fun parse(input: InputStream, folding: AccentFolding = AccentFolding.QWERTY, rules: SpellingRules = SpellingRules()): Lexicon {
            val entries = ArrayList<Pair<String, Int>>(200_000)
            val whitelist = HashMap<String, String>()
            var inWhitelist = false
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).forEachLine { line ->
                if (line == "#whitelist") {
                    inWhitelist = true
                    return@forEachLine
                }
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEachLine
                val a = line.substring(0, tab)
                val b = line.substring(tab + 1)
                if (inWhitelist) whitelist[a.lowercase()] = b else entries += a to (b.toIntOrNull() ?: 0)
            }
            return build(entries, whitelist, folding, rules)
        }

        fun parseGzip(input: InputStream, folding: AccentFolding = AccentFolding.QWERTY, rules: SpellingRules = SpellingRules()): Lexicon =
            GZIPInputStream(input).use { parse(it, folding, rules) }

        /** Format of [write]; a saved list of another format or [tag] reads as null. */
        private const val MAGIC = 0x4C4B5801 // "LKX", version 1

        /**
         * A list saved by [write] with this [tag] and [folding] (the language's, which the saved
         * accent index depends on), or null if [input] holds another format or source. Throws on
         * a damaged file.
         */
        fun read(input: InputStream, tag: String, folding: AccentFolding, rules: SpellingRules = SpellingRules()): Lexicon? {
            val data = DataInputStream(BufferedInputStream(input, 1 shl 16))
            if (data.readInt() != MAGIC || data.readUTF() != tag) return null
            fun bytes(): ByteArray = ByteArray(data.readInt()).also(data::readFully)
            fun ints(): IntArray {
                val raw = ByteArray(data.readInt() * 4).also(data::readFully)
                return IntArray(raw.size / 4).also { ByteBuffer.wrap(raw).asIntBuffer().get(it) }
            }
            fun text(): String = String(bytes(), Charsets.UTF_8)
            val chars = text().toCharArray()
            val starts = ints()
            val freqs = bytes()
            val spelledAt = ints()
            val spelled = text().let { if (spelledAt.isEmpty()) emptyArray() else it.split('\n').toTypedArray() }
            val whitelist = text().let { s ->
                if (s.isEmpty()) emptyMap() else s.split('\n').associate { it.substringBefore('\t') to it.substringAfter('\t') }
            }
            val pool = ints()
            val folded = if (data.readBoolean()) ints() else null
            check(starts.size == freqs.size + 1 && starts.last() == chars.size && spelled.size == spelledAt.size) { "Damaged word list cache" }
            return Lexicon(chars, starts, freqs, spelledAt, spelled, whitelist, folding, rules, pool, folded, hasSavedFolded = true)
        }

        /** A lexicon from a plain list, most frequent first (tests and fallbacks). */
        fun fromWords(
            list: List<String>,
            whitelist: Map<String, String> = emptyMap(),
            folding: AccentFolding = AccentFolding.QWERTY,
            rules: SpellingRules = SpellingRules(),
        ): Lexicon =
            build(list.mapIndexed { i, w -> w to (255 - i * 255 / maxOf(1, list.size)).coerceAtLeast(1) }, whitelist, folding, rules)

        private fun build(
            entries: List<Pair<String, Int>>,
            whitelist: Map<String, String>,
            folding: AccentFolding,
            rules: SpellingRules = SpellingRules(),
        ): Lexicon {
            // Sorted by lowercase form; equal forms keep their order, as the lookups' tie rule expects.
            val lower = Array(entries.size) { entries[it].first.lowercase() }
            val order = lower.indices.sortedBy { lower[it] }
            val starts = IntArray(order.size + 1)
            order.forEachIndexed { n, i -> starts[n + 1] = starts[n] + lower[i].length }
            val chars = CharArray(starts[order.size])
            order.forEachIndexed { n, i -> lower[i].toCharArray(chars, starts[n]) }
            val spelledAt = order.indices.filter { n -> entries[order[n]].first != lower[order[n]] }
            return Lexicon(
                chars,
                starts,
                ByteArray(order.size) { n -> entries[order[n]].second.coerceIn(0, 255).toByte() },
                spelledAt.toIntArray(),
                Array(spelledAt.size) { k -> entries[order[spelledAt[k]]].first },
                whitelist,
                folding,
                rules,
            )
        }
    }
}
