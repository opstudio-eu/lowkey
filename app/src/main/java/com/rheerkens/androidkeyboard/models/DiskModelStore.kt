package com.rheerkens.androidkeyboard.models

import android.content.Context
import android.os.storage.StorageManager
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URL
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/** Persists which installed model is active. */
interface ActiveModelSetting {
    fun load(): String?
    fun save(id: String?)
}

class PrefsActiveModelSetting(context: Context) : ActiveModelSetting {
    private val prefs = context.getSharedPreferences("models", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString(KEY, null)
    override fun save(id: String?) = prefs.edit { putString(KEY, id) }

    private companion object {
        const val KEY = "active"
    }
}

/** Streams one artifact. Implementations must stop promptly when the coroutine is cancelled. */
fun interface ArtifactFetcher {
    suspend fun fetch(artifact: ModelArtifact, out: OutputStream, onBytes: (Int) -> Unit)
}

/** Downloads over HTTPS only. */
class HttpsArtifactFetcher : ArtifactFetcher {
    override suspend fun fetch(artifact: ModelArtifact, out: OutputStream, onBytes: (Int) -> Unit) = coroutineScope {
        val connection = URL(artifact.url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        // A blocked read ignores coroutine cancellation; closing the connection from another
        // thread as soon as this scope is cancelled makes the read fail at once. The cancel can
        // come before the connection is open (setting up TLS the first time takes a while), when
        // closing does nothing and the connection would then open and wait for its timeouts: so
        // keep closing it until the blocked call has returned. ATOMIC, so the watchdog runs even
        // when the cancel comes before it started.
        val returned = AtomicBoolean(false)
        val watchdog = launch(Dispatchers.Default, start = CoroutineStart.ATOMIC) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    while (!returned.get()) {
                        connection.disconnect()
                        delay(50)
                    }
                }
            }
        }
        try {
            withContext(Dispatchers.IO) {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code for ${artifact.name}")
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        onBytes(n)
                    }
                }
            }
        } finally {
            returned.set(true)
            watchdog.cancel()
            connection.disconnect()
        }
    }
}

/**
 * Stores each model in `root/<id>/`, outside the APK. A download writes to `root/<id>.partial/`,
 * verifies every artifact's size and SHA-256, then renames the directory into place and writes a
 * marker. Verified artifacts left by an interrupted download are reused; cancelling deletes them.
 */
class DiskModelStore(
    private val root: File,
    private val activeSetting: ActiveModelSetting,
    private val scope: CoroutineScope,
    private val fetcher: ArtifactFetcher = HttpsArtifactFetcher(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val catalog: List<ModelInfo> = ModelCatalog.models,
    /** Bytes that can be allocated for models; 0 or less skips the check. */
    private val freeBytes: () -> Long = { 0L },
) : ModelStore {
    constructor(context: Context, scope: CoroutineScope) : this(
        File(context.filesDir, "models"), PrefsActiveModelSetting(context), scope,
        freeBytes = {
            val storage = context.getSystemService(StorageManager::class.java)
            runCatching { storage.getAllocatableBytes(storage.getUuidForPath(context.filesDir)) }.getOrDefault(0L)
        },
    )

    private val jobs = ConcurrentHashMap<String, Job>()

    /** Serializes cancel against install, and state updates with the persisted active model. */
    private val commitLock = Any()

    private val _state = MutableStateFlow(initialState())
    override val state: StateFlow<ModelStoreState> = _state.asStateFlow()

    init {
        scope.launch(io) { pruneRemovedModels() }
    }

    /**
     * Deletes the files of models that earlier versions offered and this one doesn't (Moonshine,
     * Nemotron, Parakeet, Cohere and Orukeet, up to 2.9 GB each), including partial downloads.
     * Only the directories named in [RETIRED_MODEL_IDS] are touched.
     */
    private fun pruneRemovedModels() {
        val current = catalog.map { it.id }.toSet()
        for (id in RETIRED_MODEL_IDS - current) {
            listOf(installDir(id), partialDir(id)).filter { it.isDirectory }.forEach { it.deleteRecursively() }
        }
    }

    private fun info(id: String): ModelInfo? = catalog.firstOrNull { it.id == id }

    private fun initialState(): ModelStoreState {
        val installed = catalog.filter { isComplete(it) }.map { it.id }.toSet()
        return ModelStoreState(installed = installed, activeId = activeSetting.load()?.takeIf { it in installed })
    }

    private fun installDir(id: String) = File(root, id)
    private fun partialDir(id: String) = File(root, "$id.partial")
    private fun marker(id: String) = File(installDir(id), MARKER)

    /** The marker records the pinned checksums, so a catalog change invalidates old files. */
    private fun fingerprint(info: ModelInfo) = info.artifacts.joinToString("\n") { "${it.name} ${it.sha256}" }

    private fun isComplete(info: ModelInfo): Boolean {
        val m = marker(info.id)
        return m.isFile && runCatching { m.readText() }.getOrNull() == fingerprint(info) &&
            info.artifacts.all { File(installDir(info.id), it.name).length() == it.sizeBytes }
    }

    override fun download(id: String) {
        val info = info(id) ?: return
        val s = _state.value
        if (id in s.installed || jobs.containsKey(id)) return
        _state.update { it.copy(downloads = it.downloads + (id to 0f), errors = it.errors - id) }
        val job = scope.launch(io, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                fetchAll(info)
                synchronized(commitLock) {
                    // A cancel that won the lock must not be followed by an install.
                    coroutineContext.ensureActive()
                    install(info)
                    _state.update {
                        it.copy(installed = it.installed + id, downloads = it.downloads - id, activeId = id)
                    }
                    activeSetting.save(id)
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { partialDir(id).deleteRecursively() }
                _state.update { it.copy(downloads = it.downloads - id) }
                throw e
            } catch (e: Exception) {
                if (!isActive) {
                    // Cancelling closes the connection, which can surface as an I/O error
                    // ("Socket closed") before the coroutine notices: treat it as the cancel it is.
                    withContext(NonCancellable) { partialDir(id).deleteRecursively() }
                    _state.update { it.copy(downloads = it.downloads - id) }
                } else {
                    _state.update {
                        it.copy(downloads = it.downloads - id, errors = it.errors + (id to (e.message ?: "Download failed")))
                    }
                }
            } finally {
                jobs.remove(id)
            }
        }
        jobs[id] = job
        job.start()
    }

    private suspend fun fetchAll(info: ModelInfo) {
        val partial = partialDir(info.id)
        if (!partial.isDirectory && !partial.mkdirs()) throw IOException("Can't create ${partial.name}")
        val total = info.installedBytes
        var done = 0L
        var lastReported = -1
        fun report() {
            val permille = (done * 1000 / total).toInt().coerceIn(0, 1000)
            if (permille != lastReported) {
                lastReported = permille
                _state.update { it.copy(downloads = it.downloads + (info.id to permille / 1000f)) }
            }
        }
        val missing = info.artifacts.filterNot { verified(File(partial, it.name), it) }
        done = total - missing.sumOf { it.sizeBytes }
        report()
        if (missing.isEmpty()) return
        requireSpace(missing.sumOf { it.sizeBytes })
        for (artifact in missing) {
            download(artifact, File(partial, artifact.name)) { n -> done += n; report() }
        }
    }

    private fun requireSpace(bytes: Long) {
        val needed = bytes + FREE_SPACE_MARGIN
        if (freeBytes() in 1 until needed) throw IOException("Not enough storage: ${needed / 1_000_000} MB needed")
    }

    /** Streams [artifact] into [target] via a `.tmp` file, verifying size and SHA-256 before the rename. */
    private suspend fun download(artifact: ModelArtifact, target: File, onBytes: (Int) -> Unit) {
        coroutineContext.ensureActive()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        DigestOutputStream(FileOutputStream(tmp), digest).use { out ->
            fetcher.fetch(artifact, out) { n ->
                written += n
                onBytes(n)
            }
        }
        if (written != artifact.sizeBytes || digest.digest().toHex() != artifact.sha256) {
            tmp.delete()
            throw IOException("Checksum mismatch for ${artifact.name}")
        }
        if (!tmp.renameTo(target)) throw IOException("Can't save ${artifact.name}")
    }

    private fun install(info: ModelInfo) {
        val dest = installDir(info.id)
        dest.deleteRecursively()
        if (!partialDir(info.id).renameTo(dest)) throw IOException("Can't install ${info.name}")
        marker(info.id).writeText(fingerprint(info))
    }

    override fun cancelDownload(id: String) {
        synchronized(commitLock) { jobs[id]?.cancel() }
    }

    override fun setActive(id: String): Boolean = synchronized(commitLock) {
        if (id !in _state.value.installed) return false
        _state.update { it.copy(activeId = id) }
        activeSetting.save(id)
        true
    }

    override fun delete(id: String): DeleteResult = synchronized(commitLock) {
        val s = _state.value
        when {
            id !in s.installed -> DeleteResult.NotInstalled
            id == s.activeId -> DeleteResult.ActiveModelProtected
            else -> {
                _state.update { it.copy(installed = it.installed - id) }
                scope.launch(io) { installDir(id).deleteRecursively() }
                DeleteResult.Deleted
            }
        }
    }

    override fun modelDir(id: String): File? = if (id in _state.value.installed) installDir(id) else null

    companion object {
        private const val MARKER = ".complete"

        /** Every model ID that earlier versions offered and 0.5.0 removed. */
        val RETIRED_MODEL_IDS = setOf(
            "moonshine-tiny", "moonshine-small", "moonshine-medium",
            "nemotron-en-80ms", "nemotron-en-160ms", "nemotron-en-560ms", "nemotron-3.5-multilingual-560ms",
            "parakeet-en-0.6b", "parakeet-unified-en-0.6b", "parakeet-tdt-0.6b-v3",
            "cohere-transcribe-14-lang", "orukeet-v0.1.0", "whisper-base-en",
        )
        private const val FREE_SPACE_MARGIN = 50L * 1024 * 1024

        private fun verified(file: File, artifact: ModelArtifact): Boolean =
            file.length() == artifact.sizeBytes && sha256(file) == artifact.sha256

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
