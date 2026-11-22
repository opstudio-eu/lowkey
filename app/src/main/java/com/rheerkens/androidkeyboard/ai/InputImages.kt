package com.rheerkens.androidkeyboard.ai

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/** A picture added to the prompt: [image] as it will be sent, [thumbnail] for the prompt box. */
class PromptImage(val id: Long, val image: InputImage, val thumbnail: Bitmap?)

/**
 * Pictures for the prompt, read from the clipboard history, a screenshot or the photo picker and
 * made small enough to upload quickly: at most [MAX_SIDE] px on the long side, which is more than
 * the image models use. EXIF rotation is applied, so a photo isn't sent sideways.
 */
object InputImages {
    const val MAX_SIDE = 1536
    private const val THUMB_SIDE = 160
    private const val JPEG_QUALITY = 90

    /** [width] × [height] scaled down to fit [max] on its long side; never scaled up. */
    fun fit(width: Int, height: Int, max: Int = MAX_SIDE): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= max || long <= 0) return width.coerceAtLeast(1) to height.coerceAtLeast(1)
        val scale = max.toDouble() / long
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /** [uri] as a [PromptImage], or null when it isn't a picture Android can read. Call on an IO dispatcher. */
    fun prepare(resolver: ContentResolver, uri: Uri, id: Long): PromptImage? {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val (w, h) = fit(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
                // Software, so it can be compressed and scaled below.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            // No read access (SecurityException), or a file that isn't a picture after all.
            null
        } ?: return null
        val png = bitmap.hasAlpha()
        val bytes = ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) return null
            out.toByteArray()
        }
        val (tw, th) = fit(bitmap.width, bitmap.height, THUMB_SIDE)
        val thumbnail = Bitmap.createScaledBitmap(bitmap, tw, th, true)
        if (thumbnail !== bitmap) bitmap.recycle()
        return PromptImage(id, InputImage(bytes, if (png) "image/png" else "image/jpeg"), thumbnail)
    }
}
