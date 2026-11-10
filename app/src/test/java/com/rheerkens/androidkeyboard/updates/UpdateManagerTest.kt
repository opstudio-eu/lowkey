package com.rheerkens.androidkeyboard.updates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateManagerTest {
    private fun update(code: Long, name: String = "0.5.$code") = AvailableUpdate(
        code, name, "${AvailableUpdate.DOWNLOAD_PREFIX}v$name/lowkey-$name.apk", 4, "ab".repeat(32),
    )

    private class MemoryPrefs : UpdatePrefs {
        override var lastCheckedAt: Long? = null
        override var available: AvailableUpdate? = null
        override var notifiedVersionCode: Long = 0
    }

    private class FakePlatform(var installed: Long = 41) : UpdatePlatform {
        var feed: () -> String? = { null }
        var now = 1_000L
        val notified = mutableListOf<Long>()
        val installs = mutableListOf<File>()
        var downloads = 0
        var downloadFails = false

        override fun installedVersionCode() = installed
        override fun now() = now
        override suspend fun fetchFeed(): String? = feed()
        override suspend fun download(update: AvailableUpdate, target: File, onBytes: (Long) -> Unit) {
            downloads++
            if (downloadFails) throw ChecksumMismatchException("bad")
            target.writeBytes(ByteArray(update.sizeBytes.toInt()))
            onBytes(update.sizeBytes)
        }
        override fun install(apk: File, update: AvailableUpdate) { installs += apk }
        override fun notifyAvailable(update: AvailableUpdate): Boolean { notified += update.versionCode; return true }
    }

    private fun TestScope.manager(platform: FakePlatform, prefs: UpdatePrefs = MemoryPrefs(), dir: File = tempDir()) =
        UpdateManager(platform, prefs, dir, this)

    private fun tempDir(): File = Files.createTempDirectory("updates").toFile()

    @Test
    fun aNewerReleaseIsAvailableAndNotifiedOncePerVersion() = runTest {
        val platform = FakePlatform().apply { feed = { update(42).toProperties() } }
        val prefs = MemoryPrefs()
        val m = manager(platform, prefs)
        m.check(userStarted = false)
        assertEquals(UpdateStatus.Available(update(42)), m.state.value.status)
        assertEquals(update(42), prefs.available)
        m.check(userStarted = false)
        assertEquals(listOf(42L), platform.notified)
    }

    @Test
    fun theSameOrAnOlderReleaseIsUpToDate() = runTest {
        val platform = FakePlatform(installed = 42).apply { feed = { update(42).toProperties() } }
        val m = manager(platform)
        m.check(userStarted = true)
        assertEquals(UpdateStatus.UpToDate, m.state.value.status)
        assertEquals(1_000L, m.state.value.lastCheckedAt)
        assertTrue(platform.notified.isEmpty())
    }

    @Test
    fun aReleaseWithoutFeedCountsAsUpToDate() = runTest {
        val m = manager(FakePlatform().apply { feed = { null } })
        m.check(userStarted = true)
        assertEquals(UpdateStatus.UpToDate, m.state.value.status)
    }

    @Test
    fun checksTheUserStartsDontNotify() = runTest {
        val platform = FakePlatform().apply { feed = { update(42).toProperties() } }
        val m = manager(platform)
        m.check(userStarted = true)
        m.check(userStarted = false, notify = false)
        assertTrue(platform.notified.isEmpty())
    }

    @Test
    fun backgroundFailuresStaySilentAndKeepTheLastCheckTime() = runTest {
        val platform = FakePlatform().apply { feed = { throw IOException("offline") } }
        val prefs = MemoryPrefs().apply { lastCheckedAt = 500 }
        val m = manager(platform, prefs)
        m.check(userStarted = false)
        assertEquals(UpdateStatus.Idle, m.state.value.status)
        assertEquals(500L, prefs.lastCheckedAt)
        m.check(userStarted = true)
        assertTrue(m.state.value.status is UpdateStatus.Failed)
    }

    @Test
    fun anInvalidFeedIsAFailureNotAnUpdate() = runTest {
        val m = manager(FakePlatform().apply { feed = { "versionCode=99\n" } })
        m.check(userStarted = true)
        val status = m.state.value.status as UpdateStatus.Failed
        assertNull(status.update)
    }

    @Test
    fun anInstalledUpdateIsForgottenOnTheNextStart() = runTest {
        val dir = tempDir().apply { File(this, "lowkey-0.5.42.apk").writeText("old") }
        val prefs = MemoryPrefs().apply { available = update(42) }
        val m = manager(FakePlatform(installed = 42), prefs, dir)
        assertEquals(UpdateStatus.Idle, m.state.value.status)
        assertNull(prefs.available)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun aPendingUpdateSurvivesARestart() = runTest {
        val prefs = MemoryPrefs().apply { available = update(42) }
        val m = manager(FakePlatform(installed = 41), prefs)
        assertEquals(UpdateStatus.Available(update(42)), m.state.value.status)
    }

    @Test
    fun updatingDownloadsOnceThenHandsTheApkToTheInstaller() = runTest {
        val platform = FakePlatform().apply { feed = { update(42).toProperties() } }
        val dir = tempDir()
        val m = manager(platform, dir = dir)
        m.check(userStarted = true)
        m.startUpdate()
        advanceUntilIdle()
        assertEquals(UpdateStatus.Installing(update(42)), m.state.value.status)
        assertEquals(listOf(File(dir, "lowkey-0.5.42.apk")), platform.installs)
        assertFalse(File(dir, "lowkey-0.5.42.apk.part").exists())

        // Dismissing Android's dialog returns to Available; updating again reuses the download.
        m.onInstallResult(InstallResult.Cancelled)
        assertEquals(UpdateStatus.Available(update(42)), m.state.value.status)
        m.startUpdate()
        advanceUntilIdle()
        assertEquals(1, platform.downloads)
        assertEquals(2, platform.installs.size)
    }

    @Test
    fun aDamagedDownloadFailsAndLeavesNothingBehind() = runTest {
        val platform = FakePlatform().apply { feed = { update(42).toProperties() }; downloadFails = true }
        val dir = tempDir()
        val m = manager(platform, dir = dir)
        m.check(userStarted = true)
        m.startUpdate()
        advanceUntilIdle()
        val status = m.state.value.status as UpdateStatus.Failed
        assertEquals(update(42), status.update)
        assertTrue(platform.installs.isEmpty())
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun aCheckIsDueAfterTheIntervalOrWhenTheClockWentBack() = runTest {
        val platform = FakePlatform()
        val prefs = MemoryPrefs()
        val m = manager(platform, prefs)
        assertTrue(m.checkDue(100))
        prefs.lastCheckedAt = 950
        assertFalse(m.checkDue(100))
        platform.now = 1_050
        assertTrue(m.checkDue(100))
        platform.now = 900
        assertTrue(m.checkDue(100))
    }
}
