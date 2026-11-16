package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import com.rheerkens.androidkeyboard.settings.KeyArrangement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchRowTest {
    @Test
    fun qwertyGapsBelongToAAndL() {
        val middle = touchRow(Layouts.letters(TestLanguages.keyboard("en_US")).rows[1])
        assertEquals("asdfghjkl", middle.joinToString("") { (it.key as Key.Char).label })
        assertEquals(0.5f to 0f, middle.first().before to middle.first().after)
        assertEquals(0f to 0.5f, middle.last().before to middle.last().after)
        assertTrue(middle.drop(1).dropLast(1).all { it.before == 0f && it.after == 0f })
        assertEquals(10f, middle.sumOf { it.weight.toDouble() }.toFloat())
    }

    @Test
    fun rowsWithoutGapsAreUnchanged() {
        val bottom = Layouts.letters(TestLanguages.keyboard("en_US")).rows[3]
        assertEquals(bottom.map { TouchKey(it) }, touchRow(bottom))
    }

    @Test
    fun everyKeyboardKeepsItsRowWidthsAndLeavesNoGapBesideALetter() {
        val keyboards = TestLanguages.catalog.languages.map { TypingKeyboard(it) } +
            KeyArrangement.entries.filter { it != KeyArrangement.QWERTY }.map { TestLanguages.keyboard("en_US:${it.name}") }
        for (keyboard in keyboards) {
            for ((index, row) in Layouts.letters(keyboard).rows.withIndex()) {
                val touch = touchRow(row)
                assertEquals("${keyboard.id} row $index", row.sumOf { it.weight.toDouble() }, touch.sumOf { it.weight.toDouble() }, 1e-4)
                assertEquals("${keyboard.id} row $index", row.filter { it !is Key.Gap }, touch.map { it.key }.filter { it !is Key.Gap })
                touch.zipWithNext().forEach { (a, b) ->
                    assertTrue("${keyboard.id} row $index", !(a.key is Key.Gap && b.key is Key.Char) && !(a.key is Key.Char && b.key is Key.Gap))
                }
            }
        }
    }
}
