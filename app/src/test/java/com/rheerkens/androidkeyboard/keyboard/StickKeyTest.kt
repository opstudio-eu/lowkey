package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickKeyTest {
    private fun stick(id: String, numberRow: Boolean = false) =
        stickKey(Layouts.forLayer(Layer.LETTERS, TestLanguages.keyboard(id), numberRow = numberRow))

    @Test fun qwertyUsesGInTheMiddleRow() {
        assertEquals(Stick(1, Key.Char("g")), stick("en_US"))
    }

    @Test fun aNumberRowMovesTheStickOneRowDown() {
        assertEquals(2, stick("en_US", numberRow = true)?.row)
        assertEquals("g", stick("en_US", numberRow = true)?.key?.label)
    }

    @Test fun otherArrangementsUseGWhereverItIs() {
        // Colemak and Dvorak put G on the top row; it stays the stick.
        assertEquals(0, stick("en_US:COLEMAK")?.row)
        assertEquals("g", stick("en_US:COLEMAK")?.key?.label)
        assertEquals("g", stick("en_US:DVORAK")?.key?.label)
        assertEquals("g", stick("en_US:AZERTY")?.key?.label)
    }

    @Test fun aLayoutWithoutGUsesTheKeyInItsPlace() {
        // ЙЦУКЕН: фывапролджэ, fifth key п.
        assertEquals(Stick(1, Key.Char("п")), stick("ru"))
    }

    @Test fun everyShippedLetterLayoutHasOneStick() {
        for (keyboard in TypingKeyboard.all(TestLanguages.catalog)) for (numberRow in listOf(false, true)) {
            val layout = Layouts.forLayer(Layer.LETTERS, keyboard, numberRow = numberRow)
            val stick = stickKey(layout)
            val label = "${keyboard.id} numberRow=$numberRow"
            val first = if (numberRow) 1 else 0
            assertTrue(label, stick != null && stick.row in first..first + 2)
            // Below it there is at least the bottom row (Space), so the zone under it is on the keyboard.
            assertTrue(label, layout.rows.size - 1 - stick!!.row >= 1)
            // The key is in that row, and nowhere else: KeysPanel finds it by equality.
            assertEquals(label, 1, layout.rows[stick.row].count { it == stick.key })
            assertEquals(label, 1, layout.rows.flatten().count { it == stick.key })
        }
    }

    @Test fun symbolsAndNumberPadsHaveNone() {
        assertNull(stickKey(Layouts.SYMBOLS))
        assertNull(stickKey(Layouts.SYMBOLS_2))
        for (pad in NumberPad.entries) assertNull(stickKey(Layouts.numberPad(pad)))
    }
}
