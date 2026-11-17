package com.rheerkens.androidkeyboard.dictionary

/**
 * Compound words in languages that write them as one word (Dutch `thuiswerkdag`): two common words
 * of the list, optionally joined by one of the language's linking letters
 * ([SpellingRules.compoundLinkers], Dutch `s`, `e`, `en`), make a word the list may not have.
 * Such a word is left alone, and a slip in one of its parts can be repaired
 * (`toetsenbrodinstellingen` → `toetsenbordinstellingen`).
 */
object Compounds {
    /** Shortest word treated as a compound. */
    const val MIN_LENGTH = 8
    /** Shortest part. */
    private const val MIN_PART = 3
    /**
     * Shortest parts of a repaired compound, the known one and the repaired one: with shorter
     * ones almost any unknown word (an English `including`) splits into fragments that "repair".
     */
    private const val MIN_REPAIRED_PART = 5
    /** Most a repaired part may differ from its fix: one slip. */
    private const val MAX_REPAIR_DISTANCE = 1.0
    /** Parts must be common words: rare list entries would let almost any typo split. */
    private const val MIN_PART_FREQUENCY = 60
    /** Spelling searches a repair may run, longest known part first. */
    private const val MAX_SEARCHES = 4
    /** Score lead the best repair needs over a different one. */
    private const val MIN_LEAD = 0.5

    private fun part(word: String, lexicon: Lexicon) =
        word.length >= MIN_PART && (lexicon.frequency(word) ?: 0) >= MIN_PART_FREQUENCY

    private class Split(val left: String, val linker: String, val right: String)

    private fun splits(word: String, linkers: List<String>): List<Split> = buildList {
        for (i in MIN_PART..word.length - MIN_PART) for (linker in listOf("") + linkers) {
            if (!word.startsWith(linker, i)) continue
            val right = word.substring(i + linker.length)
            if (right.length >= MIN_PART) add(Split(word.substring(0, i), linker, right))
        }
    }

    /** True if [word], not in [lexicon] itself, is two of its common words, maybe with a linking letter. */
    fun isCompound(word: String, lexicon: Lexicon): Boolean {
        val linkers = lexicon.rules.compoundLinkers ?: return false
        val w = word.lowercase()
        if (w.length < MIN_LENGTH || !w.all { it.isLetter() }) return false
        return splits(w, linkers).any { part(it.left, lexicon) && part(it.right, lexicon) }
    }

    /**
     * [typed] as a compound with a slip in one part repaired: a common word, then the other part
     * spelling-corrected to a common word ([Corrector.autoFix]), or null. Runs at most
     * [MAX_SEARCHES] searches, the splits with the longest known part first.
     */
    fun repair(typed: String, lexicon: Lexicon, keys: KeyGrid, checkActive: () -> Unit = {}): AutoFix? {
        val linkers = lexicon.rules.compoundLinkers ?: return null
        val w = typed.lowercase()
        if (w.length < MIN_LENGTH || !w.all { it.isLetter() }) return null
        val tries = splits(w, linkers).mapNotNull { s ->
            val leftKnown = part(s.left, lexicon)
            val rightKnown = part(s.right, lexicon)
            when {
                s.left.length < MIN_REPAIRED_PART || s.right.length < MIN_REPAIRED_PART -> null
                leftKnown && !rightKnown -> Triple(s, false, s.left.length)
                rightKnown && !leftKnown -> Triple(s, true, s.right.length)
                else -> null
            }
        }.sortedByDescending { it.third }.take(MAX_SEARCHES)
        val found = LinkedHashMap<String, Double>()
        for ((s, fixLeft, _) in tries) {
            checkActive()
            val broken = if (fixLeft) s.left else s.right
            val fix = Corrector.autoFix(broken, lexicon, atSentenceStart = false, keys = keys, checkActive = checkActive) ?: continue
            val fixed = fix.word.lowercase()
            if (fix.certain || !part(fixed, lexicon) ||
                Corrector.distance(broken, fixed, MAX_REPAIR_DISTANCE, keys, lexicon.folding) > MAX_REPAIR_DISTANCE) continue
            val word = if (fixLeft) fixed + s.linker + s.right else s.left + s.linker + fixed
            if (fix.score > (found[word] ?: Double.NEGATIVE_INFINITY)) found[word] = fix.score
        }
        val ranked = found.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        if (second != null && best.value - second.value < MIN_LEAD) return null
        return AutoFix(Suggester.matchCase(typed, best.key), best.value, certain = false)
    }
}
