package com.rheerkens.androidkeyboard.dictionary

import java.util.Locale

/**
 * The keyboard language to suggest adding: the first of the phone's [locales] that [catalog] has
 * a word list for and none of the [enabled] languages covers, unless the user [dismissed] it.
 * Coverage goes by language, so English (UK) covers an English (US) phone. Autocorrect changes
 * about 40% of the correct words of a language that isn't on (see `QualityBenchmark`), and with
 * it on, almost none, so the app offers the languages the phone already uses.
 */
fun languageToSuggest(
    locales: List<Locale>,
    catalog: LanguageCatalog,
    enabled: Collection<KeyboardLanguage>,
    dismissed: Set<String>,
): KeyboardLanguage? {
    val covered = enabled.mapTo(HashSet()) { base(it.id) }
    for (locale in locales) {
        val code = when (val language = locale.language) {
            "no" -> "nb" // Norwegian is Bokmål in the catalog.
            else -> language
        }
        if (code.isEmpty() || code in covered) continue
        val match = catalog.languages.firstOrNull { it.id == "${code}_${locale.country}" }
            ?: catalog.languages.firstOrNull { base(it.id) == code }
            ?: continue
        if (match.id !in dismissed) return match
    }
    return null
}

private fun base(id: String) = id.substringBefore('_')
