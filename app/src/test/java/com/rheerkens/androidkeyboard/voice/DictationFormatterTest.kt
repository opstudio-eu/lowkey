package com.rheerkens.androidkeyboard.voice

import com.rheerkens.androidkeyboard.dictionary.PhraseRule
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationFormatterTest {

    @Test
    fun collapsesWhitespaceAndKeepsTextWhenCapitalizeIsNull() {
        assertEquals("Explain this  ".trim(), DictationFormatter.format("  Explain   this ").text)
    }

    @Test
    fun capitalizesAtSentenceStart() {
        assertEquals("Run the tests.", DictationFormatter.format("run the tests.", capitalize = true).text)
    }

    @Test
    fun lowercasesPlainSentenceCaseMidSentence() {
        assertEquals("explain what this does.", DictationFormatter.format("Explain what this does.", capitalize = false).text)
    }

    @Test
    fun keepsPronounIAcronymsAndTermsMidSentence() {
        assertEquals("I think so.", DictationFormatter.format("I think so.", capitalize = false).text)
        assertEquals("I'm done.", DictationFormatter.format("I'm done.", capitalize = false).text)
        assertEquals("ADB fails.", DictationFormatter.format("ADB fails.", capitalize = false).text)
        assertEquals(
            "Kotlin coroutines.",
            DictationFormatter.format("Kotlin coroutines.", capitalize = false, matchTermCase = true, terms = listOf("Kotlin")).text,
        )
    }

    @Test
    fun restoresExactTermSpellingIncludingMultiword() {
        val r = DictationFormatter.format(
            "Run the t3 code lint task with adb and Jetpack compose.",
            matchTermCase = true,
            terms = listOf("T3 Code", "adb", "Jetpack Compose"),
        )
        assertEquals("Run the T3 Code lint task with adb and Jetpack Compose.", r.text)
        assertEquals(0, r.rulesApplied)
    }

    @Test
    fun termCaseIsOffWhenDisabled() {
        assertEquals(
            "run t3 code",
            DictationFormatter.format("run t3 code", matchTermCase = false, terms = listOf("T3 Code")).text,
        )
    }

    @Test
    fun phraseRulesApplyBeforeTermsAndAreCounted() {
        val r = DictationFormatter.format(
            "refactor the view model",
            applyRules = true,
            rules = listOf(PhraseRule(1, "view model", "ViewModel")),
            matchTermCase = true,
            terms = listOf("ViewModel"),
            capitalize = true,
        )
        assertEquals("Refactor the ViewModel", r.text)
        assertEquals(1, r.rulesApplied)
    }

    @Test
    fun exactTermSpellingWinsOverSentenceCapitalization() {
        assertEquals(
            "adb install fails.",
            DictationFormatter.format("adb install fails.", capitalize = true, matchTermCase = true, terms = listOf("adb")).text,
        )
    }

    @Test
    fun emptyTranscriptStaysEmpty() {
        assertEquals("", DictationFormatter.format("   ", capitalize = true).text)
    }
}
