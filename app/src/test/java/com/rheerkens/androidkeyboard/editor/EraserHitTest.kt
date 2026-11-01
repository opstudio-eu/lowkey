package com.rheerkens.androidkeyboard.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserHitTest {
    private val stroke = Mark.Stroke(listOf(0f to 0f, 100f to 0f, 100f to 100f), 0, 10f, 255)

    @Test
    fun strokesAreHitAlongTheirLineIncludingTheirWidth() {
        assertTrue(touches(stroke, 50f, 12f, 8f)) // 12 away, reach 8 + 5.
        assertFalse(touches(stroke, 50f, 20f, 8f))
        assertTrue(touches(stroke, 105f, 50f, 1f)) // On the second segment.
        assertTrue(touches(Mark.Stroke(listOf(5f to 5f), 0, 4f, 255), 8f, 5f, 2f)) // A dot.
    }

    @Test
    fun shapesAreHitOnTheirOutlineNotInside() {
        val rect = Mark.Shape(ShapeKind.RECTANGLE, 0f, 0f, 200f, 100f, 0, 4f)
        assertTrue(touches(rect, 200f, 50f, 5f))
        assertFalse(touches(rect, 100f, 50f, 5f)) // The middle of an outlined rectangle is empty.
        val oval = Mark.Shape(ShapeKind.OVAL, 0f, 0f, 200f, 100f, 0, 4f)
        assertTrue(touches(oval, 100f, 0f, 5f)) // Top of the oval.
        assertFalse(touches(oval, 5f, 5f, 5f)) // The rectangle's corner is outside the oval.
        val line = Mark.Shape(ShapeKind.LINE, 0f, 0f, 100f, 100f, 0, 4f)
        assertTrue(touches(line, 50f, 52f, 3f))
        assertFalse(touches(line, 0f, 100f, 3f))
    }

    @Test
    fun filledShapesAreHitAnywhereInside() {
        val rect = Mark.Shape(ShapeKind.RECTANGLE, 200f, 100f, 0f, 0f, BLUR, 4f, filled = true) // Drawn right to left.
        assertTrue(touches(rect, 100f, 50f, 1f))
        assertFalse(touches(rect, 100f, 130f, 1f))
        val oval = Mark.Shape(ShapeKind.OVAL, 0f, 0f, 200f, 100f, 0, 4f, filled = true)
        assertTrue(touches(oval, 100f, 50f, 1f))
        assertFalse(touches(oval, 5f, 5f, 1f)) // Still outside the oval itself.
        // Lines and arrows have no inside, even if marked filled.
        assertFalse(touches(Mark.Shape(ShapeKind.LINE, 0f, 0f, 100f, 0f, 0, 4f, filled = true), 50f, 40f, 1f))
    }
}
