package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.keyboard.KeyboardController
import com.rheerkens.androidkeyboard.keyboard.ShiftState
import com.rheerkens.androidkeyboard.settings.TypingKeyboard
import com.rheerkens.androidkeyboard.settings.TypingLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggesterTest {
    private val suggester = Suggester(BuiltInWords.ENGLISH)
    private val terms = listOf("T3 Code", "coroutineScope", "adb", "ViewModel", "Jetpack Compose")
        .mapIndexed { i, t -> PersonalTerm(i + 1L, t, 1_000L - i) }

    @Test
    fun typedPrefixOffersPersonalTermAndMiddleShowsWhatSpaceInserts() {
        val ranked = suggester.suggest("t3", terms)
        assertEquals(Suggestion("T3 Code", isPersonal = true), ranked.first())
        val slots = Suggester.arrangeForStrip("t3", ranked)
        assertEquals("T3 Code", slots[0]?.text)
        assertEquals(Suggestion("t3", isLiteral = true), slots[1])
        // With an autocorrection, the replacement is in the middle and the typed word on the left.
        val corrected = Suggester.arrangeForStrip("teh", listOf(Suggestion("the"), Suggestion("ten")), Suggestion("the"))
        assertEquals(listOf("teh", "the", "ten"), corrected.map { it?.text })
        // A saved phrase that Space would apply is in the middle too, with its range.
        val phrase = Suggestion("Gradle wrapper", isPersonal = true, replaceChars = 14)
        val phraseSlots = Suggester.arrangeForStrip("wrapper", listOf(Suggestion("wrapper")), phrase)
        assertEquals(listOf(Suggestion("wrapper", isLiteral = true), phrase), phraseSlots.take(2))
    }

    @Test
    fun emptyWordShowsNewestPersonalTerms() {
        val withNew = terms + PersonalTerm(99, "Gradle wrapper", 5_000)
        val ranked = suggester.suggest("", withNew)
        assertEquals(listOf("Gradle wrapper", "T3 Code", "coroutineScope"), ranked.map { it.text })
        assertEquals("Gradle wrapper", Suggester.arrangeForStrip("", ranked)[1]?.text)
    }

    @Test
    fun personalTermsKeepCaseAndBuiltInsFollowTypedCase() {
        assertEquals("ViewModel", suggester.suggest("view", terms).first().text)
        assertEquals("ViewModel", suggester.suggest("viewmodel", terms).first().text)
        assertEquals("Kotlin", suggester.suggest("Kot", terms).first().text)
        assertEquals("KOTLIN", suggester.suggest("KOT", terms).first().text)
        assertTrue(suggester.suggest("comp", terms).any { it.text == "Jetpack Compose" })
    }

    @Test
    fun autocorrectionOfTermsAndTypos() {
        assertEquals("ViewModel", suggester.autocorrection("viewmodel", terms))
        assertEquals("the", suggester.autocorrection("teh", terms))
        assertEquals("The", suggester.autocorrection("Teh", terms))
        assertNull(suggester.autocorrection("ViewModel", terms))
        assertNull(suggester.autocorrection("hello", terms))
    }

    @Test
    fun noMatchShowsTypedWordInMiddle() {
        val slots = Suggester.arrangeForStrip("zzq", suggester.suggest("zzq", terms))
        assertEquals(listOf(null, Suggestion("zzq", isLiteral = true), null), slots)
    }

    @Test
    fun shiftTapDoubleTapAndLock() {
        var now = 0L
        val kc = KeyboardController(TypingKeyboard.DEFAULT) { now }
        kc.onShiftTap()
        assertEquals(ShiftState.ONCE, kc.state.value.shift)
        kc.onCharTyped()
        assertEquals(ShiftState.OFF, kc.state.value.shift)
        now = 1_000; kc.onShiftTap()
        now = 1_200; kc.onShiftTap()
        assertEquals(ShiftState.LOCKED, kc.state.value.shift)
        kc.onCharTyped()
        assertEquals(ShiftState.LOCKED, kc.state.value.shift)
        now = 3_000; kc.onShiftTap()
        assertEquals(ShiftState.OFF, kc.state.value.shift)
        val both = TypingLayout.entries.map { TypingKeyboard(it) }
        assertEquals(TypingKeyboard(TypingLayout.NL_NL), kc.cycleKeyboard(both))
        assertEquals(TypingKeyboard.DEFAULT, kc.cycleKeyboard(both))
    }

    @Test
    fun exactSavedSpellingIsNeverAutocorrected() {
        val s = Suggester(listOf("the"))
        val terms = listOf(PersonalTerm(1, "teh", 1), PersonalTerm(2, "adb", 2), PersonalTerm(3, "ADB", 3))
        assertNull(s.autocorrection("teh", terms))
        assertNull(s.autocorrection("ADB", terms))
        assertNull(s.autocorrection("adb", terms))
        assertEquals("the", s.autocorrection("teh", emptyList()))
        assertEquals("adb", s.autocorrection("Adb", listOf(PersonalTerm(2, "adb", 2))))
    }

    @Test
    fun onlyStandaloneWordsAreAutocorrected() {
        assertTrue(Corrector.isStandaloneWord("teh", "teh"))
        assertTrue(Corrector.isStandaloneWord("(teh", "teh"))
        assertTrue(Corrector.isStandaloneWord("“teh", "teh"))
        for (token in listOf("https://teh", "user@teh", "example.teh", "~/teh", "#teh", "a=teh", "C:\\teh")) {
            assertTrue(token, !Corrector.isStandaloneWord(token, "teh"))
        }
        assertTrue(!Corrector.isStandaloneWord("", ""))
    }

    @Test
    fun multiwordTermsAreRecasedAtAWordBoundary() {
        val s = Suggester(emptyList())
        val terms = listOf(PersonalTerm(1, "T3 Code", 1), PersonalTerm(2, "Jetpack Compose", 2))
        assertEquals(7 to "T3 Code", s.protectedPhrase("run t3 code", terms))
        assertEquals(15 to "Jetpack Compose", s.protectedPhrase("jetpack compose", terms))
        assertNull(s.protectedPhrase("run T3 Code", terms)) // already exact
        assertNull(s.protectedPhrase("xt3 code", terms)) // not at a word boundary
        assertNull(s.protectedPhrase("code", terms))
    }
}
