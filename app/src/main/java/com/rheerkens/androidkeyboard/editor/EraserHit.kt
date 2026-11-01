package com.rheerkens.androidkeyboard.editor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Whether an eraser of radius [r] at ([x], [y]) touches a stroke or shape, all in image pixels:
 * its drawn line, widened by half the line width. Notes are hit-tested by their text bounds instead.
 */
fun touches(mark: Mark, x: Float, y: Float, r: Float): Boolean = when (mark) {
    // A turned shape is tested in its own frame, where it is upright.
    is Mark.Shape if mark.rotation != 0f -> mark.toLocal(x, y).let { (lx, ly) -> touches(mark.copy(rotation = 0f), lx, ly, r) }
    is Mark.Stroke -> nearPolyline(mark.points, x, y, r + mark.width / 2)
    is Mark.Shape -> nearPolyline(outline(mark), x, y, r + mark.width / 2) || (mark.filled && inside(mark, x, y))
    is Mark.Label -> false
}

/** Whether (x, y) is inside a fillable shape's area; a filled shape is erased from anywhere in it. */
private fun inside(s: Mark.Shape, x: Float, y: Float): Boolean {
    val left = minOf(s.x1, s.x2)
    val right = maxOf(s.x1, s.x2)
    val top = minOf(s.y1, s.y2)
    val bottom = maxOf(s.y1, s.y2)
    return when (s.kind) {
        ShapeKind.RECTANGLE -> x in left..right && y in top..bottom
        ShapeKind.OVAL -> {
            val rx = (right - left) / 2
            val ry = (bottom - top) / 2
            if (rx <= 0f || ry <= 0f) return false
            val dx = (x - (left + rx)) / rx
            val dy = (y - (top + ry)) / ry
            dx * dx + dy * dy <= 1f
        }
        ShapeKind.ARROW, ShapeKind.LINE -> false
    }
}

/** The shape's drawn line as connected points; an arrow's head is short enough to leave out. */
private fun outline(s: Mark.Shape): List<Pair<Float, Float>> = when (s.kind) {
    ShapeKind.LINE, ShapeKind.ARROW -> listOf(s.x1 to s.y1, s.x2 to s.y2)
    ShapeKind.RECTANGLE -> listOf(s.x1 to s.y1, s.x2 to s.y1, s.x2 to s.y2, s.x1 to s.y2, s.x1 to s.y1)
    ShapeKind.OVAL -> {
        val cx = (s.x1 + s.x2) / 2
        val cy = (s.y1 + s.y2) / 2
        val rx = (s.x2 - s.x1) / 2
        val ry = (s.y2 - s.y1) / 2
        (0..48).map { i -> (2 * PI * i / 48).let { a -> (cx + rx * cos(a)).toFloat() to (cy + ry * sin(a)).toFloat() } }
    }
}

private fun nearPolyline(points: List<Pair<Float, Float>>, x: Float, y: Float, reach: Float): Boolean {
    if (points.size == 1) return hypot(points[0].first - x, points[0].second - y) <= reach
    return points.zipWithNext().any { (a, b) -> segmentDistance(a, b, x, y) <= reach }
}

private fun segmentDistance(a: Pair<Float, Float>, b: Pair<Float, Float>, x: Float, y: Float): Float {
    val dx = b.first - a.first
    val dy = b.second - a.second
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((x - a.first) * dx + (y - a.second) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(a.first + t * dx - x, a.second + t * dy - y)
}
