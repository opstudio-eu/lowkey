package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import org.junit.Assert.*
import org.junit.Test

class AlternatesPopupTest {
    private val letters = Layouts.letters(TestLanguages.keyboard("en_US")).rows.flatten().filterIsInstance<Key.Char>().associateBy { it.label }

    @Test
    fun topRowOffersNumberThenAccents() {
        assertEquals(listOf("3", "é", "è", "ê", "ë", "ē", "ę", "ė"), letters.getValue("e").alternates)
        assertEquals(listOf("9", "ó", "ò", "ô", "ö", "õ", "ø", "œ"), letters.getValue("o").alternates)
        assertEquals(listOf("1"), letters.getValue("q").alternates)
    }

    @Test
    fun otherRowsOfferAccentsOnly() {
        assertEquals(listOf("á", "à", "â", "ä", "ã", "å", "æ", "ą"), letters.getValue("a").alternates)
        assertEquals(listOf("ç", "ć", "č"), letters.getValue("c").alternates)
        assertEquals(listOf("ñ", "ń"), letters.getValue("n").alternates)
        assertTrue(letters.getValue("b").alternates.isEmpty())
        assertTrue(Layouts.SYMBOLS.rows.flatten().filterIsInstance<Key.Char>().all { it.alternates.isEmpty() })
    }

    @Test
    fun shiftUppercasesAccentsWithoutExpandingSharpS() {
        assertEquals("É", shiftCase("é"))
        assertEquals("Œ", shiftCase("œ"))
        assertEquals("ß", shiftCase("ß"))
        val controller = KeyboardController(TestLanguages.keyboard("en_US"))
        controller.onShiftTap()
        assertEquals("Ë", controller.applyCase("ë"))
    }

    // Ten 40 px keys in a 400 px window.
    private fun popup(keyIndex: Int, count: Int) =
        AlternatesPopup(count, keyLeft = keyIndex * 40f, keyWidth = 40f, keyHeight = 50f, windowWidth = 400f,
            cellWidth = 40f, cellHeight = 43f, slop = 10f)

    @Test
    fun manyAlternatesFormTwoRowsWithTheDefaultAtTheKey() {
        val p = popup(keyIndex = 2, count = 8)
        assertEquals(4, p.columns)
        assertEquals(2, p.rows)
        assertEquals(160f, p.width, 0f)
        assertEquals(86f, p.height, 0f)
        assertEquals(-40f, p.left, 0f)
        assertEquals(0, p.select(20f, 25f))
        assertEquals(1, p.select(60f, 20f))
        assertEquals(4, p.select(20f, -20f))
        assertEquals(7, p.select(-20f, -20f))
    }

    @Test
    fun rightEdgeKeepsDefaultUnderFingerWithoutLeavingTheWindow() {
        val p = popup(keyIndex = 8, count = 8)
        assertEquals(-80f, p.left, 0f)
        assertEquals(0, p.select(20f, 25f))
        assertEquals(1, p.select(60f, 25f))
        assertEquals(4, p.select(20f, -20f))
        assertEquals(2, p.choiceAt(0, 1))
    }

    @Test
    fun popupStaysInsideWindowAcrossTheWholeKeyboard() {
        for (key in 0..9) {
            val p = popup(keyIndex = key, count = 9)
            assertEquals(5, p.columns)
            assertEquals(2, p.rows)
            assertTrue(key * 40f + p.left >= 0f)
            assertTrue(key * 40f + p.left + p.width <= 400f)
            assertEquals(0, p.select(20f, 25f))
        }
    }

    @Test fun aPartialTopRowDoesNotSelectPhantomAccents() {
        val p = popup(keyIndex = 2, count = 7)
        assertNull(p.choiceAt(0, 0))
        assertNull(p.select(-20f, -20f))
        assertEquals(0, p.select(20f, 20f))
    }

    @Test fun smallSetsStayOnOneRowAndNarrowWindowsStillFit() {
        assertEquals(1, popup(keyIndex = 0, count = 3).rows)
        val narrow = AlternatesPopup(9, 40f, 40f, 50f, 120f, 40f, 43f, 10f)
        assertEquals(3, narrow.columns)
        assertEquals(3, narrow.rows)
        assertEquals(120f, narrow.width, 0f)
    }

    @Test
    fun slidingAwayCancelsAndReturningSelectsAgain() {
        val p = popup(keyIndex = 0, count = 1)
        assertEquals(0, p.select(20f, 25f))
        assertNull(p.select(20f, 70f))
        assertNull(p.select(20f, -60f))
        assertNull(p.select(55f, 25f))
        assertEquals(0, p.select(45f, 25f))
    }
}
