package com.rheerkens.androidkeyboard.dictionary

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageHintTest {
    private val catalog = TestLanguages.catalog
    private fun lang(id: String) = TestLanguages.language(id)
    private fun hint(locales: String, enabled: List<String>, dismissed: Set<String> = emptySet()) =
        languageToSuggest(locales.split(',').map(Locale::forLanguageTag), catalog, enabled.map(::lang), dismissed)?.id

    @Test fun aPhoneLanguageThatNoKeyboardCoversIsSuggested() {
        assertEquals("nl", hint("en-US,nl-BE", listOf("en_US")))
        assertEquals("nl", hint("nl-NL,en-US", listOf("en_US")))
        assertNull(hint("en-US,nl-BE", listOf("en_US", "nl")))
    }

    @Test fun anotherVariantOfTheLanguageCountsAsCovered() {
        assertNull(hint("en-US", listOf("en_GB")))
        assertNull(hint("en-GB,en-US", listOf("en_US")))
    }

    @Test fun theCountryPicksTheVariant() {
        assertEquals("pt_PT", hint("pt-PT", listOf("en_US")))
        assertEquals("pt_BR", hint("pt-BR", listOf("en_US")))
        assertEquals("en_GB", hint("en-GB", listOf("nl")))
        // No exact variant: the first one for the language.
        assertEquals("en_US", hint("en-IE", listOf("nl")))
        assertEquals("nb", hint("no-NO", listOf("en_US")))
    }

    @Test fun languagesWithoutAWordListAndDismissedOnesAreSkipped() {
        assertNull(hint("ja-JP", listOf("en_US")))
        assertEquals("de", hint("ja-JP,de-DE", listOf("en_US")))
        assertEquals("fr", hint("nl-BE,fr-BE", listOf("en_US"), dismissed = setOf("nl")))
        assertNull(hint("nl-BE", listOf("en_US"), dismissed = setOf("nl")))
    }
}
