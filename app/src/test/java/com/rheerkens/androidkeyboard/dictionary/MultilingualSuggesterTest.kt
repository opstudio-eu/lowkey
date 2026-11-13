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

    @Test fun aCloseCallBetweenLanguagesLeavesTheTypedWordAlone() {
        // `word` (o is next to i) is a slightly closer fix, but not enough to overrule the keyboard's language.
        assertNull(mixed(listOf("ward"), listOf("word")).autocorrection("wird", emptyList()))
    }

    @Test fun aClearlyBetterCorrectionWinsWhenLanguagesDisagree() {
        assertEquals("word", mixed(listOf("word"), listOf("ward")).autocorrection("wird", emptyList()))
        // A sure fix (a missing apostrophe) beats a spelling guess from the other language.
        assertEquals("don't", mixed(listOf("don't"), listOf("doet")).autocorrection("dont", emptyList()))
    }

    @Test fun englishTyposAreCorrectedWithDutchAlsoEnabled() {
        val suggester = Suggester({ TestLanguages.lexicon("en_US") }, secondaryLexicons = { listOf(TestLanguages.lexicon("nl")) })
        val expected = mapOf(
            "dont" to "don't", "Dont" to "Don't", "didnt" to "didn't", "youre" to "you're", "im" to "I'm",
            "ive" to "I've", "cant" to "can't", "teh" to "the", "recieve" to "receive", "wiht" to "with",
            "thier" to "their", "becasue" to "because",
        )
        val wrong = expected.mapNotNull { (typed, want) ->
            val actual = suggester.autocorrection(typed, emptyList())
            if (actual == want) null else "$typed -> $actual (want $want)"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        // Dutch typos on the English keyboard are still fixed, and Dutch words kept.
        assertEquals("misschien", suggester.autocorrection("mischien", emptyList()))
        assertNull(suggester.autocorrection("fiets", emptyList()))
    }

    @Test fun aCommonWordInTheOtherLanguageBlocksEvenASureFix() {
        // `im` is an everyday Polish word ("them"), so it isn't `I'm` there.
        val suggester = Suggester({ TestLanguages.lexicon("en_US") }, secondaryLexicons = { listOf(TestLanguages.lexicon("pl")) })
        assertNull(suggester.autocorrection("im", emptyList()))
        assertEquals("don't", suggester.autocorrection("dont", emptyList()))
    }

    @Test fun differingSureFixesInTwoLanguagesLeaveTheWordAlone() {
        // German capitalizes the noun, Polish restores the accents: neither language wins.
        val probe = Suggester({ Lexicon.fromWords(listOf("Probe"), rules = SpellingRules(capitalizedNouns = true)) },
            secondaryLexicons = { listOf(TestLanguages.lexicon("pl")) })
        assertEquals("próbę", Corrector.autocorrect("probe", TestLanguages.lexicon("pl")))
        assertNull(probe.autocorrection("probe", emptyList()))
        assertNull(probe.quickAutocorrection("probe", emptyList()))
    }

    @Test fun knownWordsOnlyTakeAnEndingContraction() {
        // French `dune` and `laide` are words, not `d'une` and `l'aide`; `jai` is no word but `j'ai`.
        val french = Lexicon.fromWords(listOf("d'une", "l'aide", "j'ai", "dune", "laide"))
        assertNull(Corrector.autocorrect("dune", french))
        assertNull(Corrector.autocorrect("laide", french))
        assertEquals("j'ai", Corrector.autocorrect("jai", french))
    }

    @Test fun quickAutocorrectionSkipsSavedTermDistances() {
        val suggester = Suggester({ TestLanguages.lexicon("en_US") })
        // `kotln` could be the saved `Kotlin`: only the full search can tell, so the quick answer is "not sure".
        val terms = listOf(PersonalTerm(1, "Kotlin", 1))
        assertNull(suggester.quickAutocorrection("kotln", terms))
        assertEquals("Kotlin", suggester.autocorrection("kotln", terms))
        assertEquals("don't", suggester.quickAutocorrection("dont", terms))
    }

    @Test fun quickAutocorrectionGivesSureFixesWithoutASpellingSearch() {
        val suggester = Suggester({ TestLanguages.lexicon("en_US") }, secondaryLexicons = { listOf(TestLanguages.lexicon("nl")) })
        for (word in listOf("dont", "Dont", "cant", "ive", "youre", "im", "whats", "teh", "recieve", "fiets", "its", "hello")) {
            val quick = suggester.quickAutocorrection(word, emptyList())
            // Never a different answer from the full search; null only means "not sure without it".
            if (quick != null) assertEquals(word, suggester.autocorrection(word, emptyList()), quick)
        }
        assertEquals("don't", suggester.quickAutocorrection("dont", emptyList()))
        assertEquals("I've", suggester.quickAutocorrection("ive", emptyList()))
        assertNull(suggester.quickAutocorrection("teh", emptyList()))
        assertEquals("ViewModel", suggester.quickAutocorrection("viewmodel", listOf(PersonalTerm(1, "ViewModel", 1))))
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
