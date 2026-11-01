package com.rheerkens.androidkeyboard.dictionary

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * A language's word list with frequencies (0–255), built from the AOSP LatinIME dictionaries by
 * `scripts/build-dictionaries.py`. Supports exact lookups, frequency-ranked prefix completion,
 * and candidate lookup by length for spelling correction. Pure Kotlin.
 */
class Lexicon private constructor(
    /** Words sorted by their lowercase form (for prefix ranges). */
    private val words: Array<String>,
    private val lower: Array<String>,
    private val freqs: IntArray,
    /** Safe whitelist corrections such as `dont` -> `don't` (keys lowercase). */
    val whitelist: Map<String, String>,
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

    val size: Int get() = words.size

    /** Frequency of [word] (case-insensitive), or null if unknown. */
    fun frequency(word: String): Int? = byLower[word.lowercase()]?.let { freqs[it] }

    /** The dictionary's spelling of [word] (e.g. `android` -> `Android` if stored capitalized). */
    fun spelling(word: String): String? = byLower[word.lowercase()]?.let { words[it] }

    fun contains(word: String): Boolean = byLower.containsKey(word.lowercase())

    /** True if [word] is stored with exactly this spelling, or as a lowercase word it capitalizes. */
    fun containsExact(word: String): Boolean =
        word in exact || (word.firstOrNull()?.isUpperCase() == true && word.lowercase() in exact)

    /** Up to [limit] words starting with [prefix] (case-insensitive), most frequent first. */
    fun completions(prefix: String, limit: Int): List<Pair<String, Int>> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        var lo = lowerBound(p)
        val best = ArrayList<Int>()
        while (lo < lower.size && lower[lo].startsWith(p)) {
            if (lower[lo].length > p.length) best += lo
            lo++
        }
        return best.sortedByDescending { freqs[it] }.take(limit).map { words[it] to freqs[it] }
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
        return build(entries, whitelist)
    }

    private fun lowerBound(p: String): Int {
        var lo = 0
        var hi = lower.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (lower[mid] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /** Words considered as correction targets: the most frequent ones. */
        const val CORRECTION_POOL = 60_000

        val EMPTY = fromWords(emptyList())

        /** Parses the `word<TAB>freq` + `#whitelist` format (optionally gzipped by the caller). */
        fun parse(input: InputStream): Lexicon {
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
            return build(entries, whitelist)
        }

        fun parseGzip(input: InputStream): Lexicon = GZIPInputStream(input).use(::parse)

        /** A lexicon from a plain list, most frequent first (tests and fallbacks). */
        fun fromWords(list: List<String>, whitelist: Map<String, String> = emptyMap()): Lexicon =
            build(list.mapIndexed { i, w -> w to (255 - i * 255 / maxOf(1, list.size)).coerceAtLeast(1) }, whitelist)

        private fun build(entries: List<Pair<String, Int>>, whitelist: Map<String, String>): Lexicon {
            val sorted = entries.sortedBy { it.first.lowercase() }
            return Lexicon(
                Array(sorted.size) { sorted[it].first },
                Array(sorted.size) { sorted[it].first.lowercase() },
                IntArray(sorted.size) { sorted[it].second },
                whitelist,
            )
        }
    }
}
