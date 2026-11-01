package com.rheerkens.androidkeyboard.models

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class DiskModelStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class MemorySetting(var id: String? = null) : ActiveModelSetting {
        override fun load() = id
        override fun save(id: String?) { this.id = id }
    }

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val alpha = "alpha weights".toByteArray()
    private val beta = "beta config".toByteArray()
    private val other = "other model".toByteArray()

    private fun model(id: String, files: Map<String, ByteArray>) = ModelInfo(
        id = id, name = id, family = "Test", language = "English", description = "",
        engine = EngineSpec.SherpaWhisper(prefix = "tiny.en"), license = "MIT", source = "test",
        artifacts = files.map { (name, bytes) ->
            ModelArtifact(name, "https://example.invalid/$id/$name", bytes.size.toLong(), sha(bytes))
        },
    )

    private val small = model("small", mapOf("a.ort" to alpha, "b.json" to beta))
    private val medium = model("medium", mapOf("m.ort" to other))

    /** Serves catalog bytes; [gate] can hold a download mid-way; [corrupt] flips served bytes. */
    private inner class FakeFetcher : ArtifactFetcher {
        val served = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var corrupt = false
        private val bytes = mapOf("a.ort" to alpha, "b.json" to beta, "m.ort" to other)

        override suspend fun fetch(artifact: ModelArtifact, out: java.io.OutputStream, onBytes: (Int) -> Unit) {
            served += artifact.name
            val data = bytes.getValue(artifact.name).let { if (corrupt) it.reversedArray() else it }
            out.write(data, 0, 1)
            onBytes(1)
            gate?.await()
            out.write(data, 1, data.size - 1)
            onBytes(data.size - 1)
        }
    }

    private fun TestScope.store(
        root: File = tmp.root,
        setting: MemorySetting = MemorySetting(),
        fetcher: ArtifactFetcher = FakeFetcher(),
    ): DiskModelStore {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DiskModelStore(root, setting, backgroundScope, fetcher, dispatcher, listOf(small, medium))
    }

    @Test
    fun completedDownloadIsVerifiedInstalledAndActivated() = runTest {
        val setting = MemorySetting()
        val s = store(setting = setting)
        s.download("small")
        assertEquals(0f, s.state.value.downloads["small"])
        runCurrent()
        val st = s.state.value
        assertEquals(setOf("small"), st.installed)
        assertEquals("small", st.activeId)
        assertTrue(st.downloads.isEmpty())
        assertEquals("small", setting.id)
        val dir = s.modelDir("small")!!
        assertEquals("alpha weights", File(dir, "a.ort").readText())
        assertTrue(File(dir, ".complete").isFile)
        assertFalse(File(tmp.root, "small.partial").exists())
    }

    @Test
    fun laterDownloadReplacesActiveModel() = runTest {
        val s = store()
        s.download("small"); runCurrent()
        s.download("medium"); runCurrent()
        assertEquals("medium", s.state.value.activeId)
        assertEquals(setOf("small", "medium"), s.state.value.installed)
    }

    @Test
    fun checksumMismatchFailsWithoutInstalling() = runTest {
        val fetcher = FakeFetcher().apply { corrupt = true }
        val s = store(fetcher = fetcher)
        s.download("small"); runCurrent()
        val st = s.state.value
        assertTrue(st.installed.isEmpty())
        assertNull(st.activeId)
        assertTrue(st.downloads.isEmpty())
        assertNotNull(st.errors["small"])
        assertTrue(st.errors.getValue("small").contains("Checksum"))
    }

    @Test
    fun cancelMidDownloadDeletesPartialFiles() = runTest {
        val fetcher = FakeFetcher().apply { gate = CompletableDeferred() }
        val s = store(fetcher = fetcher)
        s.download("small"); runCurrent()
        assertTrue(File(tmp.root, "small.partial").exists())
        assertTrue(s.state.value.downloads.containsKey("small"))
        s.cancelDownload("small"); runCurrent()
        assertFalse(File(tmp.root, "small.partial").exists())
        assertTrue(s.state.value.downloads.isEmpty())
        assertTrue(s.state.value.installed.isEmpty())
        assertTrue(s.state.value.errors.isEmpty())
    }

    @Test
    fun cancelAfterTheLastByteNeverInstalls() = runTest {
        lateinit var s: DiskModelStore
        val fetcher = ArtifactFetcher { artifact, out, onBytes ->
            val bytes = if (artifact.name == "a.ort") alpha else beta
            out.write(bytes)
            onBytes(bytes.size)
            if (artifact.name == "b.json") s.cancelDownload("small")
        }
        s = store(fetcher = fetcher)
        s.download("small"); runCurrent()
        assertTrue(s.state.value.installed.isEmpty())
        assertNull(s.state.value.activeId)
        assertFalse(File(tmp.root, "small").exists())
        assertFalse(File(tmp.root, "small.partial").exists())
    }

    @Test
    fun persistedActiveModelMatchesStateAfterConcurrentDownloads() = runTest {
        val setting = MemorySetting()
        val s = store(setting = setting)
        s.download("small")
        s.download("medium")
        runCurrent()
        assertEquals(setOf("small", "medium"), s.state.value.installed)
        assertEquals(s.state.value.activeId, setting.id)
    }

    @Test
    fun noCurrentModelIsRetired() {
        assertTrue(ModelCatalog.models.none { it.id in DiskModelStore.RETIRED_MODEL_IDS })
    }

    @Test
    fun filesOfModelsNoLongerInTheCatalogAreDeleted() = runTest {
        val old = File(tmp.root, "moonshine-small").apply { mkdirs() }
        File(old, "encoder.ort").writeBytes(other)
        val oldPartial = File(tmp.root, "nemotron-en-160ms.partial").apply { mkdirs() }
        val keepPartial = File(tmp.root, "small.partial").apply { mkdirs() }
        val unrelated = File(tmp.root, "notes").apply { mkdirs() }
        store()
        runCurrent()
        assertFalse(old.exists())
        assertFalse(oldPartial.exists())
        assertTrue(keepPartial.isDirectory)
        assertTrue("only retired model directories are pruned", unrelated.isDirectory)
    }

    @Test
    fun verifiedArtifactsFromAnInterruptedDownloadAreReused() = runTest {
        val partial = File(tmp.root, "small.partial").apply { mkdirs() }
        File(partial, "a.ort").writeBytes(alpha)
        val fetcher = FakeFetcher()
        val s = store(fetcher = fetcher)
        s.download("small"); runCurrent()
        assertEquals(listOf("b.json"), fetcher.served)
        assertEquals("small", s.state.value.activeId)
    }

    @Test
    fun installedModelsAndActiveSurviveRestart() = runTest {
        val setting = MemorySetting()
        store(setting = setting).apply { download("small"); runCurrent() }
        val reopened = store(setting = setting)
        assertEquals(setOf("small"), reopened.state.value.installed)
        assertEquals("small", reopened.state.value.activeId)
    }

    @Test
    fun staleMarkerFromAnOlderCatalogIsNotInstalled() = runTest {
        val dir = File(tmp.root, "small").apply { mkdirs() }
        File(dir, "a.ort").writeBytes(alpha)
        File(dir, "b.json").writeBytes(beta)
        File(dir, ".complete").writeText("a.ort 0000")
        assertTrue(store().state.value.installed.isEmpty())
    }

    @Test
    fun activeModelIsProtectedAndOthersDelete() = runTest {
        val s = store()
        s.download("small"); runCurrent()
        s.download("medium"); runCurrent()
        assertEquals(DeleteResult.ActiveModelProtected, s.delete("medium"))
        assertEquals(DeleteResult.Deleted, s.delete("small"))
        runCurrent()
        assertEquals(setOf("medium"), s.state.value.installed)
        assertFalse(File(tmp.root, "small").exists())
        assertNull(s.modelDir("small"))
        assertEquals(DeleteResult.NotInstalled, s.delete("small"))
        assertFalse(s.setActive("small"))
        assertTrue(s.setActive("medium"))
    }
}
