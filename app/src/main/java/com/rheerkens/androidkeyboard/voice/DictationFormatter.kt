package com.rheerkens.androidkeyboard.voice

import com.rheerkens.androidkeyboard.dictionary.PhraseRule
import com.rheerkens.androidkeyboard.dictionary.PhraseRuleApplier

/** Turns a final transcript into the text inserted in the editor. Pure; no Android types. */
object DictationFormatter {

    data class Result(val text: String, val rulesApplied: Int)

    /**
     * 1. Collapses whitespace.
     * 2. Applies enabled phrase rules when [applyRules] is on.
     * 3. Capitalizes the first letter when [capitalize] is true. When it is false (mid-sentence),
     *    lowercases a plain sentence-case first word such as `Explain`, but keeps `I`, acronyms,
     *    and saved terms. Null leaves the first letter alone.
     * 4. Restores the exact spelling of saved [terms] (`t3 code` → `T3 Code`, `Adb` → `adb`) when
     *    [matchTermCase] is on.
     */
    fun format(
        raw: String,
        applyRules: Boolean = false,
        rules: List<PhraseRule> = emptyList(),
        matchTermCase: Boolean = false,
        terms: List<String> = emptyList(),
        capitalize: Boolean? = null,
    ): Result {
        var text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return Result("", 0)
        var applied = 0
        if (applyRules) {
            val r = PhraseRuleApplier.apply(text, rules)
            text = r.text
            applied = r.appliedCount
        }
        text = when (capitalize) {
            true -> text.replaceFirstChar { it.uppercaseChar() }
            false -> lowercaseSentenceStart(text, terms)
            null -> text
        }
        // Last, so exact spelling wins over sentence capitalization (`adb` stays `adb`).
        if (matchTermCase && terms.isNotEmpty()) {
            val termRules = terms.filter { it.isNotBlank() }
                .mapIndexed { i, t -> PhraseRule(id = -1L - i, spoken = t, written = t.trim()) }
            text = PhraseRuleApplier.apply(text, termRules).text
        }
        return Result(text, applied)
    }

    private fun lowercaseSentenceStart(text: String, terms: List<String>): String {
        val first = text.takeWhile { it.isLetter() || it == '\'' }
        val plainTitleCase = first.length >= 2 && first[0].isUpperCase() &&
            first.drop(1).all { it.isLowerCase() || it == '\'' }
        if (!plainTitleCase || first == "I" || first.startsWith("I'")) return text
        if (terms.any { it.trim().startsWith(first) }) return text
        return text.replaceFirstChar { it.lowercaseChar() }
    }
}
