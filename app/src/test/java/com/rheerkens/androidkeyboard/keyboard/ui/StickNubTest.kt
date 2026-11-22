package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun craterNotchFindsSurroundingKeyCornersInCapCoordinates() {
        val corner = Offset(200f, 150f)
        val corners = listOf(corner)
        val outer = 24f

        // G (above-left), H (above-right), V (below-left), B (below-right) all touch the corner.
        assertEquals(Offset(100f, 100f), craterNotch(Rect(100f, 50f, 200f, 150f), corners, outer))
        assertEquals(Offset(0f, 100f), craterNotch(Rect(200f, 50f, 300f, 150f), corners, outer))
        assertEquals(Offset(100f, 0f), craterNotch(Rect(100f, 150f, 200f, 250f), corners, outer))
        assertEquals(Offset(0f, 0f), craterNotch(Rect(200f, 150f, 300f, 250f), corners, outer))

        // Keys further away (e.g. F, J) are untouched.
        assertNull(craterNotch(Rect(0f, 50f, 100f, 150f), corners, outer))
        assertNull(craterNotch(Rect(300f, 50f, 400f, 150f), corners, outer))
        assertNull(craterNotch(Rect(100f, 50f, 200f, 150f), emptyList(), outer))
    }

    @Test
    fun craterNotchHandlesStaggeredKeysWithinBevelReach() {
        val corner = Offset(200f, 150f)
        // A staggered key whose top edge passes 10px below the corner still gets a local notch offset.
        val cap = Rect(160f, 160f, 260f, 260f)
        assertEquals(Offset(40f, -10f), craterNotch(cap, listOf(corner), outerRadius = 24f))
        assertNull(craterNotch(cap, listOf(corner), outerRadius = 10f))
    }
}
