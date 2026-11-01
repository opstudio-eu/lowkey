package com.rheerkens.androidkeyboard.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class LineSnapTest {
    @Test
    fun nearlyStraightLinesSnapAndKeepTheirLength() {
        val (x, y) = snapLineEnd(0f, 0f, 100f, 6f) // About 3.4° off horizontal.
        assertEquals(100.18f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
        val (vx, vy) = snapLineEnd(10f, 10f, 13f, -90f) // Nearly straight up.
        assertEquals(10f, vx, 0.01f)
        assertEquals(-90.04f, vy, 0.01f)
        val (dx, dy) = snapLineEnd(0f, 0f, 100f, 96f) // Nearly 45°.
        assertEquals(dx, dy, 0.01f)
    }

    @Test
    fun otherAnglesAndZeroLengthStayPut() {
        assertEquals(100f to 30f, snapLineEnd(0f, 0f, 100f, 30f))
        assertEquals(5f to 5f, snapLineEnd(5f, 5f, 5f, 5f))
    }
}
