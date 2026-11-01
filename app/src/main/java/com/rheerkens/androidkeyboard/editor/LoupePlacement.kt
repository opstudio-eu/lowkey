package com.rheerkens.androidkeyboard.editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/**
 * Where the drawing loupe sits: docked in whichever of the two allowed corners is farther from the
 * finger, so it covers neither the hand nor the shape being drawn around the finger. It stays in its
 * corner until the finger comes near, then moves to the other one.
 */
object LoupePlacement {
    /** Corner centres for a loupe of [radius], [margin] from the edges: top left, top right, bottom left, bottom right. */
    fun corners(width: Float, height: Float, radius: Float, margin: Float): List<Offset> {
        val near = radius + margin
        return listOf(
            Offset(near, near), Offset(width - near, near),
            Offset(near, height - near), Offset(width - near, height - near),
        )
    }

    /**
     * The corner index, one of [allowed], for a finger at [finger]: [current] while the finger
     * stays [clearance] away from it, else the allowed corner farthest from the finger.
     */
    fun corner(
        finger: Offset, current: Int?, allowed: List<Int>,
        width: Float, height: Float, radius: Float, margin: Float, clearance: Float,
    ): Int {
        val corners = corners(width, height, radius, margin)
        fun distance(i: Int) = hypot(corners[i].x - finger.x, corners[i].y - finger.y)
        if (current != null && current in allowed && distance(current) > clearance) return current
        return allowed.maxBy(::distance)
    }
}
