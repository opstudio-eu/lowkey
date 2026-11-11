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

private fun relativeLuminance(c: Color): Double {
    require(c.alpha == 1f) { "contrast needs opaque colours: $c" }
    fun channel(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}

/** WCAG AA: 4.5:1 for normal text, 3:1 for icons, borders and large text. */
const val AA_TEXT = 4.5
const val AA_NON_TEXT = 3.0
