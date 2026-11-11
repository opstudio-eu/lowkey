package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.Corrector
import com.rheerkens.androidkeyboard.dictionary.KeyGrid
import com.rheerkens.androidkeyboard.dictionary.Lexicon
import com.rheerkens.androidkeyboard.dictionary.Suggester
import com.rheerkens.androidkeyboard.settings.KeyArrangement
import com.rheerkens.androidkeyboard.settings.TypingKeyboard
import com.rheerkens.androidkeyboard.settings.TypingLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyArrangementTest {
    private fun rowLabels(arrangement: KeyArrangement): List<String> =
        Layouts.letters(TypingKeyboard(TypingLayout.EN_US, arrangement)).rows.take(3).map { row ->
            row.filterIsInstance<Key.Char>().joinToString("") { it.label }
        }

    @Test
    fun everyArrangementHasAllLettersOnce() {
        for (arrangement in KeyArrangement.entries) {
            val letters = arrangement.rows.joinToString("").filter { it in 'a'..'z' }
            assertEquals(arrangement.name, ('a'..'z').toList(), letters.toList().sorted())
            assertEquals(arrangement.name, arrangement.rows, rowLabels(arrangement))
        }
    }

    @Test
    fun azertyAndQwertzMoveTheTraditionalKeys() {
        assertEquals(listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"), rowLabels(KeyArrangement.AZERTY))
        assertEquals(listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"), rowLabels(KeyArrangement.QWERTZ))
    }

    @Test
    fun rowsFillTheWidthLikeQwerty() {
        for (arrangement in KeyArrangement.entries) {
            val rows = Layouts.letters(TypingKeyboard(TypingLayout.EN_US, arrangement)).rows
            // Top and middle rows are ten keys wide; centred with gaps when shorter.
            assertEquals(arrangement.name, 10f, rows[0].sumOf { it.weight.toDouble() }.toFloat())
            assertEquals(arrangement.name, 10f, rows[1].sumOf { it.weight.toDouble() }.toFloat())
            val third = rows[2]
            assertTrue(third.first() is Key.Shift && third.last() is Key.Backspace)
            assertTrue(arrangement.name, third.first().weight >= 1f)
            // Top-row digits on long-press, 1 to 0.
            assertEquals("1234567890", rows[0].joinToString("") { (it as Key.Char).longPressOutput.orEmpty() })
        }
        // QWERTY is unchanged: half-key gaps around a–l, Shift and Backspace one and a half keys.
        val qwerty = Layouts.letters(TypingKeyboard.DEFAULT).rows
        assertEquals(Key.Gap(0.5f), qwerty[1].first())
        assertEquals(Key.Shift(1.5f), qwerty[2].first())
    }

    @Test
    fun accentsFollowTheLetterNotItsPosition() {
        val azerty = Layouts.letters(TypingKeyboard(TypingLayout.NL_NL, KeyArrangement.AZERTY))
            .rows.flatten().filterIsInstance<Key.Char>().associateBy { it.label }
        assertEquals(listOf("1") + Layouts.ACCENTS.getValue("a"), azerty.getValue("a").alternates)
        assertEquals(Layouts.ACCENTS.getValue("e"), azerty.getValue("e").accents)
        assertTrue(azerty.getValue("'").alternates.isEmpty())
    }

    @Test
    fun globeCyclesLanguagesThenArrangements() {
        val enabled = setOf(
            TypingKeyboard(TypingLayout.NL_NL, KeyArrangement.AZERTY),
            TypingKeyboard(TypingLayout.EN_US, KeyArrangement.DVORAK),
            TypingKeyboard(TypingLayout.EN_US),
        )
        val kc = KeyboardController(TypingKeyboard.DEFAULT)
        assertEquals(TypingKeyboard(TypingLayout.EN_US, KeyArrangement.DVORAK), kc.cycleKeyboard(enabled))
        assertEquals(TypingKeyboard(TypingLayout.NL_NL, KeyArrangement.AZERTY), kc.cycleKeyboard(enabled))
        assertEquals(TypingKeyboard.DEFAULT, kc.cycleKeyboard(enabled))
        // A keyboard switched off meanwhile continues from the start.
        kc.setKeyboard(TypingKeyboard(TypingLayout.EN_US, KeyArrangement.COLEMAK))
        assertEquals(TypingKeyboard.DEFAULT, kc.cycleKeyboard(enabled))
    }

    @Test
    fun labelsNameTheArrangementUnlessQwerty() {
        assertEquals("English (US)", TypingKeyboard.DEFAULT.label)
        assertEquals("Nederlands · AZERTY", TypingKeyboard(TypingLayout.NL_NL, KeyArrangement.AZERTY).label)
    }

    @Test
    fun neighbouringKeysFollowTheArrangement() {
        val azerty = KeyGrid(KeyArrangement.AZERTY.rows)
        val qwertz = KeyGrid(KeyArrangement.QWERTZ.rows)
        // On AZERTY, m sits at the end of the middle row next to l; on QWERTY it doesn't.
        assertTrue(azerty.adjacent('m', 'l'))
        assertFalse(KeyGrid.QWERTY.adjacent('m', 'l'))
        // AZERTY's a is in the top row: next to z, no longer next to w.
        assertTrue(azerty.adjacent('a', 'z'))
        assertFalse(azerty.adjacent('a', 'w'))
        // QWERTZ swaps y and z: z sits next to t, y next to x.
        assertTrue(qwertz.adjacent('z', 't'))
        assertTrue(qwertz.adjacent('y', 'x'))
        assertFalse(qwertz.adjacent('y', 't'))
        // A slip onto a neighbour is cheaper than any other substitution.
        assertTrue(Corrector.distance("lilk", "milk", keys = azerty) < Corrector.distance("lilk", "milk"))
    }

    @Test
    fun qwertyNeighboursAreUnchanged() {
        // The grid used before arrangements: rows staggered by half a key.
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val pos = rows.flatMapIndexed { r, row -> row.mapIndexed { c, ch -> ch to ((c + r * 0.5) to r.toDouble()) } }.toMap()
        for (a in 'a'..'z') for (b in 'a'..'z') {
            val (pa, pb) = pos.getValue(a) to pos.getValue(b)
            val expected = kotlin.math.abs(pa.first - pb.first) <= 1.0 && kotlin.math.abs(pa.second - pb.second) <= 1.0
            assertEquals("$a$b", expected, KeyGrid.QWERTY.adjacent(a, b))
        }
    }

    @Test
    fun autocorrectUsesTheArrangementsNeighbours() {
        // AZERTY puts m next to l; on QWERTY they're far apart, so "lilk" isn't a slip for "milk".
        val words = listOf("milk", "silk")
        val azerty = Suggester({ Lexicon.fromWords(words) }, { KeyGrid(KeyArrangement.AZERTY.rows) })
        assertEquals("milk", azerty.autocorrection("lilk", emptyList()))
        assertNull(Suggester(words).autocorrection("lilk", emptyList()))
    }
}
