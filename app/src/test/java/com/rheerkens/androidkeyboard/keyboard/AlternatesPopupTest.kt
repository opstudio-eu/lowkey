package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.settings.TypingLayout
import org.junit.Assert.*
import org.junit.Test

class AlternatesPopupTest {
    private val letters = Layouts.letters(TypingLayout.EN_US).rows.flatten().filterIsInstance<Key.Char>().associateBy { it.label }

    @Test
    fun topRowOffersNumberThenAccents() {
        assertEquals(listOf("3", "é", "è", "ê", "ë", "ē", "ę", "ė"), letters.getValue("e").alternates)
        assertEquals(listOf("9", "ó", "ò", "ô", "ö", "õ", "ø", "œ"), letters.getValue("o").alternates)
        assertEquals(listOf("1"), letters.getValue("q").alternates)
    }

    @Test
    fun otherRowsOfferAccentsOnly() {
        assertEquals(listOf("á", "à", "â", "ä", "ã", "å", "æ"), letters.getValue("a").alternates)
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
        val controller = KeyboardController(TypingLayout.EN_US)
        controller.onShiftTap()
        assertEquals("Ë", controller.applyCase("ë"))
    }

    // Ten 40 px keys in a 400 px window.
    private fun popup(keyIndex: Int, count: Int) =
        AlternatesPopup(count, keyLeft = keyIndex * 40f, keyWidth = 40f, keyHeight = 50f, windowWidth = 400f,
            cellWidth = 40f, cellHeight = 43f, slop = 10f)

    @Test
    fun leftKeyExtendsRightWithDefaultOverKey() {
        val p = popup(keyIndex = 2, count = 8)
        assertFalse(p.mirrored)
        assertEquals(0f, p.left, 0f)
        assertEquals(0, p.select(20f, 25f))
        assertEquals(1, p.select(60f, -20f))
        assertEquals(7, p.select(300f, -20f))
    }

    @Test
    fun rightKeyMirrorsSoDefaultStaysUnderFinger() {
        val p = popup(keyIndex = 8, count = 8)
        assertTrue(p.mirrored)
        assertEquals(-280f, p.left, 0f)
        assertEquals(0, p.select(20f, 25f))
        assertEquals(1, p.select(-20f, -20f))
        assertEquals(7, p.select(-270f, -20f))
        assertEquals(7, p.choiceAt(0))
    }

    @Test
    fun popupStaysInsideWindowWhenNeitherSideFits() {
        val p = popup(keyIndex = 5, count = 8)
        assertFalse(p.mirrored)
        assertEquals(400f - 320f - 200f, p.left, 0f)
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
