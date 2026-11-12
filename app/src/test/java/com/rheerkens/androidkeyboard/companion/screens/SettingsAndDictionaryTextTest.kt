package com.rheerkens.androidkeyboard.companion.screens

import com.rheerkens.androidkeyboard.dictionary.PersonalTerm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsAndDictionaryTextTest {
    private val terms = listOf(
        PersonalTerm(1, "T3 Code", addedAt = 10),
        PersonalTerm(2, "ViewModel", addedAt = 30),
        PersonalTerm(3, "adb", addedAt = 20),
    )

    @Test fun countsUseSingularForOne() {
        assertEquals("0 words", countLabel(0, "word", "words"))
        assertEquals("1 word", countLabel(1, "word", "words"))
        assertEquals("2 words", countLabel(2, "word", "words"))
    }

    @Test fun searchIgnoresCaseAndSpacesAndListsNewestFirst() {
        assertEquals(listOf(2L, 3L, 1L), filterTerms(terms, "").map { it.id })
        assertEquals(listOf(1L), filterTerms(terms, "  t3 ").map { it.id })
        assertEquals(listOf(2L, 1L), filterTerms(terms, "O").map { it.id })
    }

    @Test fun wordsSummaryMatchesShownResults() {
        assertEquals("3 words", wordsSummary(3, 3, " "))
        assertEquals("1 word", wordsSummary(1, 1, ""))
        assertEquals("1 match for “t3”", wordsSummary(1, 3, " t3"))
        assertEquals("2 matches for “d”", wordsSummary(2, 3, "d"))
        assertEquals("No words match “zz”", wordsSummary(0, 3, "zz"))
    }

    @Test fun termValidationFollowsTheStore() {
        assertEquals("Enter a word or phrase.", termError("   ", terms, null))
        assertEquals("Use at most 48 characters.", termError("x".repeat(49), terms, null))
        assertNull(termError("x".repeat(48), terms, null))
        assertEquals("“ViewModel” is already in your words.", termError(" ViewModel ", terms, null))
        // Exact spelling counts: another casing is a separate word, and a word may keep its own text.
        assertNull(termError("viewmodel", terms, null))
        assertNull(termError("ViewModel", terms, 2))
        assertEquals("“adb” is already in your words.", termError("adb", terms, 2))
    }

    @Test fun ruleStatusShowsInactiveRulesKeepTheirChoice() {
        assertEquals("On", ruleStatus(enabled = true, active = true))
        assertEquals("Off", ruleStatus(enabled = false, active = true))
        assertEquals("Off", ruleStatus(enabled = false, active = false))
        assertEquals("On, inactive while speech rules are off", ruleStatus(enabled = true, active = false))
        assertEquals("No rules", rulesSummary(0, 0, true))
        assertEquals("1 rule · 1 on", rulesSummary(1, 1, true))
        assertEquals("3 rules · inactive", rulesSummary(3, 2, false))
    }

    @Test fun dependentVoiceSettingsNameTheirSavedValue() {
        assertEquals("Applies in separate voice mode, where it's on.", keptChoiceNote(true))
        assertEquals("Applies in separate voice mode, where it's off.", keptChoiceNote(false))
    }
}
