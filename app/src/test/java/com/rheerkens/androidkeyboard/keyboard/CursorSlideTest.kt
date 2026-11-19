package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.keyboard.CursorSlide.Edge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorSlideTest {
    /** A 48 dp row; the key is 130 dp wide in a 400 dp screen, with three rows and the strip above it. */
    private val zones = CursorSlide.Zones(row = 48f, left = -115f, right = 245f, top = -196f, bottom = 60f)

    /** A finger that went down in the middle of the key. */
    private fun slide() = CursorSlide(zones, 65f, 24f, 0L)

    /** Moves in small slow steps (10 dp per 100 ms) and returns the characters and lines moved in total. */
    private fun CursorSlide.glide(fromX: Float, fromY: Float, toX: Float, toY: Float, startMs: Long): Pair<Int, Int> {
        val steps = maxOf(1, (maxOf(kotlin.math.abs(toX - fromX), kotlin.math.abs(toY - fromY)) / 5f).toInt())
        var chars = 0
        var lines = 0
        for (i in 1..steps) {
            moveTo(fromX + (toX - fromX) * i / steps, fromY + (toY - fromY) * i / steps, startMs + i * 50L)
            chars += this.chars
            lines += this.lines
        }
        return chars to lines
    }

    @Test fun aSmallDriftIsStillATap() {
        val s = slide()
        s.moveTo(65f + CursorSlide.START, 30f, 40L)
        assertFalse(s.sliding)
        assertEquals(0, s.chars)
    }

    @Test fun slidingSidewaysMovesACharacterPerStep() {
        val s = slide()
        assertEquals(-6 to 0, s.glide(65f, 24f, 5f, 24f, 0L))
        assertTrue(s.sliding)
    }

    @Test fun comingBackTakesAFullStep() {
        val s = slide()
        s.glide(65f, 24f, 105f, 24f, 0L)
        s.moveTo(101f, 24f, 5_000L)
        assertEquals(0, s.chars)
        s.moveTo(94f, 24f, 5_100L)
        assertEquals(-1, s.chars)
    }

    @Test fun aRowUpIsALineUp() {
        val s = slide()
        assertEquals(0 to -1, s.glide(65f, 24f, 65f, -26f, 0L))
        assertEquals(0 to -1, s.glide(65f, -26f, 65f, -74f, 10_000L))
        // Back where it started: back on the line it started on.
        assertEquals(0 to 2, s.glide(65f, -74f, 65f, 30f, 20_000L))
    }

    @Test fun aSlantedSidewaysSlideKeepsItsLine() {
        val s = slide()
        // 150 dp to the left while drifting 60 dp up: more than a row, but never mostly upward.
        val (chars, lines) = s.glide(65f, 24f, -85f, -36f, 0L)
        assertEquals(-15, chars)
        assertEquals(0, lines)
    }

    @Test fun aFastSlideCoversMore() {
        val slow = slide()
        val slowChars = slow.glide(65f, 24f, 165f, 24f, 0L).first
        val fast = slide()
        var fastChars = 0
        for (i in 1..10) {
            fast.moveTo(65f + i * 10f, 24f, i * 5L)
            fastChars += fast.chars
        }
        assertEquals(10, slowChars)
        assertTrue("fast $fastChars", fastChars > 20)
        assertEquals(1f, CursorSlide.gain(0.1f), 0f)
        assertEquals(CursorSlide.MAX_GAIN, CursorSlide.gain(5f), 0f)
    }

    @Test fun zonesAreReportedAtTheEdges() {
        val s = slide()
        s.glide(65f, 24f, -100f, 24f, 0L)
        assertNull(s.edge)
        s.moveTo(-120f, 24f, 20_000L)
        assertEquals(Edge.LEFT, s.edge)
        // In the zone the finger's own travel no longer counts: the zone moves the cursor.
        s.moveTo(-140f, 24f, 20_100L)
        assertEquals(0, s.chars)
        s.moveTo(250f, 24f, 30_000L)
        assertEquals(Edge.RIGHT, s.edge)
        s.moveTo(65f, -200f, 40_000L)
        assertEquals(Edge.UP, s.edge)
    }

    @Test fun restingUnderTheKeyIsAZoneBeforeAnySlide() {
        val s = slide()
        s.moveTo(65f, 62f, 100L)
        assertEquals(Edge.DOWN, s.edge)
        assertFalse(s.sliding)
        s.begin()
        assertTrue(s.sliding)
        // Further down still counts no lines by distance.
        s.moveTo(65f, 120f, 200L)
        assertEquals(0, s.lines)
    }

    @Test fun theNubsRoomReachesTheAppAboveAndTheBottomRowsMiddleBelow() {
        // QWERTY's G corner: the bottom of row 1 of 4, 48 dp rows, a 50 dp strip. The nub is 96 dp
        // down the keys; the bottom row's middle is 168 dp down.
        assertEquals(146f to 72f, CursorSlide.nubBounds(48f, 4, 1, 50f))
        // With a number row the stick is a row lower, and so is everything else.
        assertEquals(194f to 72f, CursorSlide.nubBounds(48f, 5, 2, 50f))
    }

    @Test fun fromTheNubASlideGoesDownAsWellAsUp() {
        // A nub target 32 dp across: its centre is 16 dp from its corner.
        val (above, below) = CursorSlide.nubBounds(48f, 4, 1, 50f)
        val nub = CursorSlide(zones.copy(top = 16f - above, bottom = 16f + below), 16f, 16f, 0L)
        assertEquals(0 to 1, nub.glide(16f, 16f, 16f, 66f, 0L))
        assertNull(nub.edge)
        assertEquals(0 to -2, nub.glide(16f, 66f, 16f, -34f, 10_000L))
        nub.moveTo(16f, 95f, 20_000L)
        assertEquals(Edge.DOWN, nub.edge)
        nub.moveTo(16f, -140f, 30_000L)
        assertEquals(Edge.UP, nub.edge)
    }
}

class ShiftForSelectingTest {
    private fun controller() = KeyboardController(com.rheerkens.androidkeyboard.dictionary.TestLanguages.keyboard("en_US"))

    @Test fun aTappedShiftSelectsWithTheNextSlide() {
        val kc = controller()
        assertFalse(kc.shiftTapped)
        kc.onShiftTap()
        assertTrue(kc.shiftTapped)
        kc.onCharTyped()
        assertFalse(kc.shiftTapped)
    }

    @Test fun autoCapitalizationAndCapsLockDoNot() {
        val kc = controller()
        kc.setAutoShift(true)
        assertEquals(ShiftState.ONCE, kc.state.value.shift)
        assertFalse(kc.shiftTapped)
        // Tapping Shift on an automatic capital turns it off.
        kc.onShiftTap()
        assertFalse(kc.shiftTapped)
        kc.onShiftTap()
        kc.onShiftTap()
        assertEquals(ShiftState.LOCKED, kc.state.value.shift)
        assertFalse(kc.shiftTapped)
    }
}
