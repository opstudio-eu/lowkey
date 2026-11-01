package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.exp
import kotlin.math.ln

/**
 * Where each Backspace slider level begins, in dp of travel above the touch point, for a slider
 * with [top] levels (see [BackspaceScrub]) and [travel] dp of room.
 *
 * - The first [DEAD] dp delete nothing (zones shrink in proportion on a slider under 120 dp).
 * - The first word gets a [FIRST] dp band, so one word is easy to hit.
 * - Each next band is a little narrower than the one below it, on one smooth curve: band k is
 *   FIRST * c / (c + k), with c chosen so the words fill the slider. Long text speeds up toward
 *   the top without a sudden change of pace after the first words.
 * - The top [ALL] dp delete all text before the cursor.
 *
 * When every word fits at [FIRST] dp (short text), the bands are equal and fill the slider.
 */
class SlideScale(val top: Int, val travel: Float) {
    /** starts[level] for level 1..top; starts[0] is 0. */
    private val starts = FloatArray(top + 1)

    /** The dead zone, top zone and hysteresis, shrunk on a short slider (entering from far up the fan). */
    val dead: Float = minOf(DEAD, travel * 0.1f)
    val allZone: Float = minOf(ALL, travel * 0.3f)
    private val hysteresis = minOf(HYSTERESIS, travel * 0.03f)

    /** Where the top level (all text before the cursor) begins. */
    val allStart: Float = maxOf(travel - allZone, dead)

    init {
        val words = top - 1
        if (top >= 1) starts[top] = allStart
        if (words > 0) {
            val span = allStart - dead
            val bands = bandsFor(words, span)
            // Accumulate in double and never pass the top level, so the starts stay in order.
            var y = dead.toDouble()
            for (level in 1..words) {
                starts[level] = minOf(y.toFloat(), allStart)
                y += bands[level - 1]
            }
        }
    }

    /** Where [level] begins (dp above the touch point); 0 for level 0. */
    fun startOf(level: Int): Float = starts[level.coerceIn(0, top)]

    /** The level at [dp] above the touch point. */
    fun levelAt(dp: Float): Int {
        if (top == 0 || dp < dead) return 0
        // The last level that begins at or below dp.
        var lo = 0
        var hi = top
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= dp) lo = mid else hi = mid - 1
        }
        return lo
    }

    /**
     * The level at [dp], given the finger is now at [current]: it only changes once the finger is
     * [HYSTERESIS] dp past a boundary, so a finger resting on one doesn't flicker between words.
     */
    fun levelAt(dp: Float, current: Int): Int {
        val next = levelAt(dp)
        if (current !in 0..top || next == current) return next
        return when {
            next > current -> levelAt(dp - hysteresis).coerceAtLeast(current)
            else -> levelAt(dp + hysteresis).coerceAtMost(current)
        }
    }

    companion object {
        /** Travel above the touch point that deletes nothing yet. */
        const val DEAD = 10f
        /** The first word's band, about 3 mm on a phone; the ones after it narrow gradually. */
        const val FIRST = 18f
        /** On a short slider the first band takes at most this share of the span. */
        const val FIRST_SHARE = 0.25f
        /** The zone at the top that deletes all text before the cursor. */
        const val ALL = 36f
        const val HYSTERESIS = 4f

        /** The longest travel, about 4 cm: the minimized bar's slider, from a key at the keyboard's bottom. */
        const val MAX_TRAVEL = 256f
        /** How high the minimized bar's Backspace key reaches above the keyboard's bottom edge. */
        const val BOTTOM_KEY_LIFT = 48f

        /**
         * Travel (dp) for a key whose top is [keyLift] dp above the keyboard's bottom edge, with
         * [room] dp free above it on screen (null if unknown). A key higher up gets that much less, so
         * every slider ends at the same height on screen, where the thumb reaches.
         */
        fun travelFor(keyLift: Float, room: Float?): Float {
            val fromKey = MAX_TRAVEL - (keyLift - BOTTOM_KEY_LIFT).coerceAtLeast(0f)
            return minOf(fromKey, room ?: fromKey).coerceAtLeast(1f)
        }

        /** Band widths (dp) for [words] word levels sharing [span] dp. */
        internal fun bandsFor(words: Int, span: Float): FloatArray {
            if (words <= 0) return FloatArray(0)
            if (span <= 0f) return FloatArray(words)
            val first = minOf(FIRST, span * FIRST_SHARE).toDouble()
            // Short text: equal bands, at least [first] wide, filling the span.
            if (words * first <= span) return FloatArray(words) { span / words }
            // Band k is first * c / (c + k). Their sum grows with c, from first (c near 0) to
            // first * words (c very large), so a bisection on log c finds the one filling the span.
            fun sum(c: Double): Double {
                var total = 0.0
                for (k in 0 until words) total += c / (c + k)
                return first * total
            }
            var lo = ln(1e-6)
            var hi = ln(1e12)
            repeat(60) {
                val mid = (lo + hi) / 2
                if (sum(exp(mid)) < span) lo = mid else hi = mid
            }
            val c = exp((lo + hi) / 2)
            val fit = span / sum(c)
            return FloatArray(words) { (first * c / (c + it) * fit).toFloat() }
        }
    }
}
