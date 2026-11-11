package com.rheerkens.androidkeyboard.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateFeedTest {
    private val sha = "ab".repeat(32)
    private fun feed(
        versionCode: String = "42",
        versionName: String = "0.5.1",
        url: String = "https://github.com/opstudio-eu/lowkey/releases/download/v0.5.1/lowkey-0.5.1.apk",
        size: String = "87993840",
        sha256: String = sha,
    ) = "versionCode=$versionCode\nversionName=$versionName\nurl=$url\nsize=$size\nsha256=$sha256\n"

    @Test
    fun parsesTheFeedCiWrites() {
        val u = AvailableUpdate.parse(feed())
        assertEquals(42L, u.versionCode)
        assertEquals("0.5.1", u.versionName)
        assertEquals("lowkey-0.5.1.apk", u.fileName)
        assertEquals(87_993_840L, u.sizeBytes)
        assertEquals("https://github.com/opstudio-eu/lowkey/releases/tag/v0.5.1", u.releasePage)
    }

    @Test
    fun theSingleApkOfNewerReleasesIsSavedUnderItsVersion() {
        val u = AvailableUpdate.parse(
            feed(versionName = "0.5.4", url = "https://github.com/opstudio-eu/lowkey/releases/download/v0.5.4/lowkey.apk"),
        )
        assertEquals("https://github.com/opstudio-eu/lowkey/releases/download/v0.5.4/lowkey.apk", u.url)
        assertEquals("lowkey-0.5.4.apk", u.fileName)
    }

    @Test
    fun roundTripsThroughProperties() {
        val u = AvailableUpdate.parse(feed())
        assertEquals(u, AvailableUpdate.parse(u.toProperties()))
    }

    @Test
    fun rejectsDownloadsFromElsewhere() {
        listOf(
            "http://github.com/opstudio-eu/lowkey/releases/download/v0.5.1/lowkey-0.5.1.apk",
            "https://example.com/opstudio-eu/lowkey/releases/download/v0.5.1/lowkey-0.5.1.apk",
            "https://github.com/someone-else/lowkey/releases/download/v0.5.1/lowkey-0.5.1.apk",
            "https://github.com/opstudio-eu/lowkey/releases/download/v0.5.1/../x/lowkey.apk",
            "https://github.com/opstudio-eu/lowkey/releases/download/v0.5.0/lowkey-0.5.1.apk",
            "https://github.com/opstudio-eu/lowkey/releases/download/v0.5.1/lowkey-0.5.1.zip",
        ).forEach { url ->
            assertThrows(url, IllegalArgumentException::class.java) { AvailableUpdate.parse(feed(url = url)) }
        }
    }

    @Test
    fun rejectsMissingOrMalformedFields() {
        listOf(
            feed(versionCode = "x"),
            feed(versionCode = "0"),
            feed(size = "0"),
            feed(size = "999999999999"),
            feed(sha256 = "AB".repeat(32)),
            feed(sha256 = "ab"),
            "versionCode=42\n",
            "",
        ).forEach { text ->
            assertThrows(text, IllegalArgumentException::class.java) { AvailableUpdate.parse(text) }
        }
    }
}
