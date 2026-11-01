package com.rheerkens.androidkeyboard.speech

import com.rheerkens.androidkeyboard.models.ModelInfo
import com.rheerkens.androidkeyboard.settings.ModelRetention
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** The model to use for the next capture, where its files are, and the chosen language. */
data class LoadableModel(val info: ModelInfo, val dir: File, val language: String? = null) {
    /** Identifies a loaded recognizer; a language change reloads multilingual models. */
    val key: String get() = if (language == null) info.id else "${info.id}|$language"
}

/** Native resources only. Downloaded model files remain installed after release. */
data class ModelMemoryState(val loadedKey: String? = null, val inUse: Boolean = false)

/**
 * [SpeechEngine] that runs a local streaming [Recognizer] on audio from an [AudioSource].
 *
 * - Capture starts immediately; audio recorded while the model loads is queued (up to
 *   [MAX_QUEUED_CHUNKS]), not lost. [stop] ends capture at once, even mid-load; only audio
 *   captured before Stop is transcribed.
 * - Every native call runs on one [native] thread. The recognizer stream is owned by one
 *   session at a time; a session's cleanup never touches a newer session's stream, and the
 *   model is never closed while a session owns it.
 * - Idle native resources are released according to [setRetention]. Active capture and final
 *   transcription never expire. Model files stay on disk.
 */
class LocalSpeechEngine(
    private val audioSource: () -> AudioSource,
    private val activeModel: () -> LoadableModel?,
    private val keyterms: () -> List<String> = { emptyList() },
    private val factory: RecognizerFactory = EngineRecognizerFactory,
    /** Notified around native model work ("loading <key>", "running <key>", null when done). */
    private val engineWork: (String?) -> Unit = {},
    private val native: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, "speech-engine") }.asCoroutineDispatcher(),
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : SpeechEngine {
    private val housekeeping = CoroutineScope(SupervisorJob() + native)

    /** Stop signals are registered in [start] so a Stop before collection begins isn't lost. */
    private val stopSignals = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    // Confined to the native thread.
    private var recognizer: Recognizer? = null
    private var appliedTerms: List<String>? = null
    /** Session whose stream is open on [recognizer], if any. */
    private var streamOwner: Long? = null
    private var unloadRequested = false
    private var retention = ModelRetention.TWO_MINUTES
    private var idleSince: Long? = null
    private var idleJob: Job? = null
    private val _memory = MutableStateFlow(ModelMemoryState())
    val memory = _memory.asStateFlow()

    @Volatile
    private var loadedId: String? = null

    /** True when the active model is already in memory. */
    val isWarm: Boolean get() = loadedId != null && loadedId == activeModel()?.key

    /** Loads the active model ahead of the first capture. */
    suspend fun warmUp() {
        withContext(native) {
            ensureLoaded()
            if (streamOwner == null) beginIdle()
        }
    }

    /** Applies changes to an already idle model using its original idle start time. */
    fun setRetention(value: ModelRetention) {
        housekeeping.launch {
            retention = value
            scheduleIdleRelease()
        }
    }

    override fun start(sessionId: Long): Flow<SpeechEvent> {
        val stopSignal = CompletableDeferred<Unit>()
        stopSignals[sessionId] = stopSignal
        return channelFlow {
            try {
                val audio = Channel<FloatArray>(MAX_QUEUED_CHUNKS, BufferOverflow.DROP_OLDEST)
                val queued = AtomicInteger(0)
                val capture = launch {
                    audioSource().chunks().collect { chunk ->
                        send(SpeechEvent.Level(sessionId, levelOf(chunk)))
                        audio.send(chunk)
                        queued.updateAndGet { minOf(it + 1, MAX_QUEUED_CHUNKS) }
                    }
                }
                // Stop ends capture immediately, independent of model loading.
                launch {
                    stopSignal.await()
                    capture.cancelAndJoin()
                    audio.close()
                }
                val cold = !isWarm
                if (cold) send(SpeechEvent.Loading(sessionId, true))
                val lines = LinkedHashMap<Long, String>()
                var changed = false
                var failure: Throwable? = null
                val rec = withContext(native) {
                    val r = ensureLoaded()
                    coroutineContext.ensureActive() // cancelled while loading: don't open a stream
                    openStream(r, sessionId, { id, text -> lines[id] = text; changed = true }, { failure = it })
                    r
                }
                if (cold) send(SpeechEvent.Loading(sessionId, false))
                withContext(native) {
                    var reportedSeconds = 0
                    for (chunk in audio) {
                        if (streamOwner != sessionId) break
                        // Report lag in whole seconds (10 chunks each), including when it clears.
                        val seconds = queued.updateAndGet { maxOf(it - 1, 0) } / 10
                        if (seconds != reportedSeconds) {
                            reportedSeconds = seconds
                            send(SpeechEvent.Backlog(sessionId, seconds.toFloat()))
                        }
                        rec.addAudio(chunk)
                        failure?.let { throw it }
                        if (changed) {
                            changed = false
                            send(SpeechEvent.Partial(sessionId, joinLines(lines.values)))
                        }
                    }
                }
                stopSignal.await()
                val text = withContext(native) {
                    if (streamOwner != sessionId) throw IllegalStateException("Speech stream was interrupted")
                    rec.finish()
                    closeStream(sessionId)
                    failure?.let { throw it }
                    joinLines(lines.values)
                }
                send(SpeechEvent.Final(sessionId, text))
            } finally {
                stopSignals.remove(sessionId)
                withContext(NonCancellable + native) {
                    if (streamOwner == sessionId) {
                        runCatching { recognizer?.abort() }
                        closeStream(sessionId)
                    } else if (streamOwner == null && recognizer != null && idleSince == null) {
                        // Cancellation during loading, or a failed stream start, still has an
                        // idle model to release. Never change a newer session's timer.
                        beginIdle()
                    }
                }
            }
        }
    }

    override fun stop(sessionId: Long) {
        stopSignals[sessionId]?.complete(Unit)
    }

    override fun cancel(sessionId: Long) {
        // The caller cancels collection; the flow's finally block aborts this session's stream.
        stopSignals.remove(sessionId)
    }

    /** Frees the loaded model; the next capture loads it again. Deferred while a stream is open. */
    fun unload() {
        housekeeping.launch {
            if (streamOwner != null) unloadRequested = true else closeLoaded()
        }
    }

    override fun release() = unload()

    private fun openStream(r: Recognizer, sessionId: Long, onLine: (Long, String) -> Unit, onError: (Throwable) -> Unit) {
        if (streamOwner != null) runCatching { r.abort() } // a stale stream from a cancelled session
        engineWork("running $loadedId")
        try {
            r.start(onLine, onError)
        } catch (t: Throwable) {
            runCatching { r.abort() }
            engineWork(null)
            throw t
        }
        streamOwner = sessionId
        _memory.value = ModelMemoryState(loadedId, inUse = true)
    }

    private fun closeStream(sessionId: Long) {
        if (streamOwner != sessionId) return
        streamOwner = null
        engineWork(null)
        if (unloadRequested) closeLoaded() else beginIdle()
    }

    private fun ensureLoaded(): Recognizer {
        idleJob?.cancel()
        idleSince = null
        val model = activeModel() ?: throw IllegalStateException("No speech model installed")
        // Loading and keyterm setup run native code: a crash leaves the marker for the next
        // start, while a caught failure clears it (or restores the open stream's marker).
        engineWork("loading ${model.key}")
        try {
            val current = recognizer
            val rec = if (current != null && loadedId == model.key) {
                current
            } else {
                closeLoaded()
                factory.load(model.info, model.dir, model.language).also {
                    recognizer = it
                    loadedId = model.key
                    _memory.value = ModelMemoryState(loadedId)
                }
            }
            unloadRequested = false
            val terms = keyterms()
            if (terms != appliedTerms) {
                rec.setKeyterms(terms)
                appliedTerms = terms
            }
            return rec
        } finally {
            engineWork(if (streamOwner != null) "running $loadedId" else null)
        }
    }

    private fun closeLoaded() {
        idleJob?.cancel()
        idleJob = null
        idleSince = null
        recognizer?.let { runCatching { it.close() } }
        recognizer = null
        loadedId = null
        appliedTerms = null
        streamOwner = null
        unloadRequested = false
        _memory.value = ModelMemoryState()
    }

    private fun beginIdle() {
        idleSince = nowMillis()
        _memory.value = ModelMemoryState(loadedId)
        scheduleIdleRelease()
    }

    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = null
        val since = idleSince ?: return
        val timeout = retention.millis ?: return
        if (recognizer == null || streamOwner != null) return
        val remaining = (timeout - (nowMillis() - since)).coerceAtLeast(0)
        if (remaining == 0L) {
            closeLoaded()
        } else {
            idleJob = housekeeping.launch {
                delay(remaining)
                // All lifecycle decisions run on the native dispatcher, like recognition.
                if (streamOwner == null) closeLoaded()
            }
        }
    }

    companion object {
        /** About two minutes of 100 ms chunks; older audio is dropped beyond that. */
        const val MAX_QUEUED_CHUNKS = 1_200

        fun joinLines(lines: Collection<String>): String =
            lines.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    }
}
