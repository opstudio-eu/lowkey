package com.rheerkens.androidkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class BarSwipeTest {
    @Test fun smallMovementsRemainTaps() {
        assertEquals(BarSwipe.PENDING, barSwipe(8f, -12f, true, 24f))
        assertEquals(BarSwipe.PENDING, barSwipe(8f, 12f, false, 24f))
    }

    @Test fun upExpandsAndDownMinimizes() {
        assertEquals(BarSwipe.TOGGLE, barSwipe(2f, -24f, true, 24f))
        assertEquals(BarSwipe.TOGGLE, barSwipe(-2f, 24f, false, 24f))
    }

    @Test fun oppositeDirectionCannotChangeSize() {
        assertEquals(BarSwipe.IGNORE, barSwipe(0f, 30f, true, 24f))
        assertEquals(BarSwipe.IGNORE, barSwipe(0f, -30f, false, 24f))
    }

    @Test fun horizontalClipboardScrollingCannotExpand() {
        assertEquals(BarSwipe.IGNORE, barSwipe(40f, -26f, true, 24f))
        assertEquals(BarSwipe.IGNORE, barSwipe(-40f, -26f, true, 24f))
    }

    @Test fun heldClipboardPreviewKeepsItsUpwardGesture() {
        assertEquals(BarSwipe.IGNORE, barSwipe(0f, -60f, true, 24f, held = true))
    }

    @Test fun scrollingClaimsTheGestureBeforeTheLargerExpandThreshold() {
        assertEquals(BarSwipe.IGNORE, barSwipe(10f, -18f, true, 24f, horizontalSlop = 8f))
        assertEquals(BarSwipe.TOGGLE, barSwipe(3f, -24f, true, 24f, horizontalSlop = 8f))
    }
}
