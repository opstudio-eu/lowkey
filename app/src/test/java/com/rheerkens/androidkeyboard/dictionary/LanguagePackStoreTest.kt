package com.rheerkens.androidkeyboard.dictionary

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class LanguagePackStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val gz = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(File("src/test/resources/aosp/sample.combined").readBytes()) }
    }.toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(gz).joinToString("") { "%02x".format(it) }
    private val swedish = TestLanguages.language("sv").copy(sourceBytes = gz.size.toLong(), sourceSha256 = sha)
    private val catalog = LanguageCatalog(listOf(TestLanguages.language("en_US"), swedish))

    /** Serves [body] as the source browser does: base64 text with line breaks. */
    private fun serving(body: ByteArray, requests: MutableList<String> = mutableListOf()) = WordListFetcher { url, out ->
        requests += url
        out.write(Base64.getMimeEncoder().encode(body))
    }

    @Test
    fun downloadVerifiesConvertsAndInstalls() = runTest {
        val dir = tmp.newFolder("dictionaries")
        val requests = mutableListOf<String>()
        val installed = mutableListOf<String>()
        val store = LanguagePackStore(
            dir, catalog, this, serving(gz, requests), StandardTestDispatcher(testScheduler),
            onInstalled = { installed += it.id },
        )
        assertFalse(store.isInstalled(swedish))
        store.download("sv")
        advanceUntilIdle()
        assertEquals(listOf(swedish.sourceUrl), requests)
        assertEquals(listOf("sv"), installed)
        assertTrue(store.isInstalled(swedish))
        assertEquals(LanguagePackState(installed = setOf("sv")), store.state.value)
        val lexicon = store.file(swedish).inputStream().use { Lexicon.parseGzip(it, swedish.keys) }
        assertEquals(150, lexicon.frequency("żółw"))
        // A new store (next app start) finds it again.
        assertTrue(LanguagePackStore(dir, catalog, backgroundScope).isInstalled(swedish))
        // Bundled languages are never downloaded.
        store.download("en_US")
        assertEquals(setOf("sv"), store.state.value.installed)
    }

    @Test
    fun aDownloadThatIsNotThePinnedFileIsRejected() = runTest {
        val dir = tmp.newFolder("dictionaries")
        val tampered = gz.copyOf().also { it[it.size - 9] = (it[it.size - 9] + 1).toByte() }
        val store = LanguagePackStore(dir, catalog, this, serving(tampered), StandardTestDispatcher(testScheduler))
        store.download("sv")
        advanceUntilIdle()
        assertFalse(store.isInstalled(swedish))
        assertTrue(store.state.value.errors.getValue("sv").startsWith("Checksum mismatch"))
        assertTrue(store.state.value.downloads.isEmpty())
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun networkErrorsAreReportedAndCanBeRetried() = runTest {
        val dir = tmp.newFolder("dictionaries")
        var fail = true
        val store = LanguagePackStore(
            dir, catalog, this,
            { url, out -> if (fail) throw java.net.UnknownHostException(url) else serving(gz).fetch(url, out) },
            StandardTestDispatcher(testScheduler),
        )
        store.download("sv")
        advanceUntilIdle()
        assertEquals("No connection", store.state.value.errors["sv"])
        fail = false
        store.download("sv")
        advanceUntilIdle()
        assertTrue(store.isInstalled(swedish))
        assertTrue(store.state.value.errors.isEmpty())
    }

    @Test
    fun oversizedResponsesStopEarly() = runTest {
        val store = LanguagePackStore(tmp.newFolder("d"), catalog, this, serving(ByteArray(gz.size + 200_000)), StandardTestDispatcher(testScheduler))
        store.download("sv")
        advanceUntilIdle()
        assertEquals("The word list is larger than expected", store.state.value.errors["sv"])
    }

    @Test
    fun deleteRemovesTheListAndAChangedPinInvalidatesIt() = runTest {
        val dir = tmp.newFolder("dictionaries")
        val store = LanguagePackStore(dir, catalog, backgroundScope, serving(gz), StandardTestDispatcher(testScheduler))
        store.install(swedish)
        val repinned = LanguageCatalog(listOf(swedish.copy(sourceSha256 = "0".repeat(64))))
        assertFalse(LanguagePackStore(dir, repinned, backgroundScope).isInstalled(repinned["sv"]!!))
        val reopened = LanguagePackStore(dir, catalog, backgroundScope)
        assertTrue(reopened.isInstalled(swedish))
        reopened.delete("sv")
        assertFalse(reopened.isInstalled(swedish))
        assertFalse(store.file(swedish).exists())
    }

    @Test
    fun leftoverTemporaryFilesAreRemovedOnStart() = runTest {
        val dir = tmp.newFolder("dictionaries")
        File(dir, "sv.dict.tmp").writeText("partial")
        LanguagePackStore(dir, catalog, backgroundScope)
        assertFalse(File(dir, "sv.dict.tmp").exists())
    }

    @Test
    fun installFailsWithoutTouchingAnInstalledList() = runTest {
        val dir = tmp.newFolder("dictionaries")
        val good = LanguagePackStore(dir, catalog, backgroundScope, serving(gz))
        good.install(swedish)
        val bad = LanguagePackStore(dir, catalog, backgroundScope, serving(byteArrayOf(1, 2, 3)))
        try {
            bad.install(swedish)
            throw AssertionError("expected a failure")
        } catch (_: IOException) {
        }
        assertTrue(LanguagePackStore(dir, catalog, backgroundScope).isInstalled(swedish))
    }
}
