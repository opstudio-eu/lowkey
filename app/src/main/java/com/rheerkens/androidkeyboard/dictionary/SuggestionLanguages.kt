package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard

/** Matches the full-lexicon cache budget. Duplicate layouts do not consume language slots. */
const val MAX_SUGGESTION_LANGUAGES = 3

/** Current language first, then the next distinct enabled languages in globe order, wrapping. */
fun suggestionLanguages(current: TypingKeyboard, usable: List<TypingKeyboard>): List<KeyboardLanguage> {
    if (usable.isEmpty()) return emptyList()
    val start = usable.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    return (usable.drop(start) + usable.take(start)).map { it.language }
        .distinctBy { it.id }.take(MAX_SUGGESTION_LANGUAGES)
}
