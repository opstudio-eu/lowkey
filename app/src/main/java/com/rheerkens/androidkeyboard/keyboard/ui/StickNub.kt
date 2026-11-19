package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Which way a slide from the stick last moved the cursor: the nub leans that way and an arrow shows it. */
internal enum class Lean(val x: Int, val y: Int) {
    LEFT(-1, 0), RIGHT(1, 0), UP(0, -1), DOWN(0, 1);

    companion object {
        /** The main direction of a cursor step of [chars] right and [lines] down; callers never pass (0, 0). */
        fun of(chars: Int, lines: Int): Lean = when {
            abs(chars) >= abs(lines) -> if (chars < 0) LEFT else RIGHT
            else -> if (lines < 0) UP else DOWN
        }
    }
}

/**
 * Draws the pointing stick in the middle of its touch area: a small red rubber nub, as on a ThinkPad.
 * Pressed, it sinks and glows; sliding, it leans the way the cursor moves and a small red arrow
 * beside it points there.
 */
@Composable
internal fun Modifier.stickNub(pressed: Boolean, lean: Lean?): Modifier {
    val motion = tween<Float>(NUB_MS)
    val dx by animateFloatAsState((lean?.x ?: 0).toFloat(), motion, label = "nubX")
    val dy by animateFloatAsState((lean?.y ?: 0).toFloat(), motion, label = "nubY")
    val press by animateFloatAsState(if (pressed) 1f else 0f, motion, label = "nubPress")
    val ring = LocalKeyboardColors.current.background
    return drawWithContent {
        drawContent()
        drawNub(center, dx, dy, press, ring)
        if (lean != null) drawCue(center, lean)
    }
}

private fun DrawScope.drawNub(at: Offset, dx: Float, dy: Float, press: Float, ring: Color) {
    val r = NUB_RADIUS.toPx() * (1f - 0.1f * press)
    val nub = at + Offset(dx, dy) * LEAN.toPx()
    if (press > 0f) {
        val glow = r + 6.dp.toPx()
        drawCircle(Brush.radialGradient(0f to NUB_RED.copy(alpha = 0.7f * press), 1f to Color.Transparent, center = nub, radius = glow), glow, nub)
    }
    // A ring of keyboard colour sets the nub apart from the key corners under it.
    drawCircle(ring, r + 1.5.dp.toPx(), nub)
    drawCircle(Color.Black.copy(alpha = 0.4f * (1f - press)), r, nub + Offset(0f, 1.5.dp.toPx()))
    drawCircle(
        Brush.radialGradient(
            0f to NUB_LIGHT, 0.55f to NUB_RED, 1f to NUB_DARK,
            center = nub + Offset(-0.24f * r, -0.36f * r),
            radius = r * 1.5f,
        ),
        r, nub,
    )
    // Rubber stipple: a fine grid of darker dots inside the nub.
    val step = 2.dp.toPx()
    val dot = 0.45.dp.toPx()
    var y = -r
    while (y <= r) {
        var x = -r
        while (x <= r) {
            if (x * x + y * y < (r - dot) * (r - dot)) drawCircle(STIPPLE, dot, nub + Offset(x, y))
            x += step
        }
        y += step
    }
}

/** A small red arrow beside the nub at [at], on the side the cursor moves to. */
private fun DrawScope.drawCue(at: Offset, lean: Lean) {
    val reach = CUE_REACH.toPx()
    val half = 3.5.dp.toPx()
    val long = 4.5.dp.toPx()
    val tip = at + Offset(lean.x * reach, lean.y * reach)
    // Base points across the direction, behind the tip.
    val back = tip - Offset(lean.x * long, lean.y * long)
    val across = Offset(lean.y.toFloat(), lean.x.toFloat()) * half
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(back.x + across.x, back.y + across.y); lineTo(back.x - across.x, back.y - across.y); close() }, NUB_RED)
}

private const val NUB_MS = 220
private val NUB_RADIUS = 6.5.dp
private val LEAN = 3.5.dp
private val CUE_REACH = 14.5.dp
private val NUB_RED = Color(0xFFD7262C)
private val NUB_LIGHT = Color(0xFFFF7468)
private val NUB_DARK = Color(0xFF7E0C10)
private val STIPPLE = Color(0x40000000)
