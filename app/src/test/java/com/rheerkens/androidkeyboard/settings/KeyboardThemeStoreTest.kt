package com.rheerkens.androidkeyboard.settings

import com.rheerkens.androidkeyboard.keyboard.ui.CustomKeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardSeed
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.parseHexColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Fake preferences: records what was written and what the store showed while it was written. */
private class FakePersistence(var stored: StoredKeyboardTheme = StoredKeyboardTheme()) : ThemePersistence {
    var fail = false
    var onWrite: () -> Unit = {}
    override fun read() = stored
    override fun write(theme: StoredKeyboardTheme): Boolean {
        onWrite()
        if (fail) return false
        stored = theme
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardThemeStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val seed = KeyboardSeed(
        background = hex("0F2A1D"), keys = hex("1E4632"), functionKeys = hex("163826"),
        text = hex("F1FAF4"), accent = hex("F2B33D"),
    )
    private val a = name('a')
    private val b = name('b')
    private val c = name('c')

    private fun hex(s: String) = parseHexColor(s)!!
    private fun name(ch: Char) = "bg-" + ch.toString().repeat(32) + ".jpg"

    private lateinit var dir: File
    private lateinit var images: BackgroundImages
    private var now = 1_000_000_000_000L

    // The test scope itself, not backgroundScope: advanceUntilIdle does not run background work.
    private fun TestScope.store(persistence: FakePersistence, scope: CoroutineScope = this): KeyboardThemeStore {
        val io = StandardTestDispatcher(testScheduler)
        dir = tmp.newFolder("bg")
        images = BackgroundImages(dir, io) { now }
        return KeyboardThemeStore(persistence, images, scope, io)
    }

    private fun file(name: String, ageMs: Long = 0) = File(dir, name).apply {
        writeText("x")
        setLastModified(now - ageMs)
    }

    private fun savedWith(image: String?) = StoredKeyboardTheme(
        enabled = true, colors = "0F2A1D,1E4632,163826,F1FAF4,F2B33D", image = image, dim = 50,
    )

    private fun exists(name: String) = File(dir, name).exists()

    @Test
    fun saveCommitsBeforeTheThemeIsPublished() = runTest {
        val prefs = FakePersistence()
        val store = store(prefs)
        var seenDuringWrite: KeyboardTheme? = null
        prefs.onWrite = { seenDuringWrite = store.theme.value }
        assertTrue(store.save(CustomKeyboardTheme(seed)))
        assertEquals(KeyboardTheme(), seenDuringWrite)
        assertEquals(KeyboardTheme(true, CustomKeyboardTheme(seed)), store.theme.value)
    }

    @Test
    fun aFailedSaveKeepsTheThemeTheOldImageAndTheDraftImage() = runTest {
        val prefs = FakePersistence(savedWith(a))
        val store = store(prefs)
        file(a); file(b)
        images.registerImported(b)
        store.setDraftImage(b)
        val before = store.theme.value
        prefs.fail = true

        assertFalse(store.save(CustomKeyboardTheme(seed, image = b)))
        advanceUntilIdle()

        assertEquals(before, store.theme.value)
        assertEquals(savedWith(a), prefs.stored)
        assertTrue("last durable image deleted", exists(a))
        assertTrue("draft image deleted", exists(b))

        // A retry with working storage succeeds and only then releases the old image.
        prefs.fail = false
        assertTrue(store.save(CustomKeyboardTheme(seed, image = b)))
        advanceUntilIdle()
        assertFalse(exists(a))
        assertTrue(exists(b))
    }

    @Test
    fun aFailedSwitchLeavesTheThemeUnchanged() = runTest {
        val prefs = FakePersistence(savedWith(a))
        val store = store(prefs)
        val before = store.theme.value
        prefs.fail = true
        assertFalse(store.setEnabled(false, seed))
        assertEquals(before, store.theme.value)
    }

    @Test
    fun aCleanupRequestedBeforeASaveNeverDeletesTheNewlySavedImage() = runTest {
        val prefs = FakePersistence(savedWith(a))
        val store = store(prefs)
        file(a); file(b)
        images.registerImported(b)
        store.setDraftImage(b)
        // Queued with the old saved image; it runs only after the save below.
        store.releaseUnused()
        assertTrue(store.save(CustomKeyboardTheme(seed, image = b)))
        advanceUntilIdle()
        assertTrue("newly saved image deleted", exists(b))
        assertFalse(exists(a))
    }

    @Test
    fun anImportNotYetInTheDraftSurvivesCleanup() = runTest {
        val store = store(FakePersistence(savedWith(a)))
        file(a); file(b)
        images.registerImported(b) // the import finished; the editor has not taken it yet
        store.releaseUnused()
        advanceUntilIdle()
        assertTrue(exists(b))

        store.discardImport(b)
        advanceUntilIdle()
        assertFalse(exists(b))
        assertTrue(exists(a))
    }

    @Test
    fun anImportNobodyTakesIsDeletedOnceItIsStale() = runTest {
        val store = store(FakePersistence(savedWith(a)))
        file(a); file(b)
        images.registerImported(b) // e.g. the editor was closed while it ran
        now += 11 * 60 * 1000L
        store.releaseUnused()
        advanceUntilIdle()
        assertFalse(exists(b))
        assertTrue(exists(a))
    }

    @Test
    fun replacingTheDraftImageDeletesThePreviousDraftOnly() = runTest {
        val store = store(FakePersistence(savedWith(a)))
        file(a); file(b); file(c)
        images.registerImported(b)
        store.setDraftImage(b)
        images.registerImported(c)
        store.setDraftImage(c)
        advanceUntilIdle()
        assertTrue(exists(a))
        assertFalse(exists(b))
        assertTrue(exists(c))

        store.setDraftImage(null) // Cancel or Remove
        advanceUntilIdle()
        assertTrue(exists(a))
        assertFalse(exists(c))
    }

    @Test
    fun theSavedImageIsKeptWhileTheCustomThemeIsOff() = runTest {
        val store = store(FakePersistence(savedWith(a)))
        file(a, ageMs = 10L * 24 * 60 * 60 * 1000)
        assertTrue(store.setEnabled(false, seed))
        store.setDraftImage(null)
        store.releaseUnused()
        advanceUntilIdle()
        assertTrue(exists(a))
        assertEquals(a, store.theme.value.custom?.image)
    }

    @Test
    fun startupCleanupRemovesOnlyOldUnreferencedFiles() = runTest {
        val prefs = FakePersistence(savedWith(a))
        val io = StandardTestDispatcher(testScheduler)
        dir = tmp.newFolder("bg")
        val day = 24 * 60 * 60 * 1000L
        file(a, ageMs = 3 * day); file(b, ageMs = 2 * day); file(c, ageMs = 60_000)
        File(dir, ".tmp-x").apply { writeText("x"); setLastModified(now - 11 * 60 * 1000L) }
        images = BackgroundImages(dir, io) { now }
        KeyboardThemeStore(prefs, images, this, io)
        advanceUntilIdle()
        assertTrue(exists(a))
        assertFalse(exists(b))
        assertTrue("a recent draft survives process recreation", exists(c))
        assertFalse(File(dir, ".tmp-x").exists())
    }
}
