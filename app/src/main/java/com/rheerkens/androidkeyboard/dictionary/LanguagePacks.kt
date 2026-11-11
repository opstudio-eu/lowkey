package com.rheerkens.androidkeyboard.dictionary

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/** Downloaded word lists, download progress (0–1) and the last error per language id. */
data class LanguagePackState(
    val installed: Set<String> = emptySet(),
    val downloads: Map<String, Float> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
)

/** Streams the body of an HTTPS [url] into [out]. Must stop promptly when cancelled. */
fun interface WordListFetcher {
    suspend fun fetch(url: String, out: OutputStream)
}

/** Downloads over HTTPS only, closing the connection as soon as the coroutine is cancelled. */
class HttpsWordListFetcher : WordListFetcher {
    override suspend fun fetch(url: String, out: OutputStream) = coroutineScope {
        require(url.startsWith("https://")) { "Not an HTTPS URL" }
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        val watchdog = launch(Dispatchers.Default) {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                }
            }
        } finally {
            watchdog.cancel()
            connection.disconnect()
        }
    }
}

/**
 * Word lists for languages that are not bundled. A download fetches the pinned AOSP file (as the
 * base64 text the AOSP source browser serves), checks its size and SHA-256 against the catalog,
 * converts it with [AospWordList], and saves it gzipped as `dir/<id>.dict` next to a marker with
 * the source's SHA-256. A list whose marker no longer matches the catalog counts as not installed.
 */
class LanguagePackStore(
    private val dir: File,
    private val catalog: LanguageCatalog,
    private val scope: CoroutineScope,
    private val fetcher: WordListFetcher = HttpsWordListFetcher(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Called on [io] after a language was installed. */
    private val onInstalled: (KeyboardLanguage) -> Unit = {},
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    /**
     * One install at a time: each holds a download buffer and the conversion's word map, and
     * several together (the start-up restore, or many Download taps) could exhaust the heap.
     */
    private val installs = Semaphore(1)
    private val _state = MutableStateFlow(LanguagePackState(installed = scan()))
    val state: StateFlow<LanguagePackState> = _state.asStateFlow()

    fun isInstalled(language: KeyboardLanguage): Boolean = language.bundled || language.id in _state.value.installed

    /** The downloaded word list of [language] (never a bundled one). */
    fun file(language: KeyboardLanguage): File = File(dir, language.dictionaryFile)

    fun download(id: String) {
        val language = catalog[id]?.takeIf { !it.bundled } ?: return
        if (id in _state.value.installed || jobs.containsKey(id)) return
        _state.update { it.copy(downloads = it.downloads + (id to 0f), errors = it.errors - id) }
        val job = scope.launch(io, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                installs.withPermit { install(language) }
                _state.update { it.copy(installed = it.installed + id, downloads = it.downloads - id) }
                onInstalled(language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Cancelling closes the connection, which can surface as an I/O error first.
                if (isActive) _state.update { it.copy(errors = it.errors + (id to message(e))) }
            } catch (e: OutOfMemoryError) {
                // Report it like any failed download instead of taking the keyboard down with it.
                _state.update { it.copy(errors = it.errors + (id to "Not enough memory to convert the word list")) }
            }
        }
        // Runs however the job ends, also when it is cancelled before it ever starts.
        job.invokeOnCompletion {
            cleanUp(language)
            jobs.remove(id, job)
            _state.update { it.copy(downloads = it.downloads - id) }
        }
        jobs[id] = job
        job.start()
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    fun delete(id: String) {
        val language = catalog[id]?.takeIf { !it.bundled } ?: return
        cancel(id)
        file(language).delete()
        marker(language).delete()
        _state.update { it.copy(installed = it.installed - id, errors = it.errors - id) }
    }

    /** Downloads, verifies, converts and saves [language]. Throws on any failure. */
    internal suspend fun install(language: KeyboardLanguage) {
        // Another install may create the folder at the same moment: only its absence is an error.
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("Can't create the dictionary folder")
        val expectedText = language.sourceBytes * 4 / 3 + 4
        // Base64 line breaks add under 3%; anything beyond this is not the pinned file.
        val limit = expectedText + expectedText / 16 + 4096
        val text = object : ByteArrayOutputStream((expectedText + 1024).toInt()) {
            var lastReported = -1
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (count + len > limit) throw IOException("The word list is larger than expected")
                super.write(b, off, len)
                val permille = (count * 1000L / expectedText).toInt().coerceIn(0, 999)
                if (permille / 10 != lastReported) {
                    lastReported = permille / 10
                    _state.update { it.copy(downloads = it.downloads + (language.id to permille / 1000f)) }
                }
            }
        }
        fetcher.fetch(language.sourceUrl, text)
        coroutineContext.ensureActive()
        val gz = try {
            Base64.getMimeDecoder().decode(text.toByteArray())
        } catch (e: IllegalArgumentException) {
            throw IOException("The download is not a word list", e)
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(gz).joinToString("") { "%02x".format(it) }
        if (gz.size.toLong() != language.sourceBytes || sha != language.sourceSha256) {
            throw IOException("Checksum mismatch: the download is not the pinned word list")
        }
        val tmp = File(dir, language.dictionaryFile + ".tmp")
        val context = coroutineContext
        GZIPOutputStream(FileOutputStream(tmp)).bufferedWriter(Charsets.UTF_8).use {
            AospWordList.convertGzip(gz, it) { context.ensureActive() }
        }
        // The rename below is the commit point: a cancel up to here leaves nothing installed.
        context.ensureActive()
        // A crash between these steps leaves no marker, so the language counts as not installed.
        marker(language).delete()
        if (!tmp.renameTo(file(language))) throw IOException("Can't save the word list")
        marker(language).writeText(language.sourceSha256)
    }

    private fun scan(): Set<String> {
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
        return catalog.languages.filter { !it.bundled && file(it).isFile && markerMatches(it) }.map { it.id }.toSet()
    }

    private fun markerMatches(language: KeyboardLanguage) =
        runCatching { marker(language).readText().trim() }.getOrNull() == language.sourceSha256

    private fun marker(language: KeyboardLanguage) = File(dir, language.id + ".sha256")

    private fun cleanUp(language: KeyboardLanguage) {
        File(dir, language.dictionaryFile + ".tmp").delete()
    }

    private fun message(e: Exception): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "No connection"
        is java.net.SocketTimeoutException -> "The connection timed out"
        else -> e.message ?: "Download failed"
    }
}
