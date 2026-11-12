package com.rheerkens.androidkeyboard.companion.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x contrast ratio of two opaque sRGB colours (1 to 21). */
fun contrast(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** WCAG relative luminance of an opaque sRGB colour (0 for black to 1 for white). */
fun relativeLuminance(c: Color): Double {
    require(c.alpha == 1f) { "contrast needs opaque colours: $c" }
    fun channel(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}

/**
 * Whether white reads better on [background] than black. Decided by contrast, not by a 0.5
 * luminance cut-off: the two are equal at a luminance of about 0.18, so a mid grey takes black.
 */
fun prefersLightInk(background: Color): Boolean = contrast(Color.White, background) > contrast(Color.Black, background)

/** Black or white, whichever contrasts more with [background]. */
fun bestInk(background: Color): Color = if (prefersLightInk(background)) Color.White else Color.Black

/** WCAG AA: 4.5:1 for normal text, 3:1 for icons, borders and large text. */
const val AA_TEXT = 4.5
const val AA_NON_TEXT = 3.0
