package com.rheerkens.androidkeyboard.keyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StickNubTest {
    @Test
    fun theNubLeansTheMainWayTheCursorMoved() {
        assertEquals(Lean.RIGHT, Lean.of(2, 0))
        assertEquals(Lean.LEFT, Lean.of(-1, 0))
        assertEquals(Lean.UP, Lean.of(0, -1))
        assertEquals(Lean.DOWN, Lean.of(1, 3))
        // A tie reads as sideways, the way a slanted slide stays on its line.
        assertEquals(Lean.LEFT, Lean.of(-2, 2))
    }
}
