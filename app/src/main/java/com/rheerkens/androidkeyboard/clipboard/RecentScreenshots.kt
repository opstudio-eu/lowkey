package com.rheerkens.androidkeyboard.clipboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * Recent screenshots from the media library (Android doesn't put screenshots on the clipboard).
 * Needs the photos permission, which the companion app requests when the setting is turned on.
 */
class RecentScreenshots(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Screenshots from the last [maxAgeMs], newest first. Call on an IO dispatcher. */
    fun query(now: Long, limit: Int = MAX_SHOTS, maxAgeMs: Long = MAX_AGE_MS): List<ClipEntry.Screenshot> {
        if (!hasPermission()) return emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
        )
        // Pixel phones save to Pictures/Screenshots, Samsung to DCIM/Screenshots: same folder name.
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val args = arrayOf("Screenshots", ((now - maxAgeMs) / 1000).toString())
        val shots = ArrayList<ClipEntry.Screenshot>()
        try {
            context.contentResolver.query(
                collection, projection, selection, args, "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (shots.size < limit && c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val mime = c.getString(mimeCol)?.takeIf { it.startsWith("image/") } ?: "image/png"
                    shots += ClipEntry.Screenshot(
                        id, uri, mime, loadThumbnail(context.contentResolver, uri), c.getLong(dateCol) * 1000,
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        return shots
    }

    companion object {
        val PERMISSION: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
        const val MAX_SHOTS = 5
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
