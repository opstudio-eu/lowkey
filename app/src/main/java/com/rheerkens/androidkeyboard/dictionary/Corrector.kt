package com.rheerkens.androidkeyboard.dictionary

import kotlin.math.abs

/** A correction candidate: [distance] is the weighted edit cost, [score] ranks candidates (higher is better). */
data class Correction(val word: String, val distance: Double, val score: Double)

/**
 * Spelling correction against a [Lexicon], tuned for touch typing on a QWERTY layout:
 * a weighted Damerau-Levenshtein distance where hitting a neighbouring key, swapping two letters,
 * doubling a letter, or leaving out an apostrophe cost less than other edits. Candidates are
 * ranked by frequency and distance. Pure Kotlin.
 */
object Corrector {
    private const val ADJACENT = 0.55
    private const val SUBSTITUTE = 1.0
    private const val INSERT_DELETE = 1.0
    private const val TRANSPOSE = 0.65
    private const val DOUBLE_LETTER = 0.45
    private const val APOSTROPHE = 0.15
    /** AOSP frequencies are already logarithmic (0–255); this converts them to the score scale. */
    private const val FREQUENCY_SCALE = 24.0
    private const val DISTANCE_WEIGHT = 3.6

    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val keyPos: Map<Char, Pair<Double, Double>> = buildMap {
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> put(ch, (c + r * 0.5) to r.toDouble()) } }
    }

    // Edit distance visits this relation for every candidate character pair. Precompute it
    // once to avoid boxed Char/hash-map/Pair lookups in the hot loop.
    private val adjacentKeys = BooleanArray(26 * 26).also { table ->
        for (a in 'a'..'z') for (b in 'a'..'z') {
            val pa = keyPos.getValue(a)
            val pb = keyPos.getValue(b)
            table[(a - 'a') * 26 + (b - 'a')] =
                abs(pa.first - pb.first) <= 1.0 && abs(pa.second - pb.second) <= 1.0
        }
    }

    fun adjacent(a: Char, b: Char): Boolean {
        val x = a - 'a'
        val y = b - 'a'
        return x in 0..25 && y in 0..25 && adjacentKeys[x * 26 + y]
    }

    /** Weighted edit distance between lowercase [a] and [b], or a value above [limit] once it's exceeded. */
    fun distance(a: String, b: String, limit: Double = Double.MAX_VALUE): Double =
        distance(a, b, limit, Array(3) { DoubleArray(b.length + 1) })

    private fun distance(a: String, b: String, limit: Double, rows: Array<DoubleArray>): Double {
        val n = a.length
        val m = b.length
        if (abs(n - m) * INSERT_DELETE > limit + 2) return limit + 1
        var prev2 = rows[0]
        var prev = rows[1]
        prev[0] = 0.0
        for (j in 1..m) prev[j] = prev[j - 1] + insertCost(b, j - 1)
        var cur = rows[2]
        for (i in 1..n) {
            cur[0] = prev[0] + insertCost(a, i - 1)
            var rowMin = cur[0]
            for (j in 1..m) {
                val ca = a[i - 1]
                val cb = b[j - 1]
                val sub = when {
                    ca == cb -> 0.0
                    adjacent(ca, cb) -> ADJACENT
                    else -> SUBSTITUTE
                }
                var v = minOf(prev[j - 1] + sub, prev[j] + deleteCost(a, i - 1), cur[j - 1] + insertCost(b, j - 1))
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) v = minOf(v, prev2[j - 2] + TRANSPOSE)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    /** Cost of a character present in one word but not the other at [i]. */
    private fun insertCost(s: String, i: Int): Double = when {
        s[i] == '\'' -> APOSTROPHE
        i > 0 && s[i] == s[i - 1] -> DOUBLE_LETTER
        else -> INSERT_DELETE
    }

    private fun deleteCost(s: String, i: Int): Double = insertCost(s, i)

    /** Maximum distance worth considering for a typed word of [length] letters. */
    fun maxDistance(length: Int): Double = when {
        length <= 2 -> 0.0
        length <= 4 -> 1.0
        length <= 7 -> 1.8
        else -> 2.4
    }

    /** Ranked corrections for [typed] (lowercase compared), best first. */
    fun corrections(typed: String, lexicon: Lexicon, limit: Int = 3): List<Correction> {
        val t = typed.lowercase()
        val max = maxDistance(t.length)
        if (max <= 0.0) return emptyList()
        val span = kotlin.math.ceil(max).toInt()
        val out = ArrayList<Correction>()
        // All candidates fit these rows. Reuse them instead of allocating three arrays per word.
        val rows = Array(3) { DoubleArray(t.length + span + 1) }
        lexicon.forEachCandidate(
            (t.length - span).coerceAtLeast(1)..t.length + span,
            accept = { it != t && plausibleStart(t, it) },
        ) { word, lower, freq ->
            val d = distance(t, lower, max, rows)
            if (d <= max) out += Correction(word, d, freq / FREQUENCY_SCALE - DISTANCE_WEIGHT * d)
        }
        return out.sortedByDescending { it.score }.take(limit)
    }

    /**
     * Typos rarely change the first letter to a far-away key. Requiring the same first letter, a
     * neighbouring key, the first two letters swapped, or one missing/extra leading letter
     * keeps the search fast without discarding first-letter insertions and deletions.
     */
    private fun plausibleStart(t: String, w: String): Boolean {
        if (t.isEmpty() || w.isEmpty()) return false
        val a = t[0]
        val b = w[0]
        return a == b || adjacent(a, b) ||
            (t.length > 1 && w.length > 1 && t[1] == b && w[1] == a) ||
            (t.length > 2 && w.length > 1 && t[1] == b && t[2] == w[1]) ||
            (w.length > 2 && t.length > 1 && w[1] == a && w[2] == t[1])
    }

    /**
     * The word to replace [typed] with when the user presses Space, or null to leave it alone.
     * Conservative: known words, short words, and code-like tokens (digits, underscores, inner
     * capitals such as `ViewModel`) are never corrected.
     */
    fun autocorrect(typed: String, lexicon: Lexicon, atSentenceStart: Boolean = true): String? {
        if (!looksLikeAWord(typed)) return null
        // A capitalized word mid-sentence is most likely a name ("Meet Mira"): leave it.
        if (typed[0].isUpperCase() && !atSentenceStart) return null
        // A word spelled exactly as in the dictionary is never changed ("Bern", "ill").
        if (lexicon.containsExact(typed)) return null
        lexicon.whitelist[typed.lowercase()]?.let { return Suggester.matchCase(typed, it) }
        if (lexicon.contains(typed) || typed.length <= 2) return null
        // Never turn a lowercase word into a capitalized name ("lol" → "Lil").
        val ranked = corrections(typed, lexicon, 3).filter { typed[0].isUpperCase() || !it.word[0].isUpperCase() }
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        if (lexicon.frequency(best.word)?.let { it < MIN_TARGET_FREQUENCY } == true) return null
        if (second != null && best.score - second.score < MIN_MARGIN && second.distance <= best.distance) return null
        return Suggester.matchCase(typed, best.word)
    }

    /**
     * True if the whitespace-delimited [token] is just [word], optionally after opening quotes
     * or brackets. URLs, emails, paths, and hashtags (`https://teh`, `user@teh`) are not.
     */
    fun isStandaloneWord(token: String, word: String): Boolean =
        word.isNotEmpty() && token.trimStart('(', '[', '{', '"', '\'', '*', '_', '“', '‘') == word

    /** Plain words only: letters and apostrophes, and no capitals after the first letter. */
    fun looksLikeAWord(s: String): Boolean =
        s.isNotEmpty() && s.all { it.isLetter() || it == '\'' } && s.drop(1).none { it.isUpperCase() }

    private const val MIN_TARGET_FREQUENCY = 20
    private const val MIN_MARGIN = 0.35
}
