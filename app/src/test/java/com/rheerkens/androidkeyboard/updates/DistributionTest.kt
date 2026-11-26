package com.rheerkens.androidkeyboard.updates

import com.rheerkens.androidkeyboard.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionTest {
    @Test fun applicationIdIsComOpstudioLowkey() {
        assertTrue(
            BuildConfig.APPLICATION_ID == RELEASE_APPLICATION_ID ||
                BuildConfig.APPLICATION_ID == "$RELEASE_APPLICATION_ID.uipreview",
        )
        assertFalse(BuildConfig.APPLICATION_ID.startsWith("com.rheerkens."))
    }

    @Test fun selfUpdateFollowsTheFlavorAndTheOfficialId() {
        assertEquals(
            BuildConfig.SELF_UPDATE && BuildConfig.APPLICATION_ID == RELEASE_APPLICATION_ID,
            selfUpdateEnabled(),
        )
    }

    @Test fun updaterPermissionsLiveOnlyInTheGithubManifest() {
        val main = File("src/main/AndroidManifest.xml").readText()
        val github = File("src/github/AndroidManifest.xml").readText()
        val playDir = File("src/play")
        val playManifests = if (playDir.isDirectory) {
            playDir.walk().filter { it.name == "AndroidManifest.xml" }.joinToString("\n") { it.readText() }
        } else {
            ""
        }
        for (permission in UPDATER_PERMISSIONS) {
            val name = """android:name="$permission""""
            assertFalse(permission, name in main)
            assertFalse(permission, name in playManifests)
            assertTrue(permission, name in github)
        }
        for (component in UPDATER_COMPONENTS) {
            val name = """android:name=".updates.$component""""
            assertFalse(component, name in main)
            assertFalse(component, name in playManifests)
            assertTrue(component, name in github)
        }
        assertTrue(
            """<uses-feature android:name="android.hardware.microphone" android:required="false" />""" in main,
        )
    }

    private companion object {
        val UPDATER_PERMISSIONS = listOf(
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACCESS_NETWORK_STATE",
        )
        val UPDATER_COMPONENTS = listOf(
            "UpdateCheckJob",
            "UpdateDownloadService",
            "UpdateInstallReceiver",
            "UpdatedReceiver",
        )
    }
}
