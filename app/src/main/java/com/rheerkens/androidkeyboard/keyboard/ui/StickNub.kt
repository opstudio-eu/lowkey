package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
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
 * Draws the pointing stick in the middle of its touch area: a small red rubber nub seated in a
 * recessed crater between the surrounding chamfered key corners, as on a ThinkPad.
 * Pressed, it sinks and glows inside the pit; sliding, it leans inside the fixed crater while a
 * small red arrow beside it points the way.
 */
@Composable
internal fun Modifier.stickNub(pressed: Boolean, lean: Lean?): Modifier {
    val motion = tween<Float>(NUB_MS)
    val dx by animateFloatAsState((lean?.x ?: 0).toFloat(), motion, label = "nubX")
    val dy by animateFloatAsState((lean?.y ?: 0).toFloat(), motion, label = "nubY")
    val press by animateFloatAsState(if (pressed) 1f else 0f, motion, label = "nubPress")
    val darkBg = LocalKeyboardColors.current.background.luminance() < DARK_LUMINANCE
    val pit = remember { Path() }
    return drawWithContent {
        drawContent()
        drawNub(center, dx, dy, press, darkBg, pit)
        if (lean != null) drawCue(center, lean)
    }
}

private fun DrawScope.drawNub(at: Offset, dx: Float, dy: Float, press: Float, darkBg: Boolean, pit: Path) {
    val well = CRATER_WELL_RADIUS.toPx()
    val r = NUB_RADIUS.toPx() * (1f - 0.1f * press)
    val nub = at + Offset(dx, dy) * LEAN.toPx()
    pit.reset()
    pit.addOval(Rect(center = at, radius = well))
    clipPath(pit) {
        // Inner wall shadow cast into the recessed pit from the upper-left key corners.
        drawCircle(
            Brush.radialGradient(
                0.42f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (darkBg) 0.48f else 0.19f),
                center = at + Offset(1.0.dp.toPx(), 1.4.dp.toPx()),
                radius = well + 0.7.dp.toPx(),
            ),
            radius = well,
            center = at,
        )
        // Soft ambient occlusion under the ball, tracking it as it leans inside the pit.
        val aoR = r * (1.46f - 0.14f * press)
        val aoAt = nub + Offset(0.35.dp.toPx(), 1.0.dp.toPx())
        val aoAlpha = if (darkBg) 0.58f else 0.40f
        drawCircle(
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = aoAlpha),
                0.6f to Color.Black.copy(alpha = aoAlpha * 0.75f),
                1f to Color.Transparent,
                center = aoAt,
                radius = aoR,
            ),
            radius = aoR,
            center = aoAt,
        )
        if (press > 0f) {
            val glow = r + 5.5.dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    0f to NUB_RED.copy(alpha = 0.5f * press),
                    1f to Color.Transparent,
                    center = nub,
                    radius = glow,
                ),
                radius = glow,
                center = nub,
            )
        }
    }
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

/**
 * Draws the sloped chamfer bevel on a keycap whose corner borders the pointing stick at [at]
 * (in the keycap's inner coordinates). The caller clips to the keycap's rounded shape.
 */
internal fun DrawScope.drawCraterChamfer(at: Offset, capColor: Color) {
    val outer = CRATER_OUTER_RADIUS.toPx()
    val dark = capColor.luminance() < DARK_LUMINANCE
    val stops = if (dark) {
        arrayOf(
            0f to Color.Black.copy(alpha = 0.58f),
            0.48f to Color.Black.copy(alpha = 0.22f),
            0.74f to Color.White.copy(alpha = 0.03f),
            1f to Color.White.copy(alpha = 0.20f),
        )
    } else {
        arrayOf(
            0f to Color.Black.copy(alpha = 0.31f),
            0.48f to Color.Black.copy(alpha = 0.12f),
            0.76f to Color.Black.copy(alpha = 0.02f),
            1f to Color.White.copy(alpha = 0.25f),
        )
    }
    drawCircle(
        Brush.linearGradient(
            colorStops = stops,
            start = at - Offset(outer, outer),
            end = at + Offset(outer, outer),
        ),
        radius = outer,
        center = at,
    )
    drawCircle(
        Color.Black.copy(alpha = if (dark) 0.38f else 0.12f),
        radius = outer,
        center = at,
        style = Stroke(width = 0.35.dp.toPx()),
    )
}

/**
 * Punches the recessed crater well out of a keycap's offscreen layer at [at], clearing both the
 * corner and its shadow so solid themes and background photos show through naturally.
 */
internal fun DrawScope.drawCraterCutout(at: Offset) {
    drawCircle(
        Color.Black,
        radius = CRATER_WELL_RADIUS.toPx(),
        center = at,
        blendMode = BlendMode.Clear,
    )
}

/**
 * Position of a pointing stick's crater center relative to [capRect]'s top-left when any of
 * [corners] reaches into the cap within [outerRadius], or null when none does.
 */
internal fun craterNotch(capRect: Rect, corners: Collection<Offset>, outerRadius: Float): Offset? {
    val corner = corners.firstOrNull { at ->
        val nearest = Offset(at.x.coerceIn(capRect.left, capRect.right), at.y.coerceIn(capRect.top, capRect.bottom))
        (at - nearest).getDistance() < outerRadius
    } ?: return null
    return corner - capRect.topLeft
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

internal val CRATER_WELL_RADIUS = 8.1.dp
internal val CRATER_BEVEL = 1.5.dp
internal val CRATER_OUTER_RADIUS = CRATER_WELL_RADIUS + CRATER_BEVEL

private const val DARK_LUMINANCE = 0.35f
private const val NUB_MS = 220
private val NUB_RADIUS = 6.5.dp
private val LEAN = 2.6.dp
private val CUE_REACH = 14.5.dp
private val NUB_RED = Color(0xFFD7262C)
private val NUB_LIGHT = Color(0xFFFF7468)
private val NUB_DARK = Color(0xFF7E0C10)
private val STIPPLE = Color(0x40000000)
