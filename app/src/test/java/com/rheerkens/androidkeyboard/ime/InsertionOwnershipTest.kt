package com.rheerkens.androidkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertionOwnershipTest {

    @Test
    fun liveTextOffAnyMoveAwayFromTheAnchorIsNotOwned() {
        val o = InsertionOwnership()
        o.begin(10, 10)
        assertTrue(o.owns(10, 10, -1, -1))
        assertFalse("cursor moved from 10 to 2", o.owns(2, 2, -1, -1))
        assertEquals(10 to 10, o.expectedSelection())
    }

    @Test
    fun composingUpdatesAreOwnedIncludingLateOnes() {
        val o = InsertionOwnership()
        o.begin(10, 10)
        o.composed(4) // " run"
        o.composed(8) // " run the"
        // The editor reports the older composing state first, then the newer one.
        assertTrue(o.owns(14, 14, 10, 14))
        assertTrue(o.owns(18, 18, 10, 18))
        assertEquals(18 to 18, o.expectedSelection())
    }

    @Test
    fun movingTheCursorWhileComposingIsNotOwned() {
        val o = InsertionOwnership()
        o.begin(10, 10)
        o.composed(8)
        assertTrue(o.owns(18, 18, 10, 18))
        assertFalse("tap inside earlier text", o.owns(2, 2, 10, 18))
        assertFalse("composing region dropped by the editor", o.owns(18, 18, -1, -1))
        assertFalse("selection inside composing text", o.owns(12, 15, 10, 18))
    }

    @Test
    fun aMoveRightAfterOurOwnWriteIsStillCaught() {
        val o = InsertionOwnership()
        o.begin(10, 10)
        o.composed(4)
        // No time window: a user move immediately after our write is not owned.
        assertFalse(o.owns(2, 2, -1, -1))
    }

    @Test
    fun continuousRestartAnchorsAtTheCommittedCursor() {
        val o = InsertionOwnership()
        o.begin(10, 10)
        o.composed(8)
        o.committed(9) // " run the " replaces composing text
        o.begin(10, 10) // next session starts before the editor reports the commit
        assertEquals(19 to 19, o.expectedSelection())
        assertTrue(o.owns(19, 19, -1, -1))
        assertTrue(o.owns(19, 19, -1, -1))
        assertFalse(o.owns(3, 3, -1, -1))
    }

    @Test
    fun commitWithoutComposingStartsAtTheSelectionStart() {
        val o = InsertionOwnership()
        o.begin(14, 10) // backwards selection
        o.committed(5)
        o.observedIdle()
        o.begin(15, 15)
        assertEquals(15 to 15, o.expectedSelection())
    }

    @Test
    fun clearingComposingReturnsTheCursorToTheAnchor() {
        val o = InsertionOwnership()
        o.begin(10, 10)
        o.composed(6)
        o.clearedComposing()
        assertTrue(o.owns(10, 10, -1, -1))
    }

    @Test
    fun aCancelledSessionLeavesNoStaleAnchor() {
        val o = InsertionOwnership()
        o.begin(10, 10)
        o.composed(6)
        o.clearedComposing() // cancelled: switch to keys
        // The user moves the cursor in keys mode before the editor reported the clear.
        o.begin(3, 3) // back to voice
        assertEquals(3 to 3, o.expectedSelection())
        o.composed(4)
        assertTrue(o.owns(7, 7, 3, 7))
    }

    @Test
    fun idleUpdatesInAnyModeClearAPendingCommit() {
        val o = InsertionOwnership()
        o.begin(10, 10)
        o.committed(5)
        o.observedIdle() // editor reported something while no session ran
        o.begin(2, 2)
        assertEquals(2 to 2, o.expectedSelection())
    }
}
