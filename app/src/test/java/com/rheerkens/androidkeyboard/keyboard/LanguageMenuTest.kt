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

    @Test fun rowsMatchTheAlternatesPopupGrid() {
        for (count in 1..21) for ((window, cell) in listOf(1080f to 168f, 360f to 64f, 100f to 64f, 50f to 64f)) {
            val popup = AlternatesPopup(count, 0f, 40f, 48f, window, cell, 52f, 12f)
            assertEquals("count $count, window $window", popup.rows, LanguageMenu.rows(count, window, cell))
        }
    }

    @Test fun shrunkenRowsStayInsideTheKeyboardAboveTheKey() {
        // 21 keyboards on a 360 dp keyboard: five rows of at most 52 dp must fit above a key 192 dp down.
        val keyTop = 192f
        val rows = LanguageMenu.rows(21, 360f, 64f)
        val cell = minOf(52f, keyTop / (rows - 1))
        val popup = AlternatesPopup(21, 50f, 40f, 48f, 360f, 64f, cell, 12f)
        assertEquals(5, popup.rows)
        assertEquals(0f, keyTop + popup.top, 0.01f)
    }

    @Test fun capacityCountsReadableRowsAboveTheKey() {
        // 360 dp wide, 64 dp cells: five columns. A key 192 dp down leaves room for four 44 dp rows above it.
        assertEquals(25, LanguageMenu.capacity(360f, 64f, 192f, 44f))
        // A 260 dp keyboard with 32 dp rows: four columns, and two rows fit above a key 108 dp down.
        assertEquals(12, LanguageMenu.capacity(260f, 64f, 108f, 44f))
        // No room above the key: one row.
        assertEquals(5, LanguageMenu.capacity(360f, 64f, 20f, 44f))
    }

    @Test fun keyboardsThatDoNotFitMoveBehindAMoreCell() {
        val all = TypingKeyboard.all(TestLanguages.catalog)
        assertEquals(all to false, LanguageMenu.fit(all, all.size))
        val (shown, more) = LanguageMenu.fit(all, 12)
        assertEquals(true, more)
        assertEquals(all.take(11), shown)
        // The current keyboard (first) always stays, even with a single cell.
        assertEquals(all.take(1) to true, LanguageMenu.fit(all, 1))
    }
}
