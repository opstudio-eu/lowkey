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
}
