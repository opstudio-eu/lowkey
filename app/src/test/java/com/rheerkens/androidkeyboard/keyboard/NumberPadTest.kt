package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import org.junit.Assert.*
import org.junit.Test

class NumberPadTest {
    private val keyboard = TestLanguages.keyboard("en_US")

    private fun labels(row: List<Key>) = row.filterIsInstance<Key.Char>().joinToString(" ") { it.output }

    @Test fun digitsSitInAPhoneGridWithZeroBelowEight() {
        for (pad in NumberPad.entries) {
            val rows = Layouts.forLayer(Layer.NUMBERS, keyboard, pad = pad).rows
            assertEquals(4, rows.size)
            val digits = rows.map { row -> row.filterIsInstance<Key.Char>().map { it.output }.filter { it.single().isDigit() }.joinToString("") }
            assertEquals(listOf("123", "456", "789", "0"), digits)
            val widths = rows.map { row -> row.sumOf { it.weight.toDouble() } }
            widths.forEach { assertEquals("$pad", widths.first(), it, 1e-4) }
            fun centre(row: List<Key>, label: String): Double {
                val i = row.indexOfFirst { it is Key.Char && it.output == label }
                return row.take(i).sumOf { it.weight.toDouble() } + row[i].weight / 2.0
            }
            assertEquals("$pad", centre(rows[2], "8"), centre(rows[3], "0"), 1e-4)
            // Every digit is wider than the letter-style keys beside it.
            val digit = rows[0].filterIsInstance<Key.Char>().first { it.output == "1" }
            assertTrue(digit.weight > rows[0].first().weight)
        }
    }

    @Test fun eachPadOffersWhatItsFieldsType() {
        assertEquals("- 1 2 3", labels(Layouts.numberPad(NumberPad.NUMBER).rows[0]))
        assertEquals(", 0 .", labels(Layouts.numberPad(NumberPad.NUMBER).rows[3]))
        assertEquals("+ 4 5 6", labels(Layouts.numberPad(NumberPad.PHONE).rows[1]))
        assertEquals("* 0 #", labels(Layouts.numberPad(NumberPad.PHONE).rows[3]))
        assertEquals("/ 4 5 6", labels(Layouts.numberPad(NumberPad.DATETIME).rows[1]))
        assertEquals("7 8 9 :", labels(Layouts.numberPad(NumberPad.DATETIME).rows[2]))
        for (pad in NumberPad.entries) {
            val keys = Layouts.numberPad(pad).rows.flatten()
            assertEquals(1, keys.count { it is Key.Backspace })
            assertEquals(1, keys.count { it is Key.Enter })
            assertEquals(listOf(Key.Space(1f, showsLanguage = false)), keys.filterIsInstance<Key.Space>())
            assertEquals(listOf(Layer.SYMBOLS, Layer.LETTERS), keys.filterIsInstance<Key.LayerSwitch>().map { it.target })
        }
    }

    @Test fun symbolLayersReturnToTheFieldsOwnKeys() {
        for (layer in listOf(Layer.SYMBOLS, Layer.SYMBOLS_2)) {
            val plain = Layouts.forLayer(layer, keyboard).rows.flatten().filterIsInstance<Key.LayerSwitch>()
            val padded = Layouts.forLayer(layer, keyboard, pad = NumberPad.PHONE).rows.flatten().filterIsInstance<Key.LayerSwitch>()
            assertTrue(plain.any { it.target == Layer.LETTERS && it.label == "ABC" })
            assertTrue(padded.none { it.target == Layer.LETTERS })
            assertTrue(padded.any { it.target == Layer.NUMBERS && it.label == "123" })
            assertEquals(plain.filter { it.target != Layer.LETTERS }, padded.filter { it.target != Layer.NUMBERS })
        }
        // The letters keep their ?123 key, and the pad keeps its ABC key.
        assertEquals(Layouts.forLayer(Layer.LETTERS, keyboard), Layouts.forLayer(Layer.LETTERS, keyboard, pad = NumberPad.NUMBER))
        assertEquals(Layouts.numberPad(NumberPad.NUMBER), Layouts.forLayer(Layer.NUMBERS, keyboard, pad = NumberPad.NUMBER))
    }

    @Test fun aNumberFieldOpensOnItsPadAndComesBackToIt() {
        val controller = KeyboardController(keyboard)
        controller.reset(NumberPad.PHONE)
        assertEquals(Layer.NUMBERS, controller.state.value.layer)
        assertEquals(NumberPad.PHONE, controller.state.value.pad)
        controller.leaveSymbols() // Space on the pad keeps the pad.
        assertEquals(Layer.NUMBERS, controller.state.value.layer)
        controller.setLayer(Layer.SYMBOLS)
        controller.leaveSymbols()
        assertEquals(Layer.NUMBERS, controller.state.value.layer)
        controller.setLayer(Layer.LETTERS)
        controller.leaveSymbols() // Letters chosen with ABC stay until the next field.
        assertEquals(Layer.LETTERS, controller.state.value.layer)
        controller.showHome()
        assertEquals(Layer.NUMBERS, controller.state.value.layer)

        controller.reset()
        assertEquals(Layer.LETTERS, controller.state.value.layer)
        assertNull(controller.state.value.pad)
        controller.setLayer(Layer.SYMBOLS_2)
        controller.leaveSymbols()
        assertEquals(Layer.LETTERS, controller.state.value.layer)
    }
}
