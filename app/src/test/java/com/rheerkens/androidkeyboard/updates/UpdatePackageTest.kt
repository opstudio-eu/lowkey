package com.rheerkens.androidkeyboard.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePackageTest {
    @Test fun officialPackageKeepsReleaseUpdates() {
        assertTrue(acceptsReleaseUpdates("com.rheerkens.androidkeyboard"))
    }

    @Test fun separateTestInstallsCannotUseTheProductionFeed() {
        assertFalse(acceptsReleaseUpdates("com.rheerkens.androidkeyboard.uipreview"))
        assertFalse(acceptsReleaseUpdates("com.rheerkens.androidkeyboard.demo"))
        assertFalse(acceptsReleaseUpdates("example.keyboard"))
        assertFalse(acceptsReleaseUpdates("com.rheerkens.androidkeyboard2"))
    }
}
