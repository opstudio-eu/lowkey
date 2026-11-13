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
    @JvmOverloads
    fun suggest(typed: String, terms: List<PersonalTerm>, limit: Int = 3,
        checkActive: () -> Unit = {}): List<Suggestion> {
        checkActive()
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
        // Explicit safe corrections and contractions (`cant` → `can't`, `its` → `it's`) must
        // survive prefix completions.
        lexicons.mapNotNull { it.whitelist[lower] }.forEach { add(Suggestion(matchCase(typed, it))) }
        if (Corrector.looksLikeAWord(typed)) {
            interleave(lexicons.map { lex -> Corrector.apostropheForms(typed, lex).map { it.first } })
                .forEach { add(Suggestion(it)) }
        }
        val completions = interleave(lexicons.map { lex -> lex.completions(typed, limit * 2).map { it.first } })
        val corrections = if (Corrector.looksLikeAWord(typed) && lexicons.none { it.contains(typed) }) {
            interleave(lexicons.map { lex ->
                checkActive()
                listOfNotNull(lex.whitelist[lower]) + Corrector.corrections(typed, lex, limit, keys, checkActive).map { it.word }
            })
        } else emptyList()
        // 4. Keep the best completion, but reserve a slot for a spelling correction: a typo
        //    can also happen to prefix a rare word ("teh" → "tehran" versus "the").
        if (completions.isNotEmpty()) {
            add(Suggestion(matchCase(typed, completions.first())))
            corrections.firstOrNull()?.let { add(Suggestion(matchCase(typed, it))) }
            completions.drop(1).forEach { word -> add(Suggestion(matchCase(typed, word))) }
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
     * 3. A dictionary rule of the keyboard's language (`dont` → `don't`, Polish `juz` → `już`).
     * 4. A close match to a single-word saved term (`kotln` → `Kotlin`).
     * 5. The dictionary corrector (`teh` → `the`), conservative by design. When the languages
     *    typed in disagree, the clearly stronger fix wins; a close call leaves the word alone.
     */
    @JvmOverloads
    fun autocorrection(word: String, terms: List<PersonalTerm>, atSentenceStart: Boolean = true,
        checkActive: () -> Unit = {}): String? = decide(word, terms, atSentenceStart, search = true, checkActive)

    /**
     * [autocorrection] without the spelling search: a few hash lookups, for a Space pressed
     * before the background search has finished. Returns a fix only when [autocorrection] would
     * return the same one (`dont` → `don't`), else null.
     */
    fun quickAutocorrection(word: String, terms: List<PersonalTerm>, atSentenceStart: Boolean = true): String? =
        decide(word, terms, atSentenceStart, search = false) {}

    private fun decide(word: String, terms: List<PersonalTerm>, atSentenceStart: Boolean, search: Boolean,
        checkActive: () -> Unit): String? {
        checkActive()
        if (terms.any { it.text == word }) return null
        terms.firstOrNull { it.text.equals(word, ignoreCase = true) }?.let { return it.text }
        // Missing words in a temporary fallback list must not look like foreign-language typos.
        if (!dictionariesReady) return null
        val lex = lexicon()
        val secondary = secondaryLexicons()
        val primaryRule = Corrector.rule(word, lex, atSentenceStart)
        // A valid word in another participating language is not a typo in this one, unless it is
        // rare there and this language has a sure fix (`im`, odd in Dutch, is `I'm` in English).
        if (secondary.any { other ->
                other.contains(word) &&
                    (primaryRule !is Corrector.Rule.Replace || (other.frequency(word) ?: 0) >= RARE_IN_OTHER_LANGUAGE)
            }) return null
        if (primaryRule is Corrector.Rule.Replace) return primaryRule.word
        if (Corrector.looksLikeAWord(word) && word.length >= 4 && !lex.contains(word)) {
            terms.filter { ' ' !in it.text }
                .map { it.text to Corrector.distance(word.lowercase(), it.text.lowercase(), 1.0, keys, lex.folding) }
                .filter { it.second <= 1.0 }
                .minByOrNull { it.second }
                ?.let { return it.first }
        }
        // Preserve language-specific casing/accent rules for known words in the active language.
        if (primaryRule == Corrector.Rule.Keep && lex.contains(word)) return null
        val fixes = (listOf(lex) + secondary).mapNotNull { language ->
            checkActive()
            if (search) {
                // The keyboard's own language is the likelier one: `dne` is `done`, not Dutch `de`.
                Corrector.autoFix(word, language, atSentenceStart, keys, checkActive)
                    ?.let { if (language === lex) it.copy(score = it.score + PRIMARY_LEAD) else it }
            } else {
                (Corrector.rule(word, language, atSentenceStart) as? Corrector.Rule.Replace)
                    ?.let { AutoFix(it.word, Double.MAX_VALUE, certain = true) }
            }
        }
        // Without the search a sure fix still wins over any found later; anything else is unsure.
        return pick(if (search) fixes else fixes.filter { it.certain })
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
        /**
         * Frequency (0–255) at which a word in another participating language blocks a sure fix
         * in the keyboard's language: Polish `im` (159) stays, the Dutch list's `im` (119) doesn't.
         */
        private const val RARE_IN_OTHER_LANGUAGE = 128

        /** Score lead a spelling fix needs over a different one from another language. */
        private const val LANGUAGE_MARGIN = 1.0

        /**
         * Score head start of the keyboard's own language when languages disagree. Larger values
         * fix more typos in that language but break more in the other one; 1.0 balances both.
         */
        private const val PRIMARY_LEAD = 1.0

        /**
         * The fix to apply when languages may disagree: a sure (rule) fix beats spelling search
         * results, and a search result needs a clear lead. Two sure fixes that differ, or a close
         * call, leave the word alone.
         */
        private fun pick(fixes: List<AutoFix>): String? {
            val ranked = fixes.groupBy { it.word }
                .map { (_, same) -> same.maxWith(compareBy<AutoFix> { it.certain }.thenBy { it.score }) }
                .sortedWith(compareByDescending<AutoFix> { it.certain }.thenByDescending { it.score })
            val best = ranked.firstOrNull() ?: return null
            val second = ranked.getOrNull(1) ?: return best.word
            return when {
                best.certain -> best.word.takeIf { !second.certain }
                else -> best.word.takeIf { best.score - second.score >= LANGUAGE_MARGIN }
            }
        }

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
