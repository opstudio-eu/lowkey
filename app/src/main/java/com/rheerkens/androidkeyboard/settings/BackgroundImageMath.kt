package com.rheerkens.androidkeyboard.settings

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Limits for a keyboard background image. */
object BackgroundLimits {
    /** Longest side of the stored image, in pixels. */
    const val MAX_SIDE = 1440
    /** Pixel count of the stored image. */
    const val MAX_PIXELS = 1_500_000L
    /** Larger sources are refused before decoding. */
    const val MAX_SOURCE_PIXELS = 100_000_000L
    /** Longest side of the sample used for contrast. */
    const val SAMPLE_SIDE = 64
}

/** Why an image could not be used, with the message the editor shows. */
enum class BackgroundImageError(val message: String) {
    UNREADABLE("Lowkey couldn't open that file. Choose another image."),
    UNSUPPORTED("That file isn't an image Lowkey can read. Try a JPEG, PNG, WebP or HEIC photo."),
    TOO_LARGE("That image is too large. Choose one under 100 megapixels."),
}

/** Refuses a source by its decoded bounds, before any pixels are decoded. */
fun checkSourceBounds(width: Int, height: Int): BackgroundImageError? = when {
    width <= 0 || height <= 0 -> BackgroundImageError.UNSUPPORTED
    width.toLong() * height > BackgroundLimits.MAX_SOURCE_PIXELS -> BackgroundImageError.TOO_LARGE
    else -> null
}

/**
 * The power-of-two `inSampleSize` that decodes a [width] × [height] source no smaller than needed
 * for [BackgroundLimits.MAX_SIDE] and [BackgroundLimits.MAX_PIXELS], and never more than four times
 * that many pixels, so a large photo is never decoded at full size.
 */
fun sampleSizeFor(width: Int, height: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= BackgroundLimits.MAX_SIDE) sample *= 2
    while ((width.toLong() / sample) * (height.toLong() / sample) > BackgroundLimits.MAX_PIXELS * 4) sample *= 2
    return sample
}

/** The stored size for a decoded (and oriented) [width] × [height]: scaled down to the limits, never up. */
fun storedSize(width: Int, height: Int): Pair<Int, Int> {
    val bySide = BackgroundLimits.MAX_SIDE.toDouble() / max(width, height)
    val byPixels = sqrt(BackgroundLimits.MAX_PIXELS.toDouble() / (width.toLong() * height))
    val scale = min(1.0, min(bySide, byPixels))
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/** The size of the contrast sample for a [width] × [height] image. */
fun sampleSize(width: Int, height: Int): Pair<Int, Int> {
    val scale = min(1.0, BackgroundLimits.SAMPLE_SIDE.toDouble() / max(width, height))
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/**
 * How to turn a decoded image upright: rotate clockwise by [rotation] degrees, then mirror
 * horizontally if [mirror].
 */
data class Upright(val rotation: Int, val mirror: Boolean) {
    /** The size after turning a [width] × [height] image upright. */
    fun size(width: Int, height: Int): Pair<Int, Int> = if (rotation % 180 == 0) width to height else height to width

    companion object {
        /** From an EXIF orientation value (1 to 8); anything else is treated as upright. */
        fun fromExif(orientation: Int): Upright = when (orientation) {
            2 -> Upright(0, true) // flip horizontal
            3 -> Upright(180, false)
            4 -> Upright(180, true) // flip vertical
            5 -> Upright(90, true) // transpose
            6 -> Upright(90, false)
            7 -> Upright(270, true) // transverse
            8 -> Upright(270, false)
            else -> Upright(0, false)
        }
    }
}

/** The centred source rectangle (left, top, width, height) that fills [dstW] × [dstH] when scaled. */
fun centerCrop(srcW: Int, srcH: Int, dstW: Float, dstH: Float): IntArray {
    if (srcW <= 0 || srcH <= 0 || dstW <= 0f || dstH <= 0f) return intArrayOf(0, 0, max(srcW, 0), max(srcH, 0))
    val scale = max(dstW / srcW, dstH / srcH)
    val w = (dstW / scale).roundToInt().coerceIn(1, srcW)
    val h = (dstH / scale).roundToInt().coerceIn(1, srcH)
    return intArrayOf((srcW - w) / 2, (srcH - h) / 2, w, h)
}
