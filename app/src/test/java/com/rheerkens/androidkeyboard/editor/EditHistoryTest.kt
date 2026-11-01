package com.rheerkens.androidkeyboard.editor

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    @Test
    fun undoesAndRedoesInOrder() {
        val h = EditHistory<String>()
        var state = ""
        fun change(to: String) { h.record(state); state = to }
        change("a"); change("ab"); change("abc")
        state = h.undo(state)!!
        state = h.undo(state)!!
        assertEquals("a", state)
        state = h.redo(state)!!
        assertEquals("ab", state)
        assertTrue(h.canRedo)
        change("abX")
        assertFalse(h.canRedo)
        assertNull(h.redo(state))
        assertEquals("ab", h.undo(state))
    }

    @Test
    fun nothingToUndoAtTheStart() {
        val h = EditHistory<Int>()
        assertFalse(h.canUndo)
        assertNull(h.undo(1))
    }

    @Test
    fun pinchZoomsAboutTheFingersAndStaysInBounds() {
        val z = ViewZoom().pinch(2f, Offset(100f, 50f), Offset.Zero, 400f, 200f)
        assertEquals(2f, z.zoom)
        // The point under the fingers stays put: (100 - pan) / zoom == 100 before the pinch.
        assertEquals(100f, (100f - z.pan.x) / z.zoom, 0.01f)
        assertEquals(50f, (50f - z.pan.y) / z.zoom, 0.01f)
        assertEquals(1f, z.pinch(0.1f, Offset(100f, 50f), Offset.Zero, 400f, 200f).zoom)
        assertEquals(ViewZoom.MAX, z.pinch(100f, Offset.Zero, Offset.Zero, 400f, 200f).zoom)
        // Panning can't drag the image's edge into view.
        assertEquals(0f, z.pinch(1f, Offset.Zero, Offset(500f, 0f), 400f, 200f).pan.x)
        assertEquals(-400f, z.pinch(1f, Offset.Zero, Offset(-900f, 0f), 400f, 200f).pan.x)
    }
}
