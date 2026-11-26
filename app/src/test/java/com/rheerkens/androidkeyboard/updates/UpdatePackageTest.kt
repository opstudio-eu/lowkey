package com.rheerkens.androidkeyboard.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePackageTest {
    @Test fun officialPackageKeepsReleaseUpdates() {
        assertEquals(RELEASE_APPLICATION_ID, "com.opstudio.lowkey")
        assertTrue(acceptsReleaseUpdates("com.opstudio.lowkey"))
    }

    @Test fun retiredPackageAndTestInstallsCannotUseTheProductionFeed() {
        assertFalse(acceptsReleaseUpdates("com.rheerkens.androidkeyboard"))
        assertFalse(acceptsReleaseUpdates("com.opstudio.lowkey.uipreview"))
        assertFalse(acceptsReleaseUpdates("com.opstudio.lowkey.demo"))
        assertFalse(acceptsReleaseUpdates("example.keyboard"))
        assertFalse(acceptsReleaseUpdates("com.opstudio.lowkey2"))
    }
}
