package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.*
import org.junit.Test

class MultilingualSuggesterTest {
    private fun mixed(primary: List<String>, secondary: List<String>) = Suggester(
        { Lexicon.fromWords(primary) }, secondaryLexicons = { listOf(Lexicon.fromWords(secondary)) })

    @Test fun aKnownWordInTheOtherLanguageIsNotAutocorrected() {
        val suggester = mixed(listOf("wine", "window"), listOf("wien", "winkel"))
        assertNull(suggester.autocorrection("wien", emptyList()))
        assertNull(suggester.autocorrection("Wien", emptyList()))
    }

    @Test fun sideSlotsOfferCompletionsFromBothLanguages() {
        val suggester = mixed(listOf("hello", "help", "hero"), listOf("helemaal", "hemel"))
        val slots = Suggester.arrangeForStrip("he", suggester.suggest("he", emptyList()))
        assertEquals(listOf("hello", "he", "helemaal"), slots.map { it?.text })
    }

    @Test fun conflictingCorrectionsLeaveTheTypedWordAlone() {
        val suggester = mixed(listOf("word"), listOf("ward"))
        assertNull(suggester.autocorrection("wird", emptyList()))
    }

    @Test fun anUnambiguousTypoCanBeCorrectedInTheSecondaryLanguage() {
        val suggester = mixed(listOf("fiets"), listOf("the"))
        assertEquals("the", suggester.autocorrection("teh", emptyList()))
    }

    @Test fun personalTermsStillWinAndCompletionsAreDeduplicated() {
        val suggester = mixed(listOf("hello", "help"), listOf("hello", "helder"))
        val terms = listOf(PersonalTerm(1, "Helios", 1))
        val result = suggester.suggest("he", terms, limit = 6)
        assertEquals("Helios", result.first().text)
        assertEquals(1, result.count { it.text == "hello" })
        assertTrue(result.any { it.text == "helder" })
    }

    @Test fun loadingAnotherDictionaryCannotTurnADutchWordIntoEnglish() {
        val suggester = Suggester({ Lexicon.fromWords(listOf("wine")) },
            secondaryLexicons = { listOf(Lexicon.EMPTY) }, dictionariesReady = false)
        assertNull(suggester.autocorrection("wien", emptyList()))
        assertTrue(suggester.suggest("wi", emptyList()).any { it.text == "wine" })
        assertEquals("CodeWidget", suggester.autocorrection("codewidget", listOf(PersonalTerm(1, "CodeWidget", 1))))
    }

    @Test fun fullDutchDictionaryProtectsWordsWhileTheEnglishKeyboardIsSelected() {
        val english = TestLanguages.lexicon("en_US")
        val dutch = TestLanguages.lexicon("nl")
        val suggester = Suggester({ english }, secondaryLexicons = { listOf(dutch) })
        for (word in listOf("fiets", "winkel", "morgen", "vriend")) {
            assertTrue("Fixture must contain $word", dutch.contains(word))
            assertNull("Must preserve $word", suggester.autocorrection(word, emptyList()))
        }
    }
}
