package com.rheerkens.androidkeyboard.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyRolloverTest {
    @Test fun reversedReleaseOrderStillTypesInPressOrderExactlyOnce() {
        val keys = KeyRollover()
        val text = StringBuilder()
        keys.press(1) { text.append('a') }
        assertEquals("", text.toString())
        keys.press(2) { text.append('b') }
        assertEquals("a", text.toString())
        keys.release(2)
        keys.release(1)
        assertEquals("ab", text.toString())
    }

    @Test fun cancelledOrLongHeldKeysAreNeverFlushedByAnotherFinger() {
        val keys = KeyRollover()
        val text = StringBuilder()
        keys.press(1) { text.append('a') }
        keys.cancel(1) // long press owns its popup, or slide/cancel abandoned the key
        keys.press(2) { text.append('b') }
        keys.release(1)
        keys.release(2)
        assertEquals("b", text.toString())
    }

    @Test fun aKeyThatActsOnTouchFirstTypesTheLettersStillHeld() {
        val keys = KeyRollover()
        val text = StringBuilder()
        keys.press(1) { text.append('a') }
        keys.press(2) { text.append('b') }
        keys.flush() // Backspace, emoji or Globe touched while both letters are held
        text.append('<')
        keys.release(1)
        keys.release(2)
        assertEquals("ab<", text.toString())
    }

    @Test fun shiftTappedWhileALetterIsHeldAppliesAfterThatLetter() {
        val keys = KeyRollover()
        val text = StringBuilder()
        var shift = false
        keys.press("s") { text.append(if (shift) 'S' else 's') }
        keys.press("shift") { shift = true }
        keys.release("shift")
        keys.release("s")
        keys.press("d") { text.append(if (shift) 'D' else 'd') }
        keys.release("d")
        assertEquals("sD", text.toString())
    }

    @Test fun overlappingFingersOnTheSameKeyHaveDistinctOwnership() {
        val keys = KeyRollover()
        val text = StringBuilder()
        val first = Any()
        val second = Any()
        keys.press(first) { text.append('s') }
        keys.press(second) { text.append('s') }
        keys.release(second)
        keys.release(first)
        assertEquals("ss", text.toString())
    }
}
