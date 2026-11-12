package com.rheerkens.androidkeyboard.dictionary

/** One suggestion slot. [isPersonal] marks saved terms; [isLiteral] marks the typed word itself. */
data class Suggestion(
    val text: String,
    val isPersonal: Boolean = false,
    val isLiteral: Boolean = false,
    /** Characters before the cursor this replaces (a saved multi-word term); 0 = the current word. */
    val replaceChars: Int = 0,
    /** Non-null only for a catalog-backed emoji that requires an explicit tap. */
    val emojiName: String? = null,
)

/**
 * Suggestions and autocorrect over a language [Lexicon] (AOSP word list) plus personal terms.
 * Personal terms keep their exact case and rank first; dictionary words follow the typed case
 * (`Kot` → `Kotlin`, `KO` → `KOTLIN`). Unknown words get spelling corrections from [Corrector].
 * [lexicon] is a provider so the word list can load in the background; [keys] are the letter
 * keys typed on, whose neighbours count as likely slips.
 */
class Suggester(private val lexicon: () -> Lexicon, private val keys: KeyGrid = KeyGrid.QWERTY,
    private val secondaryLexicons: () -> List<Lexicon> = { emptyList() },
    private val dictionariesReady: Boolean = true) {
    /** A suggester over a fixed word list, most frequent first (tests and fallbacks). */
    constructor(words: List<String>) : this(constant(Lexicon.fromWords(words)))

    /** Ranked candidates for [typed], best first. Empty [typed] returns newest personal terms. */
    fun suggest(typed: String, terms: List<PersonalTerm>, limit: Int = 3): List<Suggestion> {
        val newestFirst = terms.sortedByDescending { it.addedAt }
        if (typed.isEmpty()) {
            return newestFirst.take(limit).map { Suggestion(it.text, isPersonal = true) }
        }
        val lower = typed.lowercase()
        val seen = HashSet<String>()
        val out = ArrayList<Suggestion>()
        fun add(s: Suggestion) {
            if (out.size < limit && seen.add(s.text.lowercase())) out += s
        }

        // 1. Personal terms whose whole text starts with the prefix (e.g. "t3" → "T3 Code").
        newestFirst.filter { it.text.lowercase().startsWith(lower) && it.text.length > typed.length }
            .sortedBy { it.text.length }
            .forEach { add(Suggestion(it.text, isPersonal = true)) }
        // 2. Exact personal term with different case ("viewmodel" → "ViewModel").
        newestFirst.filter { it.text.equals(typed, ignoreCase = true) && it.text != typed }
            .forEach { add(Suggestion(it.text, isPersonal = true)) }
        // 3. Personal multiword terms where a later word starts with the prefix ("comp" → "Jetpack Compose").
        newestFirst.filter { term ->
            term.text.split(' ').drop(1).any { it.lowercase().startsWith(lower) }
        }.forEach { add(Suggestion(it.text, isPersonal = true)) }
        val lexicons = listOf(lexicon()) + secondaryLexicons()
        val completions = interleave(lexicons.map { lex -> lex.completions(typed, limit * 2).map { it.first } })
        val corrections = if (Corrector.looksLikeAWord(typed) && lexicons.none { it.contains(typed) }) {
            interleave(lexicons.map { lex ->
                listOfNotNull(lex.whitelist[lower]) + Corrector.corrections(typed, lex, limit, keys).map { it.word }
            })
        } else emptyList()
        // 4. If the letters start real words, complete them first ("Kot" → "Kotlin"); otherwise
        //    it's likely a typo, so spelling corrections come first ("recieve" → "receive").
        if (completions.isNotEmpty()) {
            completions.forEach { word -> add(Suggestion(matchCase(typed, word))) }
            corrections.forEach { add(Suggestion(matchCase(typed, it))) }
        } else {
            corrections.forEach { add(Suggestion(matchCase(typed, it))) }
        }
        return out
    }

    /**
     * What autocorrect should turn [word] into when Space is pressed, or null to leave it.
     * 1. A word saved exactly is never changed (`ADB` stays `ADB` even if `adb` is saved too).
     * 2. A saved term with different case wins (`viewmodel` → `ViewModel`).
     * 3. A close match to a single-word saved term (`kotln` → `Kotlin`).
     * 4. The dictionary corrector (`teh` → `the`, `dont` → `don't`), conservative by design.
     */
    fun autocorrection(word: String, terms: List<PersonalTerm>, atSentenceStart: Boolean = true): String? {
        if (terms.any { it.text == word }) return null
        terms.firstOrNull { it.text.equals(word, ignoreCase = true) }?.let { return it.text }
        // Missing words in a temporary fallback list must not look like foreign-language typos.
        if (!dictionariesReady) return null
        val lex = lexicon()
        val secondary = secondaryLexicons()
        // A valid word in another participating language is not a typo in this one.
        if (secondary.any { it.contains(word) }) return null
        if (Corrector.looksLikeAWord(word) && word.length >= 4 && !lex.contains(word)) {
            terms.filter { ' ' !in it.text }
                .map { it.text to Corrector.distance(word.lowercase(), it.text.lowercase(), 1.0, keys, lex.folding) }
                .filter { it.second <= 1.0 }
                .minByOrNull { it.second }
                ?.let { return it.first }
        }
        val primaryFix = Corrector.autocorrect(word, lex, atSentenceStart, keys)
        // Preserve language-specific casing/accent rules for known words in the active language.
        if (lex.contains(word)) return primaryFix
        val fixes = (listOfNotNull(primaryFix) + secondary.mapNotNull {
            Corrector.autocorrect(word, it, atSentenceStart, keys)
        }).distinct()
        return fixes.singleOrNull()
    }

    /**
     * A saved multiword term that the text just before the cursor spells with different case
     * (`t3 code` → `T3 Code`). Returns the number of characters to replace and the term, or null.
     * Matches only at a word boundary, and never changes text that already matches exactly.
     */
    fun protectedPhrase(before: String, terms: List<PersonalTerm>): Pair<Int, String>? {
        for (term in terms.filter { ' ' in it.text }.sortedByDescending { it.text.length }) {
            val n = term.text.length
            if (before.length < n) continue
            val tail = before.substring(before.length - n)
            if (!tail.equals(term.text, ignoreCase = true) || tail == term.text) continue
            val boundary = before.length == n || !before[before.length - n - 1].let { it.isLetterOrDigit() || it == '_' }
            if (boundary) return n to term.text
        }
        return null
    }

    companion object {
        /** Compare rank within each dictionary; raw frequencies across languages aren't comparable. */
        private fun interleave(lists: List<List<String>>): List<String> = buildList {
            for (rank in 0 until (lists.maxOfOrNull { it.size } ?: 0)) {
                lists.forEach { it.getOrNull(rank)?.let(::add) }
            }
        }

        private fun constant(lexicon: Lexicon): () -> Lexicon = { lexicon }

        /**
         * Places ranked candidates into three strip slots so that the middle always shows what
         * Space will insert: the [autocorrect] replacement (typed word on the left), or else the
         * typed word itself (best candidate on the left, next on the right).
         */
        fun arrangeForStrip(typed: String, ranked: List<Suggestion>, autocorrect: Suggestion? = null,
            emoji: Suggestion? = null): List<Suggestion?> {
            if (typed.isEmpty()) {
                return listOf(ranked.getOrNull(1), ranked.getOrNull(0), ranked.getOrNull(2))
            }
            val literal = Suggestion(typed, isLiteral = true)
            val others = ranked.filterNot { it.text == typed }
            val slots = if (autocorrect == null) {
                // Space keeps the typed word, so it sits in the middle; candidates go left and right.
                listOf(others.getOrNull(0), literal, others.getOrNull(1))
            } else {
                val best = if (autocorrect.replaceChars > 0) autocorrect else others.firstOrNull { it.text == autocorrect.text } ?: autocorrect
                listOf(literal, best, others.firstOrNull { it.text != best.text })
            }
            // Preserve the Space slot and saved-term priority; emoji are optional side suggestions.
            return if (emoji?.emojiName != null && slots[2]?.isPersonal != true) slots.take(2) + emoji else slots
        }

        fun matchCase(typed: String, word: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } &&
                typed.any { it.isLetter() } -> word.uppercase()
            typed.firstOrNull()?.isUpperCase() == true ->
                word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}
