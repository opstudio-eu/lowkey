package com.rheerkens.androidkeyboard.dictionary

/**
 * A personal term with exact spelling and case, for example `T3 Code` or `coroutineScope`.
 * Terms protect spelling in suggestions and autocorrect. They do not train speech recognition.
 */
data class PersonalTerm(
    val id: Long,
    val text: String,
    val addedAt: Long,
)

/**
 * An opt-in `spoken → written` replacement applied once to final speech text after Stop,
 * for example `view model → ViewModel`.
 */
data class PhraseRule(
    val id: Long,
    val spoken: String,
    val written: String,
    val enabled: Boolean = true,
)

data class DictionaryState(
    val terms: List<PersonalTerm> = emptyList(),
    val rules: List<PhraseRule> = emptyList(),
)
