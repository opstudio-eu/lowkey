package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.*
import org.junit.Test

class NextWordPredictorTest {
    @Test fun repeatedContextSuggestsTheNextWord() {
        assertEquals("morgen", NextWordPredictor.suggest("We gaan morgen fietsen. We gaan ", emptyList()).first().text)
    }

    @Test fun longerMatchingContextWinsOverMoreFrequentSingleWordContext() {
        val text = "Ik ga lopen. Ik ga lopen. Wij ga fietsen. Wij ga "
        assertEquals("fietsen", NextWordPredictor.suggest(text, emptyList()).first().text)
    }

    @Test fun savedPhrasesKeepTheirSpelling() {
        val terms = listOf(PersonalTerm(1, "Jetpack Compose", 1))
        assertEquals(Suggestion("Compose", isPersonal = true), NextWordPredictor.suggest("Use Jetpack ", terms).single())
    }

    @Test fun savedSpellingWinsOverADifferentCaseInTheCurrentField() {
        val terms = listOf(PersonalTerm(1, "Jetpack Compose", 1))
        assertEquals(Suggestion("Compose", isPersonal = true),
            NextWordPredictor.suggest("Jetpack compose. Jetpack ", terms).single())
    }

    @Test fun noPredictionsInsideWordsOrAcrossSentenceBoundaries() {
        for (text in listOf("", "We gaan", "We gaan. ", "We gaan\n", "Hallo. We ", "We gaan https://example.test. We gaan ")) {
            assertTrue(text, NextWordPredictor.suggest(text, emptyList()).isEmpty())
        }
    }

    @Test fun predictionsAreDeduplicatedAndBounded() {
        val text = "We gaan morgen. We gaan morgen. We gaan vandaag. We gaan later. We gaan ooit. We gaan "
        val result = NextWordPredictor.suggest(text, emptyList())
        assertEquals(3, result.size)
        assertEquals("morgen", result.first().text)
        assertEquals(3, result.map { it.text }.distinct().size)
    }
}
