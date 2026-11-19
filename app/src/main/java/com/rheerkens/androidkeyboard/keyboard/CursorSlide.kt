package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.abs

/**
 * A finger that slides from the pointing stick's nub (on the corner of [stickKey], G) uses the keyboard
 * as a trackpad for the cursor. Positions are in dp from the touch target's top-left corner (x to the
 * right, y down).
 *
 * - A slide begins after [START] dp sideways or a whole key row up or down, and that travel moves the
 *   cursor a character per [STEP] or a line. Until then the touch may still be a tap.
 * - Sideways, every [STEP] dp is [speed] characters. A fast slide counts up to [MAX_GAIN] times as
 *   many, so a quick flick crosses a line and a slow slide stays exact.
 * - Up or down, every key row is [speed] lines.
 * - Each movement counts along one axis only: the one the finger mostly moves along. A slanted
 *   sideways slide therefore doesn't change line.
 * - Coming back takes a full step, so the cursor doesn't flicker on a boundary.
 * - Resting in an [Edge] zone keeps the cursor moving ([dwell][EDGE_DWELL_MS], then a step every
 *   [Edge.everyMs]): at the sides of the screen, above the keyboard, and on the bottom row, where there is no room to slide on.
 */
class CursorSlide(
    private val zones: Zones,
    private val downX: Float,
    private val downY: Float,
    downAtMs: Long,
    /** How far the cursor moves per dp once the slide is under way, as a multiple of the slowest speed. */
    private val speed: Float = 1f,
) {

    /** Where the finger leaves the area it can slide in. */
    data class Zones(
        /** The height of a key row: the travel for one line. */
        val row: Float,
        /** Left of this x the finger is at the screen's left edge; right of [right] at its right edge. */
        val left: Float,
        val right: Float,
        /** Above this y the finger is over the app, above the keyboard. */
        val top: Float,
        /** Below this y the finger is low on the keyboard's bottom row, or under it. */
        val bottom: Float,
    )

    /** A zone that keeps moving the cursor while the finger rests in it. */
    enum class Edge(val chars: Int, val lines: Int, val everyMs: Long) {
        LEFT(-1, 0, 60), RIGHT(1, 0, 60), UP(0, -1, 220), DOWN(0, 1, 220),
    }

    /** Whether the touch has become a slide: no tap and no long-press follow. */
    var sliding = false
        private set

    /** The zone the finger is in now, if any. */
    var edge: Edge? = null
        private set

    /** Characters to the right (negative: left) for the last [moveTo]. */
    var chars = 0
        private set

    /** Lines down (negative: up) for the last [moveTo]. */
    var lines = 0
        private set

    private var x = downX
    private var y = downY
    private var atMs = downAtMs
    private var pendingChars = 0f
    private var pendingLines = 0f
    private var fingerSpeed = 0f

    /** The finger moved; afterwards [chars] and [lines] hold what to move the cursor by. */
    fun moveTo(nextX: Float, nextY: Float, nowMs: Long) {
        val dx = nextX - x
        val dy = nextY - y
        val elapsed = (nowMs - atMs).coerceAtLeast(1L)
        x = nextX
        y = nextY
        atMs = nowMs
        edge = edgeAt(nextX, nextY)
        chars = 0
        lines = 0
        if (!sliding) {
            val sideways = abs(nextX - downX) > START
            if (!sideways && abs(nextY - downY) <= zones.row) return
            sliding = true
            // The travel so far counts, along the axis that began the slide, at the slowest speed: a slide
            // starts with a small step whatever the speed.
            if (sideways) pendingChars = (nextX - downX) / STEP else pendingLines = (nextY - downY) / zones.row
        } else if (abs(dx) >= abs(dy)) {
            fingerSpeed = fingerSpeed * (1 - SMOOTHING) + abs(dx) / elapsed * SMOOTHING
            // At the side of the screen the zone moves the cursor; the finger has nowhere to go.
            if (edge != Edge.LEFT && edge != Edge.RIGHT) pendingChars += dx * gain(fingerSpeed) * speed / STEP
        } else if (edge != Edge.UP && edge != Edge.DOWN) {
            pendingLines += dy * speed / zones.row
        }
        chars = pendingChars.toInt()
        lines = pendingLines.toInt()
        pendingChars -= chars
        pendingLines -= lines
    }

    /** The finger rested in an [Edge] zone before it slid anywhere: that also makes it a slide. */
    fun begin() {
        sliding = true
    }

    private fun edgeAt(x: Float, y: Float): Edge? = when {
        y > zones.bottom -> Edge.DOWN
        y < zones.top -> Edge.UP
        x < zones.left -> Edge.LEFT
        x > zones.right -> Edge.RIGHT
        else -> null
    }

    companion object {
        /** Finger travel per character: about a quarter of a letter key. */
        const val STEP = 10f

        /** Sideways travel that starts a slide: far more than a quick tap drifts, so taps aren't lost. */
        const val START = 24f

        /** How close to the side of the screen the finger counts as at the edge. */
        const val SIDE_EDGE = 20f

        /** How long the finger rests in a zone before the cursor starts moving by itself. */
        const val EDGE_DWELL_MS = 300L

        /** Up to this speed (dp per millisecond) a slide moves one character per [STEP]. */
        const val SLOW = 0.5f

        /** From this speed a slide moves [MAX_GAIN] characters per [STEP]. */
        const val FAST = 1.5f
        const val MAX_GAIN = 3f

        /** The share of the newest movement in the measured speed. */
        private const val SMOOTHING = 0.4f

        /**
         * Where a slide from the stick's nub runs out of room, in dp from the nub: it sits on the
         * bottom edge of row [stickRow], on [rows] key rows [row] dp high under [strip] dp of
         * suggestion strip. Returns how much keyboard lies above the nub (higher up is the app) and
         * how far below it the bottom row's middle is (lower is the screen's edge).
         */
        fun nubBounds(row: Float, rows: Int, stickRow: Int, strip: Float): Pair<Float, Float> {
            val nub = row * (stickRow + 1)
            return (nub + strip) to (row * (rows - 0.5f) - nub)
        }

        /** How many characters a [STEP] counts for at [speed] (dp per millisecond). */
        fun gain(speed: Float): Float = 1f + (MAX_GAIN - 1f) * ((speed - SLOW) / (FAST - SLOW)).coerceIn(0f, 1f)
    }
}

/** Where [stickKey] is: its row in the layout and the key. */
data class Stick(val row: Int, val key: Key.Char)

/**
 * The key whose bottom-right corner carries the keyboard's pointing stick, like the red nub between
 * G, H and B on some laptop keyboards: G, near the middle of the letters, so a slide has room up,
 * down and to both sides. The key itself types as usual. A layout without G (ЙЦУКЕН, Greek) uses the key in G's place on QWERTY: the fifth letter
 * of the middle letter row. Null when [layout] has neither (number pads, symbols).
 */
fun stickKey(layout: KeyboardLayout): Stick? {
    val letterRows = layout.rows.withIndex()
        .map { (index, row) -> index to row.filterIsInstance<Key.Char>().filter { !it.numberRow && it.label.singleOrNull()?.isLetter() == true } }
        .filter { (_, letters) -> letters.isNotEmpty() }
    for ((index, letters) in letterRows) {
        letters.firstOrNull { it.label == "g" }?.let { return Stick(index, it) }
    }
    val (index, middle) = letterRows.getOrNull(1) ?: return null
    return middle.getOrNull(G_INDEX)?.let { Stick(index, it) }
}

/** G is the fifth key of QWERTY's middle row. */
private const val G_INDEX = 4
