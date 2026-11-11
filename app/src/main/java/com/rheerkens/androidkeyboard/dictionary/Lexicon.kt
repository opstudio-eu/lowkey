package com.rheerkens.androidkeyboard.dictionary

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
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
 * by length for spelling correction. [keys] is the language's letter layout. Pure Kotlin.
 */
class Lexicon private constructor(
    /** Words sorted by their lowercase form (for prefix ranges). */
    private val words: Array<String>,
    private val lower: Array<String>,
    private val freqs: IntArray,
    /** Safe whitelist corrections such as `dont` -> `don't` (keys lowercase). */
    val whitelist: Map<String, String>,
    /** The language's letter layout: key neighbours and accents without their own key. */
    val keys: KeyGrid,
    /** Language-specific autocorrect rules from the catalog. */
    val rules: SpellingRules = SpellingRules(),
) {
    private val exact: Set<String> = HashSet<String>(words.size * 2).also { it.addAll(words) }

    private val byLower: Map<String, Int> = HashMap<String, Int>(lower.size * 2).also { m ->
        lower.forEachIndexed { i, w -> val prev = m[w]; if (prev == null || freqs[prev] < freqs[i]) m[w] = i }
    }

    /** Built alongside the word list on its loader thread, never lazily on a Space press. */
    private val byLength: Map<Int, IntArray> = run {
        freqs.indices.sortedByDescending { freqs[it] }.take(CORRECTION_POOL)
            .groupBy { lower[it].length }
            .mapValues { (_, v) -> v.toIntArray() }
    }

    /**
     * Word indices sorted by their accent-folded lowercase form (`żółw` → `zolw`) and those forms,
     * so `zol` completes `żółw`. Null when folding changes no word (English).
     */
    private val foldedOrder: IntArray?
    private val foldedSorted: Array<String>?

    init {
        val folded = Array(lower.size) { keys.fold(lower[it]) }
        if (folded.indices.all { folded[it] === lower[it] }) {
            foldedOrder = null
            foldedSorted = null
        } else {
            val order = folded.indices.sortedBy { folded[it] }.toIntArray()
            foldedOrder = order
            foldedSorted = Array(order.size) { folded[order[it]] }
        }
    }

    val size: Int get() = words.size

    /** Frequency of [word] (case-insensitive), or null if unknown. */
    fun frequency(word: String): Int? = byLower[word.lowercase()]?.let { freqs[it] }

    /** The dictionary's spelling of [word] (e.g. `android` -> `Android` if stored capitalized). */
    fun spelling(word: String): String? = byLower[word.lowercase()]?.let { words[it] }

    fun contains(word: String): Boolean = byLower.containsKey(word.lowercase())

    /** True if [word] is stored with exactly this spelling, or as a lowercase word it capitalizes. */
    fun containsExact(word: String): Boolean =
        word in exact || (word.firstOrNull()?.isUpperCase() == true && word.lowercase() in exact)

    /**
     * Up to [limit] words starting with [prefix] (case-insensitive), most frequent first. A prefix
     * without accents also matches accented words (`dzie` → `dziękuję`); one with accents only
     * matches words spelled that way.
     */
    fun completions(prefix: String, limit: Int): List<Pair<String, Int>> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val best = ArrayList<Int>()
        val order = foldedOrder
        val sorted = foldedSorted
        if (order != null && sorted != null && keys.fold(p) == p) {
            var lo = lowerBound(sorted, p)
            while (lo < sorted.size && sorted[lo].startsWith(p)) {
                val i = order[lo]
                if (lower[i].length > p.length) best += i
                lo++
            }
        } else {
            var lo = lowerBound(lower, p)
            while (lo < lower.size && lower[lo].startsWith(p)) {
                if (lower[lo].length > p.length) best += lo
                lo++
            }
        }
        return best.sortedByDescending { freqs[it] }.take(limit).map { words[it] to freqs[it] }
    }

    /** Visits every word whose accent-folded lowercase form is exactly [folded]. */
    internal fun forEachAccentedForm(folded: String, visit: (String, String, Int) -> Unit) {
        val order = foldedOrder ?: return
        val sorted = foldedSorted ?: return
        var i = lowerBound(sorted, folded)
        while (i < sorted.size && sorted[i] == folded) {
            val w = order[i]
            visit(words[w], lower[w], freqs[w])
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
        if (keys.fold(t) != t) return null // the user typed accents on purpose
        val own = frequency(t) ?: return null
        var best: String? = null
        var bestFreq = own + accentMargin - 1
        forEachAccentedForm(t) { word, lower, freq ->
            if (lower != t && freq > bestFreq) {
                best = word
                bestFreq = freq
            }
        }
        return best
    }

    /** Visits eligible candidates without per-word Sequence/Pair/boxed-index allocations. */
    internal fun forEachCandidate(range: IntRange, accept: (String) -> Boolean, visit: (String, String, Int) -> Unit) {
        for (len in range) {
            val indices = byLength[len] ?: continue
            for (i in indices) if (accept(lower[i])) visit(words[i], lower[i], freqs[i])
        }
    }

    /** This lexicon plus [extra] words (frequency [frequency]) that it doesn't already know. */
    fun withExtraWords(extra: Collection<String>, frequency: Int): Lexicon {
        val added = extra.distinctBy { it.lowercase() }.filter { !contains(it) }
        if (added.isEmpty()) return this
        val entries = words.indices.map { words[it] to freqs[it] } + added.map { it to frequency }
        return build(entries, whitelist, keys, rules)
    }

    private fun lowerBound(sorted: Array<String>, p: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /** Words considered as correction targets: the most frequent ones. */
        const val CORRECTION_POOL = 60_000

        val EMPTY = fromWords(emptyList())

        /** Parses the `word<TAB>freq` + `#whitelist` format (optionally gzipped by the caller). */
        fun parse(input: InputStream, keys: KeyGrid = KeyGrid.QWERTY, rules: SpellingRules = SpellingRules()): Lexicon {
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
            return build(entries, whitelist, keys, rules)
        }

        fun parseGzip(input: InputStream, keys: KeyGrid = KeyGrid.QWERTY, rules: SpellingRules = SpellingRules()): Lexicon =
            GZIPInputStream(input).use { parse(it, keys, rules) }

        /** A lexicon from a plain list, most frequent first (tests and fallbacks). */
        fun fromWords(
            list: List<String>,
            whitelist: Map<String, String> = emptyMap(),
            keys: KeyGrid = KeyGrid.QWERTY,
            rules: SpellingRules = SpellingRules(),
        ): Lexicon =
            build(list.mapIndexed { i, w -> w to (255 - i * 255 / maxOf(1, list.size)).coerceAtLeast(1) }, whitelist, keys, rules)

        private fun build(
            entries: List<Pair<String, Int>>,
            whitelist: Map<String, String>,
            keys: KeyGrid,
            rules: SpellingRules = SpellingRules(),
        ): Lexicon {
            val sorted = entries.sortedBy { it.first.lowercase() }
            return Lexicon(
                Array(sorted.size) { sorted[it].first },
                Array(sorted.size) { sorted[it].first.lowercase() },
                IntArray(sorted.size) { sorted[it].second },
                whitelist,
                keys,
                rules,
            )
        }
    }
}
