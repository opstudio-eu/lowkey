package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import org.junit.Assert.*
import org.junit.Test

class NumberRowTest {
    private val keyboards = TypingKeyboard.all(TestLanguages.catalog)

    @Test fun dedicatedNumberRowHidesOnlyTheDuplicateHints() {
        for (keyboard in keyboards) {
            val normal = Layouts.forLayer(Layer.LETTERS, keyboard).rows.flatten().filterIsInstance<Key.Char>()
            val numbered = Layouts.forLayer(Layer.LETTERS, keyboard, numberRow = true)
                .rows.drop(1).flatten().filterIsInstance<Key.Char>()
            assertTrue(normal.any { it.hint != null })
            assertTrue(numbered.all { it.hint == null })
            assertEquals(normal.map { it.alternates }, numbered.map { it.alternates })
        }
    }

    @Test fun toggleAddsDigitsWithoutChangingAnyLanguageRows() {
        for (keyboard in keyboards) {
            val normal = Layouts.forLayer(Layer.LETTERS, keyboard)
            val numbered = Layouts.forLayer(Layer.LETTERS, keyboard, numberRow = true)
            assertEquals("1234567890", numbered.rows.first().filterIsInstance<Key.Char>().joinToString("") { it.output })
            val letters = numbered.rows.drop(1).map { row ->
                row.map { if (it is Key.Char) it.copy(showLongPressHint = true) else it }
            }
            assertEquals(normal.rows, letters)
            assertEquals(normal, Layouts.forLayer(Layer.LETTERS, keyboard, numberRow = false))
        }
    }

    @Test fun symbolLayersKeepTheirExistingNumbersAndSymbols() {
        for (layer in listOf(Layer.SYMBOLS, Layer.SYMBOLS_2)) {
            assertEquals(Layouts.forLayer(layer, keyboards.first()), Layouts.forLayer(layer, keyboards.first(), numberRow = true))
        }
    }

    @Test fun splitNumberRowKeepsDigitsUniqueAndLetterBoundariesUnchanged() {
        val keyboard = keyboards.first { it.id == "en_US" }
        val rows = splitRows(Layouts.forLayer(Layer.LETTERS, keyboard, numberRow = true), keyboard, Layer.LETTERS)
        fun letters(keys: List<Key>) = keys.filterIsInstance<Key.Char>().joinToString("") { it.output }
        assertEquals("12345", letters(rows[0].left))
        assertEquals("67890", letters(rows[0].right))
        assertEquals("qwert", letters(rows[1].left))
        assertEquals("yuiop", letters(rows[1].right))
        assertEquals("asdfg", letters(rows[2].left))
        assertEquals("ghjkl", letters(rows[2].right))
        assertEquals("zxcv", letters(rows[3].left))
        assertEquals("vbnm", letters(rows[3].right))
        assertTrue(rows.last().fullWidth)
    }
}
