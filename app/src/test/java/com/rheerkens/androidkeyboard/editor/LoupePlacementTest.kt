package com.rheerkens.androidkeyboard.editor

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class LoupePlacementTest {
    private val left = listOf(0, 2)
    private val right = listOf(1, 3)
    private val top = listOf(0, 1)

    private fun corner(x: Float, y: Float, allowed: List<Int>, current: Int? = null) =
        LoupePlacement.corner(Offset(x, y), current, allowed, 1000f, 1600f, 150f, 30f, 300f)

    @Test
    fun rightHandedStaysOnTheLeft() {
        assertEquals(0, corner(900f, 1500f, left)) // Finger bottom right: top left.
        assertEquals(2, corner(800f, 300f, left)) // Finger top right: bottom left.
        assertEquals(2, corner(100f, 100f, left)) // Finger top left: bottom left, never the right side.
    }

    @Test
    fun leftHandedStaysOnTheRightAndTopStaysOnTop() {
        assertEquals(3, corner(100f, 100f, right))
        assertEquals(1, corner(200f, 1500f, right))
        assertEquals(1, corner(100f, 800f, top))
        assertEquals(0, corner(900f, 800f, top))
    }

    @Test
    fun staysPutUntilTheFingerComesNear() {
        assertEquals(0, corner(500f, 800f, left, current = 0))
        assertEquals(0, corner(450f, 400f, left, current = 0))
        // Within the clearance of the top-left loupe (centred at 180, 180) it moves down, not right.
        assertEquals(2, corner(250f, 250f, left, current = 0))
        // A corner that's no longer allowed (setting changed) is left at once.
        assertEquals(1, corner(300f, 800f, top, current = 2))
    }
}
