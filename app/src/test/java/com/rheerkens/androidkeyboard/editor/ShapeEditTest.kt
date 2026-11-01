package com.rheerkens.androidkeyboard.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeEditTest {
    private val rect = Mark.Shape(ShapeKind.RECTANGLE, 100f, 100f, 300f, 200f, 0, 4f)
    private val line = Mark.Shape(ShapeKind.LINE, 0f, 0f, 100f, 0f, 0, 4f)

    @Test
    fun boxesHaveFourCornerHandlesAndLinesTwoEnds() {
        assertEquals(4, handlesOf(rect).size)
        assertEquals(2, handlesOf(line).size)
        assertEquals(2, handlesOf(line.copy(kind = ShapeKind.ARROW)).size)
        assertEquals(ShapeHandle.END_X_START_Y, handleAt(rect, 295f, 104f, 10f))
        assertEquals(ShapeHandle.START_X_END_Y, handleAt(rect, 100f, 200f, 10f))
        assertNull(handleAt(rect, 200f, 150f, 10f))
        // On a tiny shape the nearest handle wins near the corners, and its middle moves it.
        val tiny = rect.copy(x2 = 110f, y2 = 110f)
        assertEquals(ShapeHandle.END, handleAt(tiny, 109f, 109f, 20f))
        assertNull(handleAt(tiny, 105f, 104f, 20f))
        val short = line.copy(x2 = 10f)
        assertEquals(ShapeHandle.START, handleAt(short, 1f, 2f, 20f))
        assertNull(handleAt(short, 5f, 1f, 20f))
        // A line shrunk to a point can still be grown from any side.
        val point = line.copy(x2 = 0f)
        assertEquals(ShapeHandle.START, handleAt(point, 6f, 3f, 20f))
    }

    @Test
    fun draggingACornerKeepsTheOppositeOne() {
        val r = rect.withHandle(ShapeHandle.END_X_START_Y, 400f, 50f)
        assertEquals(Mark.Shape(ShapeKind.RECTANGLE, 100f, 50f, 400f, 200f, 0, 4f), r)
        assertEquals(400f to 50f, r.handlePoint(ShapeHandle.END_X_START_Y))
        val s = rect.withHandle(ShapeHandle.START, 120f, 130f)
        assertEquals(listOf(120f, 130f, 300f, 200f), listOf(s.x1, s.y1, s.x2, s.y2))
        // Color, width and fill stay.
        val filled = rect.copy(color = BLUR, filled = true).withHandle(ShapeHandle.END, 500f, 500f)
        assertTrue(filled.filled)
        assertEquals(BLUR, filled.color)
    }

    @Test
    fun lineEndsSnapToTheirOtherEnd() {
        val end = line.withHandle(ShapeHandle.END, 100f, 4f) // About 2° off horizontal.
        assertEquals(0f, end.y2, 0.01f)
        val start = line.withHandle(ShapeHandle.START, 3f, 100f) // From (100, 0): close to 45°.
        assertEquals(start.x2 - start.x1, start.y1 - start.y2, 0.01f)
        // Arrows don't snap.
        assertEquals(4f, line.copy(kind = ShapeKind.ARROW).withHandle(ShapeHandle.END, 100f, 4f).y2)
    }

    @Test
    fun movingKeepsTheCenterInTheVisibleArea() {
        val moved = rect.movedBy(50f, -20f, 0f, 0f, 1000f, 1000f)
        assertEquals(listOf(150f, 80f, 350f, 180f), listOf(moved.x1, moved.y1, moved.x2, moved.y2))
        val far = rect.movedBy(5000f, -5000f, 0f, 0f, 1000f, 1000f) // Center stops at (1000, 0).
        assertEquals((far.x1 + far.x2) / 2, 1000f)
        assertEquals((far.y1 + far.y2) / 2, 0f)
        assertEquals(200f, far.x2 - far.x1)
        // A crop narrower than the image: the center stays inside the crop.
        val cropped = rect.movedBy(-500f, 0f, 150f, 0f, 250f, 1000f)
        assertEquals(150f, (cropped.x1 + cropped.x2) / 2)
        // A shape already outside the crop can move back in, but not further out.
        val outside = rect.movedBy(0f, 0f, 400f, 0f, 600f, 1000f)
        assertEquals(rect, outside)
        assertEquals(rect, rect.movedBy(-50f, 0f, 400f, 0f, 600f, 1000f))
        assertEquals(150f, rect.movedBy(50f, 0f, 400f, 0f, 600f, 1000f).x1)
    }

    @Test
    fun aSelectedBoxIsGrabbedAnywhereInsideALineOnlyNearIt() {
        assertTrue(grabsBody(rect, 200f, 150f, 5f)) // Inside an outlined rectangle.
        assertTrue(grabsBody(rect, 305f, 150f, 5f))
        assertFalse(grabsBody(rect, 320f, 150f, 5f))
        assertTrue(grabsBody(line, 50f, 6f, 5f))
        assertFalse(grabsBody(line, 50f, 30f, 5f))
    }

    @Test
    fun tapsSelectTheTopmostShapeOnItsLine() {
        val stroke = Mark.Stroke(listOf(0f to 150f, 400f to 150f), 0, 10f, 255)
        val filled = rect.copy(filled = true)
        val marks = listOf(rect, stroke, filled, Mark.Label(0f, 0f, "note", 0, 20f, 100f))
        assertEquals(2, shapeAt(marks, 200f, 150f, 5f)) // Inside the filled one, over the stroke.
        assertEquals(0, shapeAt(listOf(rect, stroke), 300f, 120f, 5f))
        assertEquals(-1, shapeAt(listOf(stroke), 200f, 150f, 5f)) // Strokes aren't shapes.
        assertEquals(-1, shapeAt(marks, 600f, 600f, 5f))
        // Inside an outline counts only when no line is hit: a small shape inside a big later one wins.
        val big = Mark.Shape(ShapeKind.RECTANGLE, 0f, 0f, 1000f, 1000f, 0, 4f)
        val small = Mark.Shape(ShapeKind.OVAL, 400f, 400f, 600f, 600f, 0, 4f)
        assertEquals(0, shapeAt(listOf(small, big), 600f, 500f, 5f))
        assertEquals(1, shapeAt(listOf(small, big), 500f, 500f, 5f)) // Inside both: the topmost.
        assertEquals(1, shapeAt(listOf(small, big), 200f, 200f, 5f))
        assertEquals(-1, shapeAt(listOf(line), 50f, 30f, 5f)) // Lines have no inside.
    }

    @Test
    fun shapesAreReachableWhenSomeOfThemIsInTheArea() {
        val area = Area(400f, 400f, 600f, 600f)
        assertTrue(reachableIn(Mark.Shape(ShapeKind.LINE, 300f, 200f, 600f, 500f, 0, 4f), area))
        assertFalse(reachableIn(Mark.Shape(ShapeKind.LINE, 450f, 200f, 750f, 500f, 0, 4f), area))
        // A box around the whole area counts (its inside can be tapped); an oval only by its inside.
        assertTrue(reachableIn(Mark.Shape(ShapeKind.RECTANGLE, 0f, 0f, 1000f, 1000f, 0, 4f), area))
        assertTrue(reachableIn(Mark.Shape(ShapeKind.OVAL, 0f, 0f, 1000f, 1000f, 0, 4f), area))
        assertFalse(reachableIn(Mark.Shape(ShapeKind.OVAL, 0f, 0f, 420f, 420f, 0, 4f), area)) // Box corner only.
        assertTrue(reachableIn(Mark.Shape(ShapeKind.OVAL, 0f, 0f, 820f, 820f, 0, 4f), area))
    }

    @Test
    fun movesThatWouldLoseAShapeStopAtTheEdge() {
        val area = Area(400f, 400f, 600f, 600f)
        val cut = Mark.Shape(ShapeKind.LINE, 300f, 200f, 600f, 500f, 0, 4f)
        // It can go 100 to the right before it leaves the area; a faster drag stops it there.
        for (dx in listOf(150f, 200f, 5000f)) {
            val moved = cut.movedWithin(dx, 0f, area)
            assertEquals(400f, moved.x1, 0.1f)
            assertTrue(reachableIn(moved, area))
        }
        assertEquals(cut.copy(x1 = 350f, x2 = 650f), cut.movedWithin(50f, 0f, area))
        // A shape that is already out of sight may go anywhere it's allowed to move.
        val away = Mark.Shape(ShapeKind.LINE, 0f, 0f, 100f, 0f, 0, 4f)
        assertEquals(away.copy(x1 = 10f, x2 = 110f), away.movedWithin(10f, 0f, area))
    }

    @Test
    fun resizingKeepsTheShapeInTheArea() {
        // Snapping would carry the end out of the area: the end stays where the finger is instead.
        val line = Mark.Shape(ShapeKind.LINE, 150f, 0f, 950f, 1000f, 0, 4f)
        val area = Area(900f, 900f, 1000f, 1000f)
        val resized = line.resizedWithin(ShapeHandle.END, 950f, 950f, area)
        assertEquals(950f to 950f, resized.handlePoint(ShapeHandle.END))
        assertTrue(reachableIn(resized, area))
        // The handle can't be dragged out of the area.
        val rect = Mark.Shape(ShapeKind.RECTANGLE, 450f, 450f, 550f, 550f, 0, 4f)
        val grown = rect.resizedWithin(ShapeHandle.END, 900f, 300f, Area(400f, 400f, 600f, 600f))
        assertEquals(600f to 400f, grown.handlePoint(ShapeHandle.END))
        // Snapping still applies when it keeps the line in sight.
        val snapped = line.resizedWithin(ShapeHandle.END, 950f, 4f, Area(0f, 0f, 1000f, 1000f))
        assertEquals(0f, snapped.y2 - snapped.y1, 0.01f)
        // A snap that would put the end outside the area is dropped, even if the line stays in sight.
        val steep = Mark.Shape(ShapeKind.LINE, 150f, 0f, 550f, 600f, 0, 4f)
        val kept = steep.resizedWithin(ShapeHandle.END, 600f, 530f, Area(400f, 400f, 600f, 600f))
        assertEquals(600f to 530f, kept.handlePoint(ShapeHandle.END))
    }

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 0.01f)
        assertEquals(expected.second, actual.second, 0.01f)
    }

    @Test
    fun aTurnedBoxHasItsHandlesAndKnobTurnedWithIt() {
        // 100 × 100 around (200, 200), turned a quarter clockwise: the top-left corner goes top-right.
        val box = Mark.Shape(ShapeKind.RECTANGLE, 150f, 150f, 250f, 250f, 0, 4f, rotation = 90f)
        assertPoint(250f to 150f, box.handlePoint(ShapeHandle.START))
        assertPoint(150f to 250f, box.handlePoint(ShapeHandle.END))
        // The knob above the top edge ends up to the right of the box.
        assertPoint(200f + 50f + 2f + 30f to 200f, box.knobPoint(30f))
        assertPoint(200f - 82f to 200f, box.knobPoint(30f, KnobSide.BOTTOM))
        assertPoint(200f to 282f, box.knobPoint(30f, KnobSide.RIGHT))
        assertPoint(150f to 150f, box.toLocal(250f, 150f))
    }

    @Test
    fun rotationFollowsTheFingerAndSnaps() {
        val rect = Mark.Shape(ShapeKind.RECTANGLE, 100f, 100f, 300f, 200f, 0, 4f)
        // From straight above the center (200, 150) to straight right of it: a quarter turn.
        val turn = rect.rotationFollowing(200f, 50f, 300f, 150f)
        assertEquals(90f, turn, 0.01f)
        assertEquals(30f, rect.rotatedTo(30f).rotation)
        assertEquals(45f, rect.rotatedTo(42f).rotation)
        assertEquals(0f, rect.rotatedTo(-3f).rotation)
        assertEquals(-90f, rect.rotatedTo(270f).rotation)
        assertEquals(180f, rect.rotatedTo(-179f).rotation)
        // Lines and arrows turn by their ends instead.
        assertEquals(0f, line.rotatedTo(30f).rotation)
    }

    @Test
    fun resizingATurnedBoxKeepsTheOppositeCorner() {
        val box = Mark.Shape(ShapeKind.RECTANGLE, 100f, 100f, 300f, 200f, 0, 4f).rotatedTo(30f)
        val opposite = mapOf(
            ShapeHandle.START to ShapeHandle.END, ShapeHandle.END to ShapeHandle.START,
            ShapeHandle.END_X_START_Y to ShapeHandle.START_X_END_Y, ShapeHandle.START_X_END_Y to ShapeHandle.END_X_START_Y,
        )
        for (h in ShapeHandle.entries) {
            val fixed = box.handlePoint(opposite.getValue(h))
            val (hx, hy) = box.handlePoint(h)
            val target = hx + 37f to hy - 21f
            val resized = box.withHandle(h, target.first, target.second)
            assertPoint(target, resized.handlePoint(h))
            assertPoint(fixed, resized.handlePoint(opposite.getValue(h)))
            assertEquals(30f, resized.rotation)
        }
    }

    @Test
    fun aTurnedBoxIsHitAndReachedWhereItIsDrawn() {
        // A thin bar along x from 0 to 200 at y = 100, turned to stand upright about (100, 100).
        val bar = Mark.Shape(ShapeKind.RECTANGLE, 0f, 95f, 200f, 105f, 0, 2f, rotation = 90f)
        assertTrue(touches(bar, 100f, 1f, 3f))
        assertFalse(touches(bar, 5f, 100f, 3f))
        assertTrue(grabsBody(bar, 100f, 150f, 2f))
        assertFalse(grabsBody(bar, 150f, 100f, 2f))
        assertEquals(0, shapeAt(listOf(bar.copy(filled = true)), 101f, 30f, 1f))
        // Upright, it reaches up to y = 0; lying flat it wouldn't reach this area.
        val area = Area(90f, -50f, 110f, 10f)
        assertTrue(reachableIn(bar, area))
        assertFalse(reachableIn(bar.copy(rotation = 0f), area))
        val oval = bar.copy(kind = ShapeKind.OVAL)
        assertTrue(reachableIn(oval, area))
        assertFalse(reachableIn(oval, Area(150f, 150f, 200f, 200f)))
    }

    @Test
    fun turningStopsBeforeAShapeLeavesTheArea() {
        // An upright bar reaching into a strip at the top; lying flat it would miss the strip.
        val bar = Mark.Shape(ShapeKind.RECTANGLE, 95f, 0f, 105f, 200f, 0, 2f)
        val strip = Area(0f, 0f, 200f, 20f)
        val turned = bar.rotatedWithin(90f, strip)
        assertTrue(reachableIn(turned, strip))
        assertTrue(turned.rotation > 0f && turned.rotation < 90f)
        // Where the area allows it, it turns all the way.
        assertEquals(90f, bar.rotatedWithin(90f, Area(0f, 0f, 400f, 400f)).rotation)
    }
}
