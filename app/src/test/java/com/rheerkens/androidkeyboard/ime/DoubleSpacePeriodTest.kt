package com.rheerkens.androidkeyboard.ime

import org.junit.Assert.*
import org.junit.Test

class DoubleSpacePeriodTest {
    @Test fun twoQuickSpacesAfterAWordEndTheSentence() {
        val period = DoubleSpacePeriod()
        assertFalse(period.onSpace("Hallo", 100))
        assertTrue(period.onSpace("Hallo ", 350))
        assertFalse(period.onSpace("Hallo. ", 500))
    }

    @Test fun slowSpacesAndPreexistingSpacesStayLiteral() {
        val period = DoubleSpacePeriod()
        assertFalse(period.onSpace("Hallo ", 100))
        assertFalse(period.onSpace("Hallo  ", 200))
        period.reset()
        assertFalse(period.onSpace("Hallo", 300))
        assertFalse(period.onSpace("Hallo ", 1500))
    }

    @Test fun resetPreventsReplacementAfterAnotherKeyOrEditorChange() {
        val period = DoubleSpacePeriod()
        period.onSpace("Hallo", 100)
        period.reset()
        assertFalse(period.onSpace("Hallo ", 200))
    }

    @Test fun aMovedCursorOrChangedSurroundingTextCannotReuseThePreviousSpace() {
        val period = DoubleSpacePeriod()
        period.onSpace("Hallo", 100)
        assertFalse(period.onSpace("Anders ", 200))
    }

    @Test fun theFirstSpaceCanAlsoAutocorrectTheWord() {
        val period = DoubleSpacePeriod()
        assertFalse(period.onSpace("teh", 100))
        period.spaceCommitted("the ")
        assertTrue(period.onSpace("the ", 200))
    }

    @Test fun contextTruncationDoesNotDisableLongSentences() {
        val period = DoubleSpacePeriod()
        val before = "a".repeat(64)
        assertFalse(period.onSpace(before, 100))
        assertTrue(period.onSpace("a".repeat(63) + " ", 200))
    }

    @Test fun punctuationAndWhitespaceAreNotSentenceContent() {
        for (text in listOf("", " ", "Hallo.", "Hallo!", "Hallo,", "Hallo\n", "...")) {
            val period = DoubleSpacePeriod()
            assertFalse(period.onSpace(text, 100))
            assertFalse(text, period.onSpace("$text ", 200))
        }
        for (text in listOf("123", "café", "\"Hallo\"", "(Hallo)")) {
            val period = DoubleSpacePeriod()
            period.onSpace(text, 100)
            assertTrue(text, period.onSpace("$text ", 200))
        }
    }
}
