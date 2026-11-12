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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
 * first under a temporary name, so a saved theme only ever names a complete file. Decoded images are
 * cached (two entries: the saved image and an editor draft), so drawing never reads the disk.
 *
 * Files are deleted only when nothing holds them: the saved theme's image, the editor draft's image,
 * and each fresh import until the editor takes or discards it (or, if it never does because its
 * request was cancelled, for [PENDING_AGE_MS]). Holds are checked at each deletion under one lock,
 * so a cleanup requested earlier cannot delete a file that became saved or a draft since.
 */
class BackgroundImages(
    private val dir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, LoadedBackground>(4, 0.75f, true)

    private val holds = Any()
    private var saved: String? = null
    private var draft: String? = null
    private val pending = HashMap<String, Long>()

    /** The image the stored theme names; set only after the preferences were written. */
    fun holdSaved(name: String?) = synchronized(holds) { saved = name }

    /** The image the editor's draft shows; taking a fresh import ends its pending hold. */
    fun holdDraft(name: String?) = synchronized(holds) {
        draft = name
        if (name != null) pending.remove(name)
    }

    /** Ends the pending hold of an import nobody will use. */
    fun release(name: String) = synchronized(holds) { pending.remove(name) }

    /** Holds a just-imported file until the editor takes or discards it. */
    internal fun registerImported(name: String) = synchronized(holds) { pending[name] = now() }

    private fun isHeld(name: String, at: Long): Boolean = synchronized(holds) {
        name == saved || name == draft || pending[name]?.let { at - it < PENDING_AGE_MS } == true
    }

    fun file(name: String): File = File(dir, name)

    /** Copies the image at [uri] into a new bounded, upright file without any of its metadata. */
    suspend fun import(resolver: ContentResolver, uri: Uri): BackgroundImport = withContext(io) {
        mutex.withLock {
            try {
                importLocked(resolver, uri)
            } catch (e: OutOfMemoryError) {
                BackgroundImport.Failed(BackgroundImageError.TOO_LARGE)
            } catch (e: IOException) {
                BackgroundImport.Failed(BackgroundImageError.UNREADABLE)
            } catch (e: SecurityException) {
                BackgroundImport.Failed(BackgroundImageError.UNREADABLE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // Bitmap decoding and transforming report bad input as IllegalArgument or IllegalState.
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
            // Held before the lock is released, so no cleanup can run before the editor takes it.
            registerImported(name)
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
    suspend fun load(name: String): LoadedBackground? = withContext(io) {
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
        // A corrupt, truncated or unreadable file falls back to the background colour.
        return decodeOrNull {
            // The file was written by import, but check its bounds anyway before decoding it fully.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@decodeOrNull null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(f.path, options) ?: return@decodeOrNull null
            try {
                val (sw, sh) = sampleSize(bitmap.width, bitmap.height)
                val small = bitmap.scale(sw, sh)
                val pixels = IntArray(sw * sh)
                small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
                if (small !== bitmap) small.recycle()
                LoadedBackground(name, bitmap.asImageBitmap(), ImageSample(pixels))
            } catch (e: Throwable) {
                bitmap.recycle()
                throw e
            }
        }
    }

    /**
     * Deletes image files nothing holds: all of them at once, and unfinished temporary files after
     * ten minutes. [minAgeMs] keeps recent unheld files too (used at start, so a draft survives
     * process recreation).
     */
    suspend fun cleanup(minAgeMs: Long = 0) = withContext(io) {
        mutex.withLock {
            for (f in dir.listFiles().orEmpty()) {
                val at = now()
                val age = at - f.lastModified()
                when {
                    f.name.startsWith(".tmp-") -> if (age > TEMP_AGE_MS) f.delete()
                    isBackgroundFileName(f.name) -> synchronized(holds) {
                        // Checked and deleted under the hold lock: nothing can take the file in between.
                        if (age >= minAgeMs && !isHeld(f.name, at)) {
                            cache.remove(f.name)
                            f.delete()
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TEMP_AGE_MS = 10 * 60 * 1000L
        const val PENDING_AGE_MS = 10 * 60 * 1000L
    }
}

/**
 * Runs a decode, turning a failure on a bad file into null. Out of memory counts as one; a
 * coroutine cancellation is passed on, never swallowed.
 */
internal inline fun <T> decodeOrNull(block: () -> T?): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: RuntimeException) {
    Log.w("BackgroundImages", "background image unreadable: ${e.javaClass.simpleName}")
    null
} catch (e: OutOfMemoryError) {
    Log.w("BackgroundImages", "background image too large to load")
    null
}
