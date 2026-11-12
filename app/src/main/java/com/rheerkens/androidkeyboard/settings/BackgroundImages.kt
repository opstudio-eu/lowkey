// android.media.ExifInterface: the platform class reads streams and parses reliably from API 24;
// minSdk is 28, so the AndroidX copy would only add a dependency.
@file:Suppress("ExifInterface")

package com.rheerkens.androidkeyboard.settings

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import com.rheerkens.androidkeyboard.keyboard.ui.ImageSample
import com.rheerkens.androidkeyboard.keyboard.ui.isBackgroundFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** A decoded background image and its contrast sample. */
class LoadedBackground(val name: String, val bitmap: ImageBitmap, val sample: ImageSample)

/** The result of importing a picked image: a new file name, or why it failed. */
sealed interface BackgroundImport {
    data class Imported(val name: String) : BackgroundImport
    data class Failed(val error: BackgroundImageError) : BackgroundImport
}

/**
 * Background images in app-private storage. Each import writes a new immutable file (`bg-<random>`),
 * first under a temporary name, so a saved theme only ever names a complete file. Files are deleted
 * only when nothing references them. Decoded images are cached (two entries: the saved image and an
 * editor draft), so drawing never reads the disk.
 */
class BackgroundImages(private val dir: File) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, LoadedBackground>(4, 0.75f, true)

    fun file(name: String): File = File(dir, name)

    /** Copies the image at [uri] into a new bounded, upright file without any of its metadata. */
    suspend fun import(resolver: ContentResolver, uri: Uri): BackgroundImport = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                importLocked(resolver, uri)
            } catch (e: OutOfMemoryError) {
                BackgroundImport.Failed(BackgroundImageError.TOO_LARGE)
            } catch (e: IOException) {
                BackgroundImport.Failed(BackgroundImageError.UNREADABLE)
            } catch (e: SecurityException) {
                BackgroundImport.Failed(BackgroundImageError.UNREADABLE)
            } catch (e: IllegalArgumentException) {
                BackgroundImport.Failed(BackgroundImageError.UNSUPPORTED)
            }
        }
    }

    private fun importLocked(resolver: ContentResolver, uri: Uri): BackgroundImport {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return BackgroundImport.Failed(BackgroundImageError.UNREADABLE)
        // Returns no bitmap by design; only the bounds are filled in.
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        checkSourceBounds(bounds.outWidth, bounds.outHeight)?.let { return BackgroundImport.Failed(it) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return BackgroundImport.Failed(BackgroundImageError.UNSUPPORTED)
        val upright = Upright.fromExif(exifOrientation(resolver, uri))
        val (uw, uh) = upright.size(decoded.width, decoded.height)
        val (tw, th) = storedSize(uw, uh)
        val matrix = Matrix().apply {
            postRotate(upright.rotation.toFloat())
            if (upright.mirror) postScale(-1f, 1f)
            // Scale relative to the decoded size; rotation by 90° swaps which side maps to which.
            postScale(tw.toFloat() / uw, th.toFloat() / uh)
        }
        val stored = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (stored !== decoded) decoded.recycle()
        try {
            dir.mkdirs()
            val png = stored.hasAlpha()
            val name = "bg-" + UUID.randomUUID().toString().replace("-", "") + if (png) ".png" else ".jpg"
            val temp = File(dir, ".tmp-$name")
            FileOutputStream(temp).use { out ->
                val ok = if (png) stored.compress(Bitmap.CompressFormat.PNG, 100, out)
                else stored.compress(Bitmap.CompressFormat.JPEG, 90, out)
                if (!ok) throw IOException("encode failed")
                out.fd.sync()
            }
            if (!temp.renameTo(File(dir, name))) {
                temp.delete()
                throw IOException("rename failed")
            }
            return BackgroundImport.Imported(name)
        } finally {
            stored.recycle()
        }
    }

    private fun exifOrientation(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        // No or unreadable EXIF: treat the image as upright.
        ExifInterface.ORIENTATION_NORMAL
    }

    /** The decoded image [name], from the cache or the disk; null when missing or unreadable. */
    suspend fun load(name: String): LoadedBackground? = withContext(Dispatchers.IO) {
        mutex.withLock {
            cache[name]?.let { return@withLock it }
            val loaded = decodeStored(name) ?: return@withLock null
            cache[name] = loaded
            while (cache.size > 2) cache.remove(cache.keys.first())
            loaded
        }
    }

    private fun decodeStored(name: String): LoadedBackground? {
        if (!isBackgroundFileName(name)) return null
        val f = file(name)
        if (!f.isFile) return null
        return try {
            // The file was written by import, but check its bounds anyway before decoding it fully.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(f.path, options) ?: return null
            val (sw, sh) = sampleSize(bitmap.width, bitmap.height)
            val small = bitmap.scale(sw, sh)
            val pixels = IntArray(sw * sh)
            small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
            if (small !== bitmap) small.recycle()
            LoadedBackground(name, bitmap.asImageBitmap(), ImageSample(pixels))
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "background image too large to load")
            null
        }
    }

    /**
     * Deletes image files not in [keep]: all of them at once, and unfinished temporary files after
     * ten minutes. [minAgeMs] keeps recent unreferenced files too (used at start, so a draft survives
     * process recreation).
     */
    suspend fun cleanup(keep: Set<String?>, minAgeMs: Long = 0) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            for (f in dir.listFiles().orEmpty()) {
                val age = now - f.lastModified()
                val delete = when {
                    f.name.startsWith(".tmp-") -> age > TEMP_AGE_MS
                    isBackgroundFileName(f.name) -> f.name !in keep && age >= minAgeMs
                    else -> false
                }
                if (delete) {
                    cache.remove(f.name)
                    f.delete()
                }
            }
        }
    }

    private companion object {
        const val TAG = "BackgroundImages"
        const val TEMP_AGE_MS = 10 * 60 * 1000L
    }
}
