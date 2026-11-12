package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rheerkens.androidkeyboard.settings.centerCrop
import kotlin.math.ceil

/** A decoded background image and how much of the background colour covers it, in percent. */
@Immutable
class KeyboardBackgroundImage(val bitmap: ImageBitmap, val dim: Int)

/**
 * The keyboard's background: [colors]' background, then [image] centre-cropped to the current
 * size with the background colour over it at the image's dimming. The keyboard and the editor's
 * preview both draw with this.
 */
fun Modifier.keyboardBackground(colors: KeyboardColors, image: KeyboardBackgroundImage?): Modifier = drawBehind {
    drawRect(colors.background)
    if (image == null || size.width <= 0f || size.height <= 0f) return@drawBehind
    val bmp = image.bitmap
    val (x, y, w, h) = centerCrop(bmp.width, bmp.height, size.width, size.height)
    drawImage(
        bmp,
        srcOffset = IntOffset(x, y),
        srcSize = IntSize(w, h),
        dstSize = IntSize(ceil(size.width).toInt(), ceil(size.height).toInt()),
    )
    drawRect(colors.background, alpha = clampDim(image.dim) / 100f)
}
