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

/**
 * [this] with the [terms] and [rules] of a backup added, skipping blank or too long ones and
 * ones already saved (the same term, or the same spoken and written text). New entries get ids
 * from [firstId] on; terms keep when they were added. Returns the result and how many terms and
 * rules were added.
 */
fun DictionaryState.withImported(terms: List<PersonalTerm>, rules: List<PhraseRule>, firstId: Long): Triple<DictionaryState, Int, Int> {
    var id = firstId
    val newTerms = ArrayList(this.terms)
    for (term in terms) {
        val text = term.text.trim()
        if (text.isEmpty() || text.length > DictionaryStore.MAX_TERM_LENGTH || newTerms.any { it.text == text }) continue
        newTerms += PersonalTerm(id++, text, term.addedAt)
    }
    val newRules = ArrayList(this.rules)
    for (rule in rules) {
        val spoken = rule.spoken.trim()
        val written = rule.written.trim()
        if (spoken.isEmpty() || written.isEmpty() || newRules.any { it.spoken == spoken && it.written == written }) continue
        newRules += PhraseRule(id++, spoken, written, rule.enabled)
    }
    return Triple(DictionaryState(newTerms, newRules), newTerms.size - this.terms.size, newRules.size - this.rules.size)
}
