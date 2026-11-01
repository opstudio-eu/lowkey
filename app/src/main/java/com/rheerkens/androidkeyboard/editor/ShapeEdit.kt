package com.rheerkens.androidkeyboard.editor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

/**
 * A grab point on a placed shape, all in image pixels. A line or arrow has its two ends, [START]
 * at (x1, y1) and [END] at (x2, y2), the tip; a rectangle or oval also has the two other corners
 * of its box, [END_X_START_Y] at (x2, y1) and [START_X_END_Y] at (x1, y2).
 */
enum class ShapeHandle { START, END, END_X_START_Y, START_X_END_Y }

/** The shape's handles and where they are (on the turned box, for a turned shape). */
fun handlesOf(shape: Mark.Shape): List<Pair<ShapeHandle, Pair<Float, Float>>> =
    (if (shape.kind.fillable) ShapeHandle.entries else listOf(ShapeHandle.START, ShapeHandle.END))
        .map { it to shape.handlePoint(it) }

/** The middle of the shape's box, which it turns about. */
val Mark.Shape.centerX get() = (x1 + x2) / 2
val Mark.Shape.centerY get() = (y1 + y2) / 2

/** Where the point ([x], [y]) of the upright box lands once the shape is turned by its rotation. */
fun Mark.Shape.toWorld(x: Float, y: Float): Pair<Float, Float> = turn(x, y, centerX, centerY, rotation)

/** The point of the upright box that the image point ([x], [y]) is on the turned shape. */
fun Mark.Shape.toLocal(x: Float, y: Float): Pair<Float, Float> = turn(x, y, centerX, centerY, -rotation)

/** ([x], [y]) turned by [degrees] (clockwise on screen) about ([cx], [cy]). */
private fun turn(x: Float, y: Float, cx: Float, cy: Float, degrees: Float): Pair<Float, Float> {
    if (degrees == 0f) return x to y
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val dx = x - cx
    val dy = y - cy
    return cx + dx * c - dy * s to cy + dx * s + dy * c
}

/** Which edge of its box a shape's rotate knob stands out from. */
enum class KnobSide { TOP, BOTTOM, RIGHT, LEFT }

/**
 * Where the rotate knob of a rectangle or oval sits: [distance] beyond the middle of the [side]
 * edge of its box and the outline's half width, turned with the shape.
 */
fun Mark.Shape.knobPoint(distance: Float, side: KnobSide = KnobSide.TOP): Pair<Float, Float> {
    val down = abs(y2 - y1) / 2 + width / 2 + distance
    val across = abs(x2 - x1) / 2 + width / 2 + distance
    return when (side) {
        KnobSide.TOP -> toWorld(centerX, centerY - down)
        KnobSide.BOTTOM -> toWorld(centerX, centerY + down)
        KnobSide.RIGHT -> toWorld(centerX + across, centerY)
        KnobSide.LEFT -> toWorld(centerX - across, centerY)
    }
}

/**
 * The shape turned to [degrees], snapped to the nearest multiple of 45° within [snapDegrees],
 * and kept between -180° and 180°. Only rectangles and ovals turn.
 */
fun Mark.Shape.rotatedTo(degrees: Float, snapDegrees: Float = 4f): Mark.Shape {
    if (!kind.fillable) return this
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    val nearest = round(d / 45f) * 45f
    if (abs(d - nearest) <= snapDegrees) d = if (nearest == -180f) 180f else nearest
    return copy(rotation = if (d == -0f) 0f else d)
}

/**
 * [rotatedTo] [degrees], kept where it can be found in [area]: if that would leave nothing of a
 * shape that was in the area inside it, it turns only as far as it can on the way.
 */
fun Mark.Shape.rotatedWithin(degrees: Float, area: Area): Mark.Shape {
    val target = rotatedTo(degrees)
    if (target.rotation == rotation || !reachableIn(this, area) || reachableIn(target, area)) return target
    val from = rotation
    return furthest({ t -> rotatedTo(from + (degrees - from) * t, snapDegrees = 0f) }) { reachableIn(it, area) }
}

/**
 * The rotation that follows a finger turning about the shape's center from ([fromX], [fromY]) to
 * ([toX], [toY]), from the shape's current one, in degrees (not yet snapped).
 */
fun Mark.Shape.rotationFollowing(fromX: Float, fromY: Float, toX: Float, toY: Float): Float {
    val from = atan2(fromY - centerY, fromX - centerX)
    val to = atan2(toY - centerY, toX - centerX)
    return rotation + Math.toDegrees((to - from).toDouble()).toFloat()
}

/**
 * The handle nearest ([x], [y]) within [reach], or null. A finger closer to the shape's center
 * than to any handle grabs no handle, so even a tiny shape can still be moved from its middle; a
 * shape shrunk to a point can only be grown again.
 */
fun handleAt(shape: Mark.Shape, x: Float, y: Float, reach: Float): ShapeHandle? {
    val toCenter = hypot((shape.x1 + shape.x2) / 2 - x, (shape.y1 + shape.y2) / 2 - y)
    val point = hypot(shape.x2 - shape.x1, shape.y2 - shape.y1) < 1f
    return handlesOf(shape)
        .map { (h, p) -> h to hypot(p.first - x, p.second - y) }
        .filter { (_, d) -> d <= reach && (point || d < toCenter) }
        .minByOrNull { (_, d) -> d }
        ?.first
}

/** Where [handle] is on [shape], turned with it. */
fun Mark.Shape.handlePoint(handle: ShapeHandle): Pair<Float, Float> = when (handle) {
    ShapeHandle.START -> toWorld(x1, y1)
    ShapeHandle.END -> toWorld(x2, y2)
    ShapeHandle.END_X_START_Y -> toWorld(x2, y1)
    ShapeHandle.START_X_END_Y -> toWorld(x1, y2)
}

/** The corner across the box from [this] one. */
private val ShapeHandle.opposite get() = when (this) {
    ShapeHandle.START -> ShapeHandle.END
    ShapeHandle.END -> ShapeHandle.START
    ShapeHandle.END_X_START_Y -> ShapeHandle.START_X_END_Y
    ShapeHandle.START_X_END_Y -> ShapeHandle.END_X_START_Y
}

/**
 * The shape with [handle] dragged to ([x], [y]); the opposite corner or end stays put, and a box
 * may flip over it. With [snap], a line's end snaps to horizontal, vertical or 45° from its other
 * end, as when drawing it.
 */
fun Mark.Shape.withHandle(handle: ShapeHandle, x: Float, y: Float, snap: Boolean = true): Mark.Shape = when {
    rotation != 0f -> withTurnedHandle(handle, x, y)
    else -> withUprightHandle(handle, x, y, snap)
}

private fun Mark.Shape.withUprightHandle(handle: ShapeHandle, x: Float, y: Float, snap: Boolean): Mark.Shape = when (handle) {
    ShapeHandle.START -> {
        val (sx, sy) = if (snap && kind == ShapeKind.LINE) snapLineEnd(x2, y2, x, y) else x to y
        copy(x1 = sx, y1 = sy)
    }
    ShapeHandle.END -> {
        val (sx, sy) = if (snap && kind == ShapeKind.LINE) snapLineEnd(x1, y1, x, y) else x to y
        copy(x2 = sx, y2 = sy)
    }
    ShapeHandle.END_X_START_Y -> copy(x2 = x, y1 = y)
    ShapeHandle.START_X_END_Y -> copy(x1 = x, y2 = y)
}

/**
 * A turned box with [handle] dragged to ([x], [y]): the opposite corner stays where it is on the
 * image, so the box's middle moves to halfway between the two, and its sides keep their angle.
 */
private fun Mark.Shape.withTurnedHandle(handle: ShapeHandle, x: Float, y: Float): Mark.Shape {
    val (ox, oy) = handlePoint(handle.opposite)
    // The new diagonal from the fixed corner to the finger, in the box's own (upright) axes.
    val (dx, dy) = turn(x - ox, y - oy, 0f, 0f, -rotation)
    val cx = (ox + x) / 2
    val cy = (oy + y) / 2
    // Which way along each axis the dragged corner lies from the fixed one: x2 and y2 for END.
    val sx = if (handle == ShapeHandle.END || handle == ShapeHandle.END_X_START_Y) 1 else -1
    val sy = if (handle == ShapeHandle.END || handle == ShapeHandle.START_X_END_Y) 1 else -1
    return copy(
        x1 = cx - sx * dx / 2, x2 = cx + sx * dx / 2,
        y1 = cy - sy * dy / 2, y2 = cy + sy * dy / 2,
    )
}

/**
 * The shape moved by ([dx], [dy]), as far as its center stays inside the area from ([left], [top])
 * to ([right], [bottom]) (the visible crop), so a shape can hang over the edge but never be lost
 * off it. A shape whose center is already outside only moves toward the area.
 */
fun Mark.Shape.movedBy(dx: Float, dy: Float, left: Float, top: Float, right: Float, bottom: Float): Mark.Shape {
    val cx = (x1 + x2) / 2
    val cy = (y1 + y2) / 2
    val mx = (cx + dx).coerceIn(minOf(left, cx), maxOf(right, cx)) - cx
    val my = (cy + dy).coerceIn(minOf(top, cy), maxOf(bottom, cy)) - cy
    return copy(x1 = x1 + mx, y1 = y1 + my, x2 = x2 + mx, y2 = y2 + my)
}

/**
 * Whether a finger at ([x], [y]) grabs a selected [shape] to move it: anywhere in a rectangle's or
 * oval's box (outline included) widened by [reach], or within [reach] of a line or arrow.
 */
fun grabsBody(shape: Mark.Shape, x: Float, y: Float, reach: Float): Boolean {
    if (!shape.kind.fillable) return touches(shape, x, y, reach)
    if (shape.rotation != 0f) return shape.toLocal(x, y).let { (lx, ly) -> grabsBody(shape.copy(rotation = 0f), lx, ly, reach) }
    val margin = reach + shape.width / 2
    return x in minOf(shape.x1, shape.x2) - margin..maxOf(shape.x1, shape.x2) + margin &&
        y in minOf(shape.y1, shape.y2) - margin..maxOf(shape.y1, shape.y2) + margin
}

/**
 * The index of the shape a tap at ([x], [y]) selects, or -1: the topmost one whose line is within
 * [reach] (or with the tap inside it, if filled), else the topmost rectangle or oval with the tap
 * inside it. So a shape inside a larger outline can be picked, and a shape whose outline is out of
 * sight (around the whole crop) can still be found. Strokes and notes are skipped.
 */
fun shapeAt(marks: List<Mark>, x: Float, y: Float, reach: Float): Int {
    val onLine = marks.indexOfLast { it is Mark.Shape && touches(it, x, y, reach) }
    if (onLine >= 0) return onLine
    return marks.indexOfLast { it is Mark.Shape && touches(it.copy(filled = true), x, y, 0f) }
}

/** An axis-aligned area in image pixels, such as the crop. */
data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Whether some of [shape] lies in [area], so it can still be seen and tapped there (see
 * [shapeAt]): part of a line or arrow, or part of a rectangle's box or an oval's inside.
 */
fun reachableIn(shape: Mark.Shape, area: Area): Boolean = when {
    shape.rotation != 0f && shape.kind.fillable -> turnedReachableIn(shape, area)
    else -> uprightReachableIn(shape, area)
}

private fun uprightReachableIn(shape: Mark.Shape, area: Area): Boolean = when (shape.kind) {
    ShapeKind.LINE, ShapeKind.ARROW -> segmentMeets(shape.x1, shape.y1, shape.x2, shape.y2, area)
    ShapeKind.RECTANGLE -> minOf(shape.x1, shape.x2) <= area.right && maxOf(shape.x1, shape.x2) >= area.left &&
        minOf(shape.y1, shape.y2) <= area.bottom && maxOf(shape.y1, shape.y2) >= area.top
    ShapeKind.OVAL -> {
        // The area's point nearest the center, in the oval's own scale, is nearest in plain distance too.
        val cx = (shape.x1 + shape.x2) / 2
        val cy = (shape.y1 + shape.y2) / 2
        val rx = kotlin.math.abs(shape.x2 - shape.x1) / 2
        val ry = kotlin.math.abs(shape.y2 - shape.y1) / 2
        val px = cx.coerceIn(area.left, area.right)
        val py = cy.coerceIn(area.top, area.bottom)
        if (rx <= 0f || ry <= 0f) segmentMeets(shape.x1, shape.y1, shape.x2, shape.y2, area)
        else ((px - cx) / rx).let { it * it } + ((py - cy) / ry).let { it * it } <= 1f
    }
}

/**
 * [reachableIn] for a turned rectangle or oval, worked out in the shape's own frame, where it is
 * upright and the area is a turned rectangle.
 */
private fun turnedReachableIn(shape: Mark.Shape, area: Area): Boolean {
    val corners = listOf(area.left to area.top, area.right to area.top, area.right to area.bottom, area.left to area.bottom)
        .map { (x, y) -> shape.toLocal(x, y) }
    val left = minOf(shape.x1, shape.x2)
    val right = maxOf(shape.x1, shape.x2)
    val top = minOf(shape.y1, shape.y2)
    val bottom = maxOf(shape.y1, shape.y2)
    if (shape.kind == ShapeKind.RECTANGLE) {
        val box = listOf(left to top, right to top, right to bottom, left to bottom)
        return convexOverlap(box, corners)
    }
    val rx = (right - left) / 2
    val ry = (bottom - top) / 2
    val cx = (left + right) / 2
    val cy = (top + bottom) / 2
    if (rx <= 0f || ry <= 0f) {
        // A flat oval is the line across its box.
        val (ax, ay) = shape.toWorld(left, top)
        val (bx, by) = shape.toWorld(right, bottom)
        return segmentMeets(ax, ay, bx, by, area)
    }
    // Scaled so the oval is the unit circle: it meets the area if its middle is inside the
    // area, or an edge of the area comes within 1 of the middle.
    val unit = corners.map { (x, y) -> (x - cx) / rx to (y - cy) / ry }
    if (convexContains(unit, 0f, 0f)) return true
    return (unit + unit.first()).zipWithNext().any { (a, b) -> distanceToSegment(a, b) <= 1f }
}

/** Whether two convex polygons overlap (separating axis test). */
private fun convexOverlap(a: List<Pair<Float, Float>>, b: List<Pair<Float, Float>>): Boolean {
    for (poly in listOf(a, b)) {
        for (i in poly.indices) {
            val (px, py) = poly[i]
            val (qx, qy) = poly[(i + 1) % poly.size]
            val nx = qy - py
            val ny = px - qx
            val pa = a.map { it.first * nx + it.second * ny }
            val pb = b.map { it.first * nx + it.second * ny }
            if (pa.max() < pb.min() || pb.max() < pa.min()) return false
        }
    }
    return true
}

/** Whether ([x], [y]) is inside or on the convex polygon [poly]. */
private fun convexContains(poly: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
    var sign = 0
    for (i in poly.indices) {
        val (px, py) = poly[i]
        val (qx, qy) = poly[(i + 1) % poly.size]
        val cross = (qx - px) * (y - py) - (qy - py) * (x - px)
        val s = if (cross > 0f) 1 else if (cross < 0f) -1 else 0
        if (s != 0) {
            if (sign != 0 && s != sign) return false
            sign = s
        }
    }
    return true
}

/** The distance from the origin to the segment from [a] to [b]. */
private fun distanceToSegment(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = b.first - a.first
    val dy = b.second - a.second
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else ((-a.first * dx - a.second * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(a.first + t * dx, a.second + t * dy)
}

/** Whether the segment from (x1, y1) to (x2, y2) passes through [area] (Liang–Barsky clipping). */
private fun segmentMeets(x1: Float, y1: Float, x2: Float, y2: Float, area: Area): Boolean {
    var t0 = 0f
    var t1 = 1f
    val dx = x2 - x1
    val dy = y2 - y1
    for ((p, q) in listOf(-dx to x1 - area.left, dx to area.right - x1, -dy to y1 - area.top, dy to area.bottom - y1)) {
        if (p == 0f) {
            if (q < 0f) return false
        } else {
            val r = q / p
            if (p < 0f) t0 = maxOf(t0, r) else t1 = minOf(t1, r)
            if (t0 > t1) return false
        }
    }
    return true
}

/**
 * [shape] with [handle] dragged to ([x], [y]), kept where it can be found in [area]: the handle
 * stays in the area (or, if it starts outside, only comes closer), a line snaps only if its
 * snapped end stays there too, and if the shape would leave the area altogether, the handle
 * stops at the last point on the way where some of it is still inside.
 */
fun Mark.Shape.resizedWithin(handle: ShapeHandle, x: Float, y: Float, area: Area): Mark.Shape {
    val (hx, hy) = handlePoint(handle)
    val xs = minOf(area.left, hx)..maxOf(area.right, hx)
    val ys = minOf(area.top, hy)..maxOf(area.bottom, hy)
    val cx = x.coerceIn(xs)
    val cy = y.coerceIn(ys)
    val wasIn = reachableIn(this, area)
    fun ok(s: Mark.Shape) = !wasIn || reachableIn(s, area)
    val snapped = withHandle(handle, cx, cy)
    val (sx, sy) = snapped.handlePoint(handle)
    if (sx in xs && sy in ys && ok(snapped)) return snapped
    return furthest({ t -> withHandle(handle, hx + (cx - hx) * t, hy + (cy - hy) * t, snap = false) }, ::ok)
}

/**
 * [shape] moved by ([dx], [dy]) as in [movedBy]; if that would leave nothing of a shape that was
 * in [area] inside it, moved as far along the way as it can go, so it stops at the edge.
 */
fun Mark.Shape.movedWithin(dx: Float, dy: Float, area: Area): Mark.Shape {
    val step = { t: Float -> movedBy(dx * t, dy * t, area.left, area.top, area.right, area.bottom) }
    if (!reachableIn(this, area)) return step(1f)
    return furthest(step) { reachableIn(it, area) }
}

/** [at] (1), or [at] of the largest fraction found by bisection that is [ok]; [at] (0) must be. */
private fun furthest(at: (Float) -> Mark.Shape, ok: (Mark.Shape) -> Boolean): Mark.Shape {
    at(1f).let { if (ok(it)) return it }
    var lo = 0f
    var hi = 1f
    repeat(16) {
        val mid = (lo + hi) / 2
        if (ok(at(mid))) lo = mid else hi = mid
    }
    return at(lo)
}
