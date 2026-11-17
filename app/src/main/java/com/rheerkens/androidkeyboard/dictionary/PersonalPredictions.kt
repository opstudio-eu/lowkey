package com.rheerkens.androidkeyboard.dictionary

/**
 * How the learned [WordContextModel] joins the strip: what it learns from the text before the
 * cursor, and how its words join next-word predictions and completions. Pure, so the keyboard and
 * `PredictionBenchmark` use the same steps.
 */
object PersonalPredictions {
    /** Text before the cursor worth reading for context: a few words of the current sentence. */
    const val CONTEXT_CHARS = 160

    /**
     * The complete words of the sentence the cursor is in, before it (nearest last). A word still
     * being typed at the end of [before] is not one of them.
     */
    fun sentenceWords(before: String): List<String> {
        var tokens = NextWordPredictor.tokens(before.takeLast(CONTEXT_CHARS))
        if (before.lastOrNull()?.let { it.isLetter() || it == '\'' || it == '’' } == true) tokens = tokens.dropLast(1)
        return tokens.takeLastWhile { it != null }.filterNotNull()
    }

    /**
     * The word just completed before the cursor, after a space or punctuation, with the up to two
     * words before it in its sentence; null when there is none, or it starts the sentence.
     */
    fun justTyped(before: String): Pair<List<String>, String>? {
        if (before.lastOrNull()?.let { it.isLetterOrDigit() || it == '\'' || it == '’' } != false) return null
        val tokens = NextWordPredictor.tokens(before.takeLast(CONTEXT_CHARS)).let { if (it.lastOrNull() == null) it.dropLast(1) else it }
        val sentence = tokens.takeLastWhile { it != null }.filterNotNull()
        if (sentence.size < 2) return null
        return sentence.dropLast(1).takeLast(2) to sentence.last()
    }

    /**
     * Next-word predictions after [before] (which ends with a space): the current field's own
     * repeats ([NextWordPredictor]) first, then what [model] learned follows these words.
     * [capitalize] starts the learned words with a capital (sentence start, Shift on).
     */
    fun nextWords(before: String, terms: List<PersonalTerm>, model: WordContextModel?, capitalize: Boolean, limit: Int = 3): List<Suggestion> {
        val field = NextWordPredictor.suggest(before, terms, limit)
        if (model == null || field.size >= limit) return field
        val learned = model.next(sentenceWords(before), limit = limit).map { word ->
            Suggestion(if (capitalize) word.replaceFirstChar { it.uppercaseChar() } else word)
        }
        return (field + learned).distinctBy { it.text.lowercase() }.take(limit)
    }

    /**
     * [ranked] completions of [typed] (see [Suggester.suggest]) with up to two words [model]
     * learned after the words before it put first, in [typed]'s case.
     */
    fun rank(typed: String, ranked: List<Suggestion>, before: String, model: WordContextModel?): List<Suggestion> {
        if (model == null || typed.isEmpty()) return ranked
        val learned = model.next(sentenceWords(before), prefix = typed, limit = LEARNED_COMPLETIONS)
        if (learned.isEmpty()) return ranked
        return (learned.map { Suggestion(Suggester.matchCase(typed, it)) } + ranked).distinctBy { it.text.lowercase() }
    }

    /** Learned words put before the dictionary's completions; the strip shows two besides the middle. */
    private const val LEARNED_COMPLETIONS = 2
}
