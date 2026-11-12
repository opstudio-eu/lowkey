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

    /** True when Android won't show the photos dialog again, as far as the app can tell. */
    fun deniedForever(activity: Activity?): Boolean = observeDenial(activity, afterRequest = false)

    /** [deniedForever], called with the result of a photos request. */
    fun deniedForeverAfterRequest(activity: Activity?): Boolean = observeDenial(activity, afterRequest = true)

    /** Updates what the app knows about earlier refusals (kept across restarts); see [photoDenial]. */
    private fun observeDenial(activity: Activity?, afterRequest: Boolean): Boolean {
        if (activity == null) return false
        val access = access()
        val rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION)
        val before = PhotoDenial(prefs.getBoolean(K_DENIED, false), prefs.getInt(K_UNANSWERED, 0))
        val now = photoDenial(before, access, rationale, afterRequest)
        if (now != before) prefs.edit { putBoolean(K_DENIED, now.deniedBefore).putInt(K_UNANSWERED, now.unanswered) }
        return now.forever(access, rationale)
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

        /**
         * What the app has seen of photo refusals. [deniedBefore]: Android reported a refusal.
         * [unanswered]: requests in a row that came back without access and without a rationale.
         */
        data class PhotoDenial(val deniedBefore: Boolean = false, val unanswered: Int = 0) {
            /**
             * Android shows a rationale only after one refusal, and stops showing it once it won't
             * ask again. No rationale is also the state before the first answer and after the dialog
             * was dismissed, so "won't ask again" needs a refusal seen earlier. A refusal from before
             * the app tracked them shows as two requests in a row without a dialog.
             */
            fun forever(access: PhotoAccess, rationale: Boolean): Boolean =
                access == PhotoAccess.NONE && !rationale && (deniedBefore || unanswered >= 2)
        }

        /**
         * The next [PhotoDenial] after looking at [access] and [rationale], when Settings or Home
         * shows or [afterRequest] a request came back. Any grant forgets it all: after a revoke or
         * Android's reset of an unused app's permissions, the dialog can show again.
         */
        fun photoDenial(before: PhotoDenial, access: PhotoAccess, rationale: Boolean, afterRequest: Boolean): PhotoDenial = when {
            access != PhotoAccess.NONE -> PhotoDenial()
            rationale -> PhotoDenial(deniedBefore = true)
            afterRequest -> before.copy(unanswered = before.unanswered + 1)
            else -> before
        }

        /** All photos wins; otherwise only picked photos is limited access. */
        fun photoAccess(all: Boolean, picked: Boolean): PhotoAccess = when {
            all -> PhotoAccess.ALL
            picked -> PhotoAccess.LIMITED
            else -> PhotoAccess.NONE
        }
        const val MAX_SHOTS = 5
        private const val K_DENIED = "photos_denied"
        private const val K_UNANSWERED = "photos_unanswered"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
