package com.rheerkens.androidkeyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAccessTest {
    @Test
    fun allPhotosWinsOverPickedPhotos() {
        // "Allow all" on Android 14+ grants both permissions.
        assertEquals(PhotoAccess.ALL, RecentScreenshots.photoAccess(all = true, picked = true))
        assertEquals(PhotoAccess.ALL, RecentScreenshots.photoAccess(all = true, picked = false))
    }

    @Test
    fun onlyPickedPhotosIsLimited() {
        assertEquals(PhotoAccess.LIMITED, RecentScreenshots.photoAccess(all = false, picked = true))
    }

    @Test
    fun neitherIsNone() {
        assertEquals(PhotoAccess.NONE, RecentScreenshots.photoAccess(all = false, picked = false))
    }

    // Each step: what Android reports after a request or when the screen shows, as Settings and Home see it.
    private fun step(before: RecentScreenshots.Companion.PhotoDenial, rationale: Boolean, afterRequest: Boolean = true, access: PhotoAccess = PhotoAccess.NONE) =
        RecentScreenshots.photoDenial(before, access, rationale, afterRequest)

    private val fresh = RecentScreenshots.Companion.PhotoDenial()

    @Test
    fun beforeTheFirstRequestTheDialogCanShow() {
        val shown = step(fresh, rationale = false, afterRequest = false)
        assertEquals(false, shown.forever(PhotoAccess.NONE, rationale = false))
    }

    @Test
    fun dismissingTheFirstDialogStaysRetryable() {
        val dismissed = step(fresh, rationale = false)
        assertEquals(false, dismissed.forever(PhotoAccess.NONE, rationale = false))
        // Showing the screen again changes nothing.
        assertEquals(dismissed, step(dismissed, rationale = false, afterRequest = false))
    }

    @Test
    fun twoRefusalsMeanAndroidWontAskAgain() {
        val once = step(fresh, rationale = true)
        assertEquals(false, once.forever(PhotoAccess.NONE, rationale = true))
        val twice = step(once, rationale = false)
        assertEquals(true, twice.forever(PhotoAccess.NONE, rationale = false))
        // Remembered after a restart, when the screen shows again.
        assertEquals(true, step(twice, rationale = false, afterRequest = false).forever(PhotoAccess.NONE, rationale = false))
    }

    @Test
    fun aRefusalFromBeforeTrackingShowsAsTwoRequestsWithoutADialog() {
        val first = step(fresh, rationale = false)
        assertEquals(false, first.forever(PhotoAccess.NONE, rationale = false))
        assertEquals(true, step(first, rationale = false).forever(PhotoAccess.NONE, rationale = false))
    }

    @Test
    fun anyGrantForgetsEarlierRefusals() {
        val twice = step(step(fresh, rationale = true), rationale = false)
        assertEquals(fresh, step(twice, rationale = false, access = PhotoAccess.LIMITED))
        assertEquals(fresh, step(twice, rationale = false, access = PhotoAccess.ALL))
    }
}

