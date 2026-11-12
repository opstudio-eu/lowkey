package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.companion.theme.relativeLuminance
import kotlin.math.roundToInt

/** Dimming of the background image: how much of the background colour covers it, in percent. */
const val MIN_DIM = 0
const val MAX_DIM = 90
const val DEFAULT_DIM = 50

/** Clamps a stored or typed dimming to [MIN_DIM]..[MAX_DIM]. */
fun clampDim(dim: Int): Int = dim.coerceIn(MIN_DIM, MAX_DIM)

/**
 * A small sample of a background image's pixels (ARGB), taken over the whole image so it covers any
 * crop the keyboard shows. Used to judge contrast and navigation bar icons without assuming the
 * image is one colour.
 */
class ImageSample(private val pixels: IntArray) {
    init {
        require(pixels.isNotEmpty()) { "empty sample" }
    }

    /**
     * The luminance range after the keyboard draws it: the image over [background], then
     * [background] again at [dim] percent on top.
     */
    fun range(background: Color, dim: Int): LuminanceRange {
        var lo = 1.0
        var hi = 0.0
        val cover = clampDim(dim) / 100f
        val br = background.red8()
        val bgG = background.green8()
        val bb = background.blue8()
        for (p in pixels) {
            val alpha = (p ushr 24) / 255f
            // Share of the background in the final pixel: under transparent parts, plus the cover.
            val t = cover + (1 - cover) * (1 - alpha)
            fun ch(px: Int, bgc: Int) = (px + (bgc - px) * t).roundToInt().coerceIn(0, 255)
            val c = Color(ch((p shr 16) and 0xFF, br), ch((p shr 8) and 0xFF, bgG), ch(p and 0xFF, bb))
            val l = relativeLuminance(c)
            if (l < lo) lo = l
            if (l > hi) hi = l
        }
        return LuminanceRange(lo, hi)
    }

    override fun equals(other: Any?) = other is ImageSample && other.pixels.contentEquals(pixels)
    override fun hashCode() = pixels.contentHashCode()
}

/**
 * Whether the navigation bar's icons should be light. Before Android 15 ([edgeToEdge] false) the
 * bar is painted in [background], so its contrast decides, as before. From Android 15 the keyboard
 * shows through; with an image, the icons take the colour with the better worst case over its range.
 */
fun navigationBarLightIcons(background: Color, image: LuminanceRange?, edgeToEdge: Boolean): Boolean {
    if (!edgeToEdge || image == null) {
        return com.rheerkens.androidkeyboard.companion.theme.prefersLightInk(background)
    }
    return worstContrast(Color.White, image) > worstContrast(Color.Black, image)
}

/** How the input method window styles the navigation bar under the keyboard. */
data class NavigationBarStyle(
    val lightIcons: Boolean,
    /** Painted behind the bar before Android 15; transparent while the keyboard floats. */
    val color: Color,
)

/**
 * The navigation bar style for a keyboard drawn in [background], with an optional background image
 * judged by [image]. A floating keyboard leaves the bar to the app behind it: the bar stays
 * transparent and no image is under it, so only the keyboard colour guides the icons, as the
 * standard theme does with its light or dark background.
 */
fun keyboardNavigationBarStyle(
    background: Color,
    image: LuminanceRange?,
    floating: Boolean,
    edgeToEdge: Boolean,
): NavigationBarStyle =
    if (floating) {
        NavigationBarStyle(navigationBarLightIcons(background, null, edgeToEdge), Color.Transparent)
    } else {
        NavigationBarStyle(navigationBarLightIcons(background, image, edgeToEdge), background)
    }
