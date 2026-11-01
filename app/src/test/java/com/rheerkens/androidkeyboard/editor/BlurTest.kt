package com.rheerkens.androidkeyboard.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun level(p: Int) = p and 0xFF

    @Test
    fun boxBlurSpreadsASharpEdgeAndKeepsFlatAreas() {
        // 16 × 4: black on the left half, white on the right, like the edge of a letter.
        val w = 16
        val h = 4
        val pixels = IntArray(w * h) { i -> if (i % w < 8) gray(0) else gray(255) }
        boxBlur(pixels, w, h, radius = 2, passes = 3)
        for (y in 0 until h) {
            assertEquals(0, level(pixels[y * w])) // Far from the edge stays as it was.
            assertEquals(255, level(pixels[y * w + w - 1]))
            val left = level(pixels[y * w + 7])
            val right = level(pixels[y * w + 8])
            assertTrue("edge softened: $left, $right", left in 60..195 && right in 60..195)
            assertEquals(0xFF, pixels[y * w + 7] ushr 24) // Opaque stays opaque.
        }
    }

    @Test
    fun boxBlurIsSymmetricAndPreservesTheAverage() {
        val w = 9
        val h = 9
        val pixels = IntArray(w * h) { gray(0) }.also { it[4 * w + 4] = gray(255) }
        boxBlur(pixels, w, h, radius = 1, passes = 1)
        // A single bright pixel becomes a 3 × 3 block of 255 / 9.
        for (y in 3..5) for (x in 3..5) assertEquals(28, level(pixels[y * w + x]))
        assertEquals(0, level(pixels[2 * w + 4]))
    }

    @Test
    fun onlyBlurMarksBlur() {
        assertTrue(Mark.Stroke(listOf(0f to 0f), BLUR, 4f, 255).blurs)
        assertTrue(Mark.Shape(ShapeKind.RECTANGLE, 0f, 0f, 1f, 1f, BLUR, 4f, filled = true).blurs)
        assertFalse(Mark.Shape(ShapeKind.RECTANGLE, 0f, 0f, 1f, 1f, 0xFF000000.toInt(), 4f, filled = true).blurs)
        assertFalse(Mark.Label(0f, 0f, "note", 0xFFFFFFFF.toInt(), 14f, 100f).blurs)
    }

    @Test
    fun onlyRectanglesAndOvalsFill() {
        assertTrue(ShapeKind.RECTANGLE.fillable)
        assertTrue(ShapeKind.OVAL.fillable)
        assertFalse(ShapeKind.ARROW.fillable)
        assertFalse(ShapeKind.LINE.fillable)
    }

    @Test
    fun blurCellsGrowWithWhatTheMarkCovers() {
        // A 1080 × 2400 screenshot: cells start at 1080 / 96 = 11.25 px.
        val thinStroke = Mark.Stroke(listOf(0f to 0f), BLUR, 44f, 255)
        assertEquals(11.25f, blurCell(thinStroke, 1080, 2400), 0.01f)
        // A filled rectangle over 220 px tall text: the short side spans at most four cells.
        val tall = Mark.Shape(ShapeKind.RECTANGLE, 30f, 60f, 1050f, 280f, BLUR, 8f, filled = true)
        val cell = blurCell(tall, 1080, 2400)
        assertTrue("cell $cell", cell * 4 >= 228f && cell / 2 * 4 < 228f)
        // Outlined, the same rectangle only blurs its line.
        assertEquals(11.25f, blurCell(tall.copy(filled = false), 1080, 2400), 0.01f)
        // Tiny images still get usable cells.
        assertEquals(4f, blurCell(thinStroke.copy(width = 2f), 10, 10), 0.01f)
    }
}
