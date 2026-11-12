package com.rheerkens.androidkeyboard.clipboard

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.core.content.edit

/** How much of the photo library the app can read. */
enum class PhotoAccess {
    /** Every photo, so new screenshots too. */
    ALL,
    /** Only the photos the user picked (Android 14+ "Allow limited access"): new screenshots stay hidden. */
    LIMITED,
    NONE,
}

/**
 * Recent screenshots from the media library (Android doesn't put screenshots on the clipboard).
 * Needs access to all photos, which the companion app requests when the setting is turned on.
 */
class RecentScreenshots(private val context: Context) {
    fun access(): PhotoAccess = photoAccess(
        all = granted(PERMISSION),
        picked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
    )

    fun hasPermission(): Boolean = access() == PhotoAccess.ALL

    /** Call before showing the photos dialog, so [deniedForever] can tell a first request from a refusal. */
    fun markRequested() = prefs.edit { putBoolean(K_REQUESTED, true) }

    /**
     * True when Android won't show the photos dialog again: asked before, still no access, and no
     * rationale to show (which is also the state before the first request, hence [markRequested]).
     * Any grant forgets the request, since a later revoke or Android's reset of an unused app's
     * permissions can show the dialog again.
     */
    fun deniedForever(activity: Activity?): Boolean {
        if (access() != PhotoAccess.NONE) {
            if (prefs.contains(K_REQUESTED)) prefs.edit { remove(K_REQUESTED) }
            return false
        }
        return activity != null && prefs.getBoolean(K_REQUESTED, false) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION)
    }

    private val prefs get() = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)

    /**
     * Checks the app op as well as the permission: a "limited access" grant from before this app
     * declared READ_MEDIA_VISUAL_USER_SELECTED keeps READ_MEDIA_IMAGES granted with its app op denied.
     */
    private fun granted(permission: String): Boolean =
        PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

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

        /**
         * What to request. From Android 14 it includes READ_MEDIA_VISUAL_USER_SELECTED, so a
         * "limited access" choice shows as [PhotoAccess.LIMITED] instead of a grant that only
         * reaches the picked photos and lapses when the app leaves the screen.
         */
        val REQUEST: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                arrayOf(PERMISSION, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            } else {
                arrayOf(PERMISSION)
            }

        /** All photos wins; otherwise only picked photos is limited access. */
        fun photoAccess(all: Boolean, picked: Boolean): PhotoAccess = when {
            all -> PhotoAccess.ALL
            picked -> PhotoAccess.LIMITED
            else -> PhotoAccess.NONE
        }
        const val MAX_SHOTS = 5
        private const val K_REQUESTED = "photos_requested"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
