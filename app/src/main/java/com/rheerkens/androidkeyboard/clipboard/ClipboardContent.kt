package com.rheerkens.androidkeyboard.clipboard

import android.content.ClipData
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import java.io.IOException

/** The current system clip, kept in memory only while the keyboard is visible. */
sealed interface ClipboardContent {
    val sensitive: Boolean
    /** When the clip was copied (wall clock), or 0 if unknown. */
    val copiedAtMs: Long

    data class Text(val text: String, override val sensitive: Boolean, override val copiedAtMs: Long = 0) : ClipboardContent
    data class Image(
        val uri: Uri,
        val mimeType: String,
        val thumbnail: Bitmap?,
        override val sensitive: Boolean,
        override val copiedAtMs: Long = 0,
    ) : ClipboardContent
}

/** Call on an IO dispatcher: provider queries and image decoding must not block typing. */
class ClipboardReader(private val resolver: ContentResolver) {
    fun read(clip: ClipData?): ClipboardContent? {
        if (clip == null || clip.itemCount == 0) return null
        val sensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
        val item = clip.getItemAt(0)
        val copiedAt = clip.description.timestamp
        val uri = item.uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
        if (uri != null) {
            val mime = try { resolver.getType(uri) } catch (_: SecurityException) { null }
                ?: (0 until clip.description.mimeTypeCount).map(clip.description::getMimeType)
                    .firstOrNull { it.startsWith("image/") && !it.contains('*') }
            if (mime?.startsWith("image/") == true) {
                return ClipboardContent.Image(uri, mime, if (sensitive) null else thumbnail(uri), sensitive, copiedAt)
            }
        }
        return item.text?.toString()?.takeIf { it.isNotEmpty() }?.let { ClipboardContent.Text(it, sensitive, copiedAt) }
    }

    private fun thumbnail(uri: Uri): Bitmap? = loadThumbnail(resolver, uri)
}

/** A preview bounded to [sizePx] for a clip or screenshot, or null if the image can't be read. */
internal fun loadThumbnail(resolver: ContentResolver, uri: Uri, sizePx: Int = THUMBNAIL_PX): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } else {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inSampleSize = 1
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inJustDecodeBounds = false
            // Bound both dimensions, including panoramas, before allocating a bitmap.
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > sizePx * 2) {
                options.inSampleSize *= 2
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private const val THUMBNAIL_PX = 96
