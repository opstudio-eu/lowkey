package com.rheerkens.androidkeyboard.dictionary

import kotlin.math.abs

/** A correction candidate: [distance] is the weighted edit cost, [score] ranks candidates (higher is better). */
data class Correction(val word: String, val distance: Double, val score: Double)

/**
 * An autocorrection in one language. [certain] fixes come from dictionary rules (a missing
 * apostrophe or accent, a whitelisted shortcut); others from a spelling search, ranked by [score].
 */
data class AutoFix(val word: String, val score: Double, val certain: Boolean)

/**
 * Spelling correction against a [Lexicon], tuned for touch typing on the keyboard's [KeyGrid]
 * (QWERTY unless given): a weighted Damerau-Levenshtein distance where hitting a neighbouring key,
 * leaving out an accent (`zolw` → `żółw`, [Lexicon.folding]), swapping two letters, doubling a
 * letter, or leaving out an apostrophe cost less than other edits. Candidates are ranked by
 * frequency and distance. Pure Kotlin.
 */
object Corrector {
    private const val ADJACENT = 0.55
    /**
     * The base letter typed for an accented one that has no key of its own (`z` for `ż`): as
     * cheap as a missing apostrophe, so `gdzies` becomes `gdzieś` rather than the more frequent
     * `gdzie`.
     */
    private const val ACCENT = 0.15
    private const val SUBSTITUTE = 1.0
    private const val INSERT_DELETE = 1.0
    private const val TRANSPOSE = 0.65
    private const val DOUBLE_LETTER = 0.45
    private const val APOSTROPHE = 0.15
    /** AOSP frequencies are already logarithmic (0–255); this converts them to the score scale. */
    private const val FREQUENCY_SCALE = 24.0
    private const val DISTANCE_WEIGHT = 3.6

    /** Weighted edit distance between lowercase [a] and [b], or a value above [limit] once it's exceeded. */
    fun distance(
        a: String,
        b: String,
        limit: Double = Double.MAX_VALUE,
        keys: KeyGrid = KeyGrid.QWERTY,
        folding: AccentFolding = AccentFolding.QWERTY,
    ): Double = distance(a, b, limit, Array(3) { DoubleArray(b.length + 1) }, keys, folding)

    private fun distance(
        a: String,
        b: String,
        limit: Double,
        rows: Array<DoubleArray>,
        keys: KeyGrid,
        folding: AccentFolding,
    ): Double {
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
                    folding.fold(ca) == folding.fold(cb) -> ACCENT
                    // A neighbour, or a letter whose accented form has a key of its own (`u` for `ü` in German).
                    keys.adjacent(ca, cb) || KeyGrid.sameBaseLetter(ca, cb) -> ADJACENT
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
    fun corrections(typed: String, lexicon: Lexicon, limit: Int = 3, keys: KeyGrid = KeyGrid.QWERTY,
        checkActive: () -> Unit = {}): List<Correction> {
        checkActive()
        val t = typed.lowercase()
        val max = maxDistance(t.length)
        if (max <= 0.0) return emptyList()
        val span = kotlin.math.ceil(max).toInt()
        val out = ArrayList<Correction>()
        // All candidates fit these rows. Reuse them instead of allocating three arrays per word.
        val rows = Array(3) { DoubleArray(t.length + span + 1) }
        val folding = lexicon.folding
        val folded = folding.fold(t)
        lexicon.forEachCandidate(
            (t.length - span).coerceAtLeast(1)..t.length + span,
            checkActive = checkActive,
            accept = { it != t && plausibleStart(folded, it, keys, folding) },
        ) { word, lower, freq ->
            val d = distance(t, lower, max, rows, keys, folding)
            if (d <= max) out += Correction(word, d, freq / FREQUENCY_SCALE - DISTANCE_WEIGHT * d)
        }
        // The same word with accents (`dziekuje` → `dziękuję`) counts even when it is too rare
        // for the candidate pool, as inflected Polish forms often are.
        lexicon.forEachAccentedForm(folded) { word, lower, freq ->
            if (lower != t && out.none { it.word == word }) {
                val d = distance(t, lower, max, rows, keys, folding)
                if (d <= max) out += Correction(word, d, freq / FREQUENCY_SCALE - DISTANCE_WEIGHT * d)
            }
        }
        return out.sortedByDescending { it.score }.take(limit)
    }

    /**
     * Typos rarely change the first letter to a far-away key. Requiring the same first letter
     * (accents aside), a neighbouring key, the first two letters swapped, or one missing/extra
     * leading letter keeps the search fast without discarding first-letter insertions and
     * deletions. [t] is the typed word with its accents already folded.
     */
    private fun plausibleStart(t: String, w: String, keys: KeyGrid, folding: AccentFolding): Boolean {
        if (t.isEmpty() || w.isEmpty()) return false
        val a = t[0]
        val b = folding.fold(w[0])
        if (a == b || keys.adjacent(a, b) || KeyGrid.sameBaseLetter(a, b)) return true
        val w1 = if (w.length > 1) folding.fold(w[1]) else ' '
        return (t.length > 1 && w.length > 1 && t[1] == b && w1 == a) ||
            (t.length > 2 && w.length > 1 && t[1] == b && t[2] == w1) ||
            (w.length > 2 && t.length > 1 && w1 == a && folding.fold(w[2]) == t[1])
    }

    /**
     * The word to replace [typed] with when the user presses Space, or null to leave it alone.
     * Conservative: known words, short words, and code-like tokens (digits, underscores, inner
     * capitals such as `ViewModel`) are never corrected.
     */
    fun autocorrect(typed: String, lexicon: Lexicon, atSentenceStart: Boolean = true, keys: KeyGrid = KeyGrid.QWERTY,
        checkActive: () -> Unit = {}): String? = autoFix(typed, lexicon, atSentenceStart, keys, checkActive)?.word

    /** [autocorrect] with how sure it is, so that the fixes of several languages can be compared. */
    fun autoFix(typed: String, lexicon: Lexicon, atSentenceStart: Boolean = true, keys: KeyGrid = KeyGrid.QWERTY,
        checkActive: () -> Unit = {}): AutoFix? {
        checkActive()
        when (val rule = rule(typed, lexicon, atSentenceStart)) {
            Rule.Keep -> return null
            is Rule.Replace -> return AutoFix(rule.word, Double.MAX_VALUE, certain = true)
            Rule.Search -> Unit
        }
        val nouns = lexicon.rules.capitalizedNouns
        // Never turn a lowercase word into a capitalized name ("lol" → "Lil"), except where nouns
        // are capitalized; the pronoun's contractions (`ive` → `I've`) are not names.
        val ranked = corrections(typed, lexicon, 3, keys, checkActive)
            .filter { nouns || typed[0].isUpperCase() || !it.word[0].isUpperCase() || isPronounI(it.word) }
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        if (lexicon.frequency(best.word)?.let { it < MIN_TARGET_FREQUENCY } == true) return null
        if (second != null && best.score - second.score < MIN_MARGIN && second.distance <= best.distance) return null
        return AutoFix(Suggester.matchCase(typed, best.word), best.score, certain = false)
    }

    /** What dictionary lookups alone decide for a typed word, before any spelling search. */
    sealed interface Rule {
        /** Leave the word as typed. */
        data object Keep : Rule
        /** Replace it: a missing apostrophe or accent, a whitelisted shortcut, a German noun. */
        data class Replace(val word: String) : Rule
        /** Unknown word: only a spelling search can tell. */
        data object Search : Rule
    }

    /**
     * The rules part of [autocorrect]: a handful of hash lookups, cheap enough for the input
     * thread when Space comes before the background spelling search has finished.
     */
    fun rule(typed: String, lexicon: Lexicon, atSentenceStart: Boolean = true): Rule {
        if (!looksLikeAWord(typed)) {
            // A shouted contraction (`DONT` → `DON'T`); short all-caps words are often acronyms.
            val shouted = typed.length >= 4 && typed.all { it.isLetter() && it.isUpperCase() }
            return contraction(typed, lexicon)?.takeIf { shouted }?.let(Rule::Replace) ?: Rule.Keep
        }
        // A capitalized word mid-sentence is most likely a name ("Meet Mira"): leave it.
        if (typed[0].isUpperCase() && !atSentenceStart) return Rule.Keep
        // In languages whose word list also has accent-less spellings of common words (Polish
        // `juz` next to `już`), restore the accents when that spelling is much more frequent.
        lexicon.accentRestoration(typed)?.let { return Rule.Replace(Suggester.matchCase(typed, it)) }
        // `dont` → `don't`, and `cant` → `can't` because `cant` is that much rarer.
        contraction(typed, lexicon)?.let { return Rule.Replace(it) }
        // A word spelled exactly as in the dictionary is never changed ("Bern", "ill").
        if (lexicon.containsExact(typed)) return Rule.Keep
        lexicon.whitelist[typed.lowercase()]?.let { return Rule.Replace(Suggester.matchCase(typed, it)) }
        if (lexicon.rules.capitalizedNouns && typed[0].isLowerCase()) {
            // A more frequent spelling with umlauts or ß comes first: `hauser` is `Häuser` rather
            // than the surname Hauser, and `strasse` is `Straße`.
            val own = lexicon.frequency(typed)
            umlautSpelling(typed.lowercase(), lexicon)?.let { (word, freq) ->
                if (own == null || freq > own) return Rule.Replace(Suggester.matchCase(typed, word))
            }
            // German nouns: `zeit` is only known as `Zeit`.
            val spelling = lexicon.spelling(typed)
            if (spelling != null && spelling[0].isUpperCase() && spelling.substring(1) == typed.substring(1)) {
                return Rule.Replace(spelling)
            }
        }
        if (lexicon.contains(typed) || typed.length <= 2) return Rule.Keep
        return Rule.Search
    }

    /**
     * Dictionary words that are [typed] with one apostrophe added (`dont` → `don't`, `ive` →
     * `I've`, French `cest` → `c'est`), most frequent first, in the typed case. Capitalized forms
     * (`Japan's`) are left out, except the pronoun I's.
     */
    fun apostropheForms(typed: String, lexicon: Lexicon): List<Pair<String, Int>> {
        if (typed.length < 2 || '\'' in typed) return emptyList()
        val t = typed.lowercase()
        val out = ArrayList<Pair<String, Int>>(2)
        for (i in 1 until t.length) {
            val variant = t.substring(0, i) + '\'' + t.substring(i)
            val freq = lexicon.frequency(variant) ?: continue
            val spelling = lexicon.spelling(variant) ?: continue
            if (spelling[0].isUpperCase() && !isPronounI(spelling)) continue
            out += Suggester.matchCase(typed, spelling) to freq
        }
        return out.sortedByDescending { it.second }
    }

    /**
     * The contraction [typed] is missing an apostrophe of, or null. An unknown word takes its
     * apostrophe form (`didnt`, `youre`, `itll`, French `jai`). A known word changes only to an
     * ending contraction that is far more common (`cant`, `wont`, `whats`), so that `its`,
     * `lets`, `ill` and `were` stay, and so do French words that look like an elision
     * (`dun` and `dune` are not `d'un` and `d'une`).
     */
    fun contraction(typed: String, lexicon: Lexicon): String? {
        val forms = apostropheForms(typed, lexicon)
        val own = lexicon.frequency(typed)
            ?: return forms.firstOrNull()?.takeIf { it.second >= MIN_TARGET_FREQUENCY }?.first
        return forms.firstOrNull { (word, freq) ->
            freq >= own + CONTRACTION_MARGIN && ENDINGS.any { word.endsWith(it, ignoreCase = true) }
        }?.first
    }

    private fun isPronounI(word: String): Boolean = word.length > 2 && word.startsWith("I'")

    /**
     * The most frequent dictionary spelling of [lower] with some of its a, o, u turned into ä, ö,
     * ü and some ss into ß (German writers on a phone often leave them out), with its frequency.
     * Only the first [MAX_UMLAUT_SPOTS] such spots vary, so at most 16 lookups.
     */
    private fun umlautSpelling(lower: String, lexicon: Lexicon): Pair<String, Int>? {
        val spots = ArrayList<Int>()
        var i = 0
        while (i < lower.length && spots.size < MAX_UMLAUT_SPOTS) {
            if (lower[i] in "aou" || lower.startsWith("ss", i)) spots += i
            i += if (lower.startsWith("ss", i)) 2 else 1
        }
        var best: Pair<String, Int>? = null
        for (mask in 1 until (1 shl spots.size)) {
            val variant = buildString {
                var j = 0
                while (j < lower.length) {
                    val spot = spots.indexOf(j)
                    when {
                        spot >= 0 && mask and (1 shl spot) != 0 && lower.startsWith("ss", j) -> { append('ß'); j += 2 }
                        spot >= 0 && mask and (1 shl spot) != 0 -> { append(UMLAUTS.getValue(lower[j])); j++ }
                        else -> { append(lower[j]); j++ }
                    }
                }
            }
            val freq = lexicon.frequency(variant) ?: continue
            if (best == null || freq > best.second) best = lexicon.spelling(variant)!! to freq
        }
        return best
    }

    private val UMLAUTS = mapOf('a' to 'ä', 'o' to 'ö', 'u' to 'ü')
    private const val MAX_UMLAUT_SPOTS = 4

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
    /**
     * How much more common (0–255 scale) a contraction must be than the known word it is typed
     * as: `wont` 50 → `won't` 158 and `cant` 70 → `can't` 160 change, `lets` 106 → `let's` 145 not.
     */
    private const val CONTRACTION_MARGIN = 60
    /** Endings a known word may gain an apostrophe in (`cant` → `can't`, `whats` → `what's`). */
    private val ENDINGS = listOf("n't", "'s", "'ll", "'re", "'ve", "'d", "'m")
}
