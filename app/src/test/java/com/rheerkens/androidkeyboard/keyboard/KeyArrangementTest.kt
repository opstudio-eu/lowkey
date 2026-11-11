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
        }
    }

    @Test
    fun rowsAreTheTraditionalOnes() {
        val expected = mapOf(
            KeyArrangement.QWERTY to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
            KeyArrangement.AZERTY to listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"),
            KeyArrangement.QWERTZ to listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"),
            KeyArrangement.DVORAK to listOf("',.pyfgcrl", "aoeuidhtns", "qjkxbmwvz"),
            KeyArrangement.COLEMAK to listOf("qwfpgjluy;", "arstdhneio", "zxcvbkm"),
        )
        assertEquals(KeyArrangement.entries.toSet(), expected.keys)
        for ((arrangement, rows) in expected) assertEquals(arrangement.name, rows, rowLabels(arrangement))
    }

    @Test
    fun rowsFillTheWidthLikeQwerty() {
        for (arrangement in KeyArrangement.entries) {
            val rows = Layouts.letters(TypingKeyboard(TypingLayout.EN_US, arrangement)).rows
            fun width(row: List<Key>) = row.sumOf { it.weight.toDouble() }.toFloat()
            // Top and middle rows are ten keys wide; centred with gaps when shorter.
            assertEquals(arrangement.name, 10f, width(rows[0]))
            assertEquals(arrangement.name, 10f, width(rows[1]))
            // The third row too, unless its letters leave Shift and Backspace less than a key each.
            val third = rows[2]
            val letters = third.count { it is Key.Char }
            assertTrue(third.first() is Key.Shift && third.last() is Key.Backspace)
            assertEquals(arrangement.name, maxOf(10f, letters + 2f), width(third))
            // Top-row digits on long-press, 1 to 0.
            assertEquals("1234567890", rows[0].joinToString("") { (it as Key.Char).longPressOutput.orEmpty() })
        }
        // QWERTY is unchanged: half-key gaps around a–l, Shift and Backspace one and a half keys.
        val qwerty = Layouts.letters(TypingKeyboard.DEFAULT).rows
        assertEquals(Key.Gap(0.5f), qwerty[1].first())
        assertEquals(Key.Shift(1.5f), qwerty[2].first())
    }

    @Test
    fun letterCentresAreWhereTheKeysAreDrawn() {
        val azerty = Layouts.letterCentres(KeyArrangement.AZERTY).map { it.toMap() }
        assertEquals(0.5, azerty[0].getValue('a'), 1e-9)
        assertEquals(9.5, azerty[1].getValue('m'), 1e-9)
        // After Shift, one and a half keys wide.
        assertEquals(2.0, azerty[2].getValue('w'), 1e-9)
        // Dvorak's third row is eleven keys wide on screen, squeezed into the same width.
        val dvorak = Layouts.letterCentres(KeyArrangement.DVORAK)[2].toMap()
        assertEquals(1.5 * 10 / 11, dvorak.getValue('q'), 1e-9)
        assertEquals(9.5 * 10 / 11, dvorak.getValue('z'), 1e-9)
        // Punctuation keys aren't letters to correct.
        assertTrue(Layouts.letterCentres(KeyArrangement.COLEMAK)[0].none { it.first == ';' })
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
        assertEquals("English (US)", TypingKeyboard.DEFAULT.spaceLabel)
        val nlAzerty = TypingKeyboard(TypingLayout.NL_NL, KeyArrangement.AZERTY)
        assertEquals("Nederlands · AZERTY", nlAzerty.label)
        assertEquals("NL · AZERTY", nlAzerty.spaceLabel)
    }

    // The grids the app's suggesters use.
    private fun grid(arrangement: KeyArrangement) = Layouts.keyGrid(arrangement)

    @Test
    fun appGridsAreQwertysOwnOrTheDrawnKeys() {
        assertTrue(Layouts.keyGrid(KeyArrangement.QWERTY) === KeyGrid.QWERTY)
        for (arrangement in KeyArrangement.entries - KeyArrangement.QWERTY) {
            val drawn = KeyGrid(Layouts.letterCentres(arrangement))
            for (a in 'a'..'z') for (b in 'a'..'z') {
                assertEquals("${arrangement.name} $a$b", drawn.adjacent(a, b), Layouts.keyGrid(arrangement).adjacent(a, b))
            }
        }
        assertTrue(Layouts.keyGrid(KeyArrangement.AZERTY).adjacent('m', 'l'))
    }

    @Test
    fun neighbouringKeysFollowTheArrangement() {
        val azerty = grid(KeyArrangement.AZERTY)
        val qwertz = grid(KeyArrangement.QWERTZ)
        // On AZERTY, m sits at the end of the middle row next to l; on QWERTY it doesn't.
        assertTrue(azerty.adjacent('m', 'l'))
        assertFalse(KeyGrid.QWERTY.adjacent('m', 'l'))
        // AZERTY's a is in the top row: next to z and q, no longer next to w.
        assertTrue(azerty.adjacent('a', 'z'))
        assertTrue(azerty.adjacent('a', 'q'))
        assertFalse(azerty.adjacent('a', 'w'))
        // w starts the third row after the wide Shift, a key and a half from q.
        assertFalse(azerty.adjacent('q', 'w'))
        assertTrue(azerty.adjacent('w', 's'))
        // QWERTZ swaps y and z: z sits next to t, y next to x.
        assertTrue(qwertz.adjacent('z', 't'))
        assertTrue(qwertz.adjacent('y', 'x'))
        assertFalse(qwertz.adjacent('y', 't'))
        // Dvorak's squeezed third row: q is under a and o.
        val dvorak = grid(KeyArrangement.DVORAK)
        assertTrue(dvorak.adjacent('q', 'a'))
        assertTrue(dvorak.adjacent('q', 'o'))
        assertFalse(dvorak.adjacent('q', 'e'))
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
    fun suggestionsAndAutocorrectUseTheArrangementsNeighbours() {
        // AZERTY puts m next to l; on QWERTY they're far apart, so "lilk" isn't a slip for "milk".
        val words = listOf("milk", "silk")
        val azerty = Suggester({ Lexicon.fromWords(words) }, grid(KeyArrangement.AZERTY))
        val qwerty = Suggester(words)
        assertEquals("milk", azerty.autocorrection("lilk", emptyList()))
        assertNull(qwerty.autocorrection("lilk", emptyList()))
        assertEquals("milk", azerty.suggest("lilk", emptyList()).first().text)
        assertTrue(qwerty.suggest("lilk", emptyList()).none { it.text == "milk" })
    }
}
