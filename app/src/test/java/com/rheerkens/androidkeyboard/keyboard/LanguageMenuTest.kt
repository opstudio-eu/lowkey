package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import com.rheerkens.androidkeyboard.settings.KeyArrangement
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageMenuTest {
    private val en = TypingKeyboard(TestLanguages.language("en_US"))
    private val nl = TypingKeyboard(TestLanguages.language("nl"))
    private val pl = TypingKeyboard(TestLanguages.language("pl"))
    private val enDvorak = TypingKeyboard(TestLanguages.language("en_US"), KeyArrangement.DVORAK)

    @Test fun currentKeyboardComesFirstThenTheGlobeOrder() {
        val all = listOf(en, nl, pl)
        assertEquals(listOf(en, nl, pl), LanguageMenu.choices(all, en))
        assertEquals(listOf(nl, pl, en), LanguageMenu.choices(all, nl))
        assertEquals(listOf(pl, en, nl), LanguageMenu.choices(all, pl))
    }

    @Test fun aCurrentKeyboardMissingFromTheListIsStillShownFirst() {
        assertEquals(listOf(pl, en, nl), LanguageMenu.choices(listOf(en, nl), pl))
        assertEquals(listOf(en), LanguageMenu.choices(emptyList(), en))
    }

    @Test fun releasingDecidesBetweenChoosingClosingAndStayingOpen() {
        assertEquals(LanguageMenu.Release.KEEP_OPEN, LanguageMenu.release(selected = 0, moved = false))
        assertEquals(LanguageMenu.Release.CLOSE, LanguageMenu.release(selected = 0, moved = true))
        assertEquals(LanguageMenu.Release.CLOSE, LanguageMenu.release(selected = null, moved = true))
        assertEquals(LanguageMenu.Release.CHOOSE, LanguageMenu.release(selected = 2, moved = true))
    }

    @Test fun cellsShowTheCodeAndTheNameOrAlternativeLayout() {
        assertEquals("NL", LanguageMenu.code(nl))
        assertEquals("Nederlands", LanguageMenu.caption(nl))
        assertEquals("EN", LanguageMenu.code(enDvorak))
        assertEquals(KeyArrangement.DVORAK.label, LanguageMenu.caption(enDvorak))
    }
}
