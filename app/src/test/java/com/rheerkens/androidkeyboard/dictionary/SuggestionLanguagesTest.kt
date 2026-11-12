package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionLanguagesTest {
    @Test fun startsAtCurrentLanguageAndSkipsDuplicateLayoutsWithinTheMemoryBudget() {
        val current = TestLanguages.keyboard("nl:AZERTY")
        val enabled = listOf("en_US", "nl", "nl:AZERTY", "pl", "de").map(TestLanguages::keyboard)
        assertEquals(listOf("nl", "pl", "de"), suggestionLanguages(current, enabled).map { it.id })
    }

    @Test fun wrapsToIncludeTheOtherEnabledLanguage() {
        val current = TestLanguages.keyboard("nl")
        val enabled = listOf("en_US", "nl").map(TestLanguages::keyboard)
        assertEquals(listOf("nl", "en_US"), suggestionLanguages(current, enabled).map { it.id })
    }

    @Test fun doesNotReintroduceADisabledCurrentLanguage() {
        assertEquals(listOf("en_US"), suggestionLanguages(TestLanguages.keyboard("nl"),
            listOf(TestLanguages.keyboard("en_US"))).map { it.id })
    }
}
