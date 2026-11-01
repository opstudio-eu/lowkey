package com.rheerkens.androidkeyboard.voice

import com.rheerkens.androidkeyboard.dictionary.PhraseRule
import com.rheerkens.androidkeyboard.speech.SpeechEngine
import com.rheerkens.androidkeyboard.speech.SpeechEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why a capture was cancelled. [label] appears in the Diagnostics log. */
enum class CancelReason(val label: String) {
    SWITCHED_TO_KEYS("switched to keys"),
    KEYBOARD_HIDDEN("keyboard hidden"),
    EDITOR_CHANGED("editor changed"),
    ENGINE_ERROR("engine error"),
    KEYBOARD_CLOSED("keyboard closed"),
    MODEL_PICKER("opened model picker"),
    MODEL_CHANGED("model changed"),
    CURSOR_MOVED("cursor moved"),
}

enum class VoicePhase(val label: String) { LISTENING("Listening"), FINALIZING("Finalizing") }

/** Result of the most recent capture, shown briefly in the voice row (see [VoiceNotes]). */
sealed interface VoiceOutcome {
    data class Inserted(val words: Int, val rulesApplied: Int) : VoiceOutcome
    data object NoSpeech : VoiceOutcome
    /** A message for the voice row that isn't about a capture (for example a held-back model). */
    data class Notice(val text: String) : VoiceOutcome
    data class Cancelled(
        val reason: CancelReason,
        val phase: VoicePhase,
        val wordsDiscarded: Int,
        /** Engine error message when [reason] is [CancelReason.ENGINE_ERROR]. */
        val error: String? = null,
        /**
         * The editor kept the provisional words: Android commits composing text when a field
         * loses focus, so they couldn't be removed (switching fields, Home, rotation).
         */
        val leftInEditor: Boolean = false,
    ) : VoiceOutcome
}

/** Explicit voice state machine. Only [Listening] and [Finalizing] own a session ID. */
sealed interface VoiceState {
    data class Idle(val last: VoiceOutcome? = null) : VoiceState
    data class Listening(
        val sessionId: Long,
        val startedAtMs: Long,
        val text: String = "",
        val level: Float = 0f,
        /** The model is still loading; audio is being recorded and will be transcribed. */
        val loading: Boolean = false,
        /** Seconds of audio the model hasn't processed yet. */
        val backlogSeconds: Float = 0f,
    ) : VoiceState
    data class Finalizing(
        val sessionId: Long,
        val text: String,
        val stoppedAtMs: Long,
        val backlogSeconds: Float = 0f,
    ) : VoiceState
    data object NoModel : VoiceState
    data object NoPermission : VoiceState
}

enum class VoiceReadiness { READY, NO_PERMISSION, NO_MODEL }

/** Where dictated text goes. The IME implements this with composing text in the host editor. */
interface VoiceTextSink {
    /** Shows provisional text (replaces any previous provisional text). */
    fun showProvisional(text: String)

    /**
     * Removes provisional text only; committed text stays, and text the provisional words
     * replaced (a selection) is restored. Returns false if the editor kept the provisional words.
     */
    fun clearProvisional(): Boolean

    /**
     * Replaces provisional text with [text] and commits it. Called once per session.
     * Returns false, without writing, if the insertion point no longer belongs to the session.
     */
    fun commitFinal(text: String): Boolean

    /** A new capture is starting at the editor's current insertion point. */
    fun sessionStarted() {}

    /** True when the editor wants a capital letter where dictated text goes. */
    fun capitalizeAtInsertion(): Boolean
}

/** Per-capture options, read when each session starts or finalizes. */
data class VoiceConfig(
    val liveText: Boolean = true,
    val continuous: Boolean = false,
    val applyPhraseRules: Boolean = false,
    val autoCapitalize: Boolean = true,
    val matchTermCase: Boolean = true,
    val terms: List<String> = emptyList(),
    val autoStopMs: Long = 0,
    val rules: List<PhraseRule> = emptyList(),
)

/** Diagnostics record for one finished session. */
data class VoiceSessionRecord(
    val sessionId: Long,
    val startedAtMs: Long,
    val transitions: List<String>,
    val words: Int,
    val finalizeLatencyMs: Long?,
)

/**
 * Drives one voice capture at a time: Idle → Listening → Finalizing → Idle, with
 * NoModel / NoPermission as blocking states.
 *
 * - Every capture gets a new session ID. Events from any other session are ignored, so late
 *   callbacks after a cancel can never insert text.
 * - [stop] works once per session; repeated stops are no-ops.
 * - Final text is committed exactly once, after optional phrase rules and auto-capitalization.
 * - [cancel] removes provisional text and keeps committed text.
 */
class VoiceSessionController(
    private val scope: CoroutineScope,
    private val engine: SpeechEngine,
    private val sink: VoiceTextSink,
    private val config: () -> VoiceConfig,
    private val readiness: () -> VoiceReadiness,
    private val clock: () -> Long,
    firstSessionId: Long = 1,
    private val onSessionFinished: (VoiceSessionRecord) -> Unit = {},
    /** After final insertion/no-speech, with the session already idle. Never called on cancel. */
    private val onDictationCompleted: (VoiceOutcome) -> Unit = {},
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var nextSessionId = firstSessionId
    private var captureJob: Job? = null
    private var silenceJob: Job? = null
    /** Audio-level voice activity for the current session (auto-stop and no-speech guard). */
    private var levelEvents = 0
    private var heardSpeech = false
    private var lastSpeechAtMs = 0L
    private var transitions = mutableListOf<String>()
    private var sessionStartedAtMs = 0L

    /** Number of events dropped because their session wasn't current (for tests and diagnostics). */
    var staleEventsIgnored: Int = 0
        private set

    val currentSessionId: Long?
        get() = when (val s = _state.value) {
            is VoiceState.Listening -> s.sessionId
            is VoiceState.Finalizing -> s.sessionId
            else -> null
        }

    /** Starts a new capture. No-op while a capture is running. */
    fun start(): Boolean {
        val s = _state.value
        if (s is VoiceState.Listening || s is VoiceState.Finalizing) return false
        when (readiness()) {
            VoiceReadiness.NO_PERMISSION -> { _state.value = VoiceState.NoPermission; return false }
            VoiceReadiness.NO_MODEL -> { _state.value = VoiceState.NoModel; return false }
            VoiceReadiness.READY -> Unit
        }
        val id = nextSessionId++
        val now = clock()
        transitions = mutableListOf("listening")
        sessionStartedAtMs = now
        sink.sessionStarted()
        levelEvents = 0
        heardSpeech = false
        lastSpeechAtMs = now
        _state.value = VoiceState.Listening(sessionId = id, startedAtMs = now)
        val flow = engine.start(id)
        captureJob = scope.launch {
            try {
                flow.collect { deliver(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                deliver(SpeechEvent.Error(id, e.message ?: e.javaClass.simpleName))
            }
        }
        armSilenceTimer(id)
        return true
    }

    /** Moves Listening → Finalizing once. Returns false (no-op) in any other state. */
    fun stop(): Boolean {
        val s = _state.value as? VoiceState.Listening ?: return false
        silenceJob?.cancel()
        transitions += "finalizing"
        _state.value = VoiceState.Finalizing(s.sessionId, s.text, clock(), s.backlogSeconds)
        engine.stop(s.sessionId)
        return true
    }

    /**
     * Cancels Listening or Finalizing, discarding provisional text.
     * Returns the outcome (with discarded word count), or null if nothing was running.
     */
    fun cancel(reason: CancelReason, error: String? = null): VoiceOutcome.Cancelled? {
        val s = _state.value
        val (id, phase, text) = when (s) {
            is VoiceState.Listening -> Triple(s.sessionId, VoicePhase.LISTENING, s.text)
            is VoiceState.Finalizing -> Triple(s.sessionId, VoicePhase.FINALIZING, s.text)
            else -> return null
        }
        captureJob?.cancel()
        captureJob = null
        silenceJob?.cancel()
        engine.cancel(id)
        val words = countWords(text)
        val left = !sink.clearProvisional() && words > 0
        val outcome = VoiceOutcome.Cancelled(reason, phase, words, error, leftInEditor = left)
        _state.value = VoiceState.Idle(outcome)
        finish(id, "cancelled (${reason.label}${if (left) ", words left in field" else ""})", words, null)
        return outcome
    }

    /** Re-checks permission and model after the user returns from the companion app. */
    fun refreshReadiness() {
        val s = _state.value
        if (s is VoiceState.Listening || s is VoiceState.Finalizing) return
        _state.value = when (readiness()) {
            VoiceReadiness.NO_PERMISSION -> VoiceState.NoPermission
            VoiceReadiness.NO_MODEL -> VoiceState.NoModel
            VoiceReadiness.READY -> if (s is VoiceState.Idle) s else VoiceState.Idle()
        }
    }

    /** Shows [text] in the voice row without starting a capture. */
    fun showNotice(text: String) {
        val s = _state.value
        if (s is VoiceState.Listening || s is VoiceState.Finalizing) return
        _state.value = VoiceState.Idle(VoiceOutcome.Notice(text))
    }

    /** Clears the last outcome so the voice row's status area is empty again. */
    fun clearOutcome() {
        if (_state.value is VoiceState.Idle) _state.value = VoiceState.Idle()
    }

    /** Entry point for engine events. Exposed for tests; events for stale sessions are dropped. */
    internal fun deliver(event: SpeechEvent) {
        val s = _state.value
        if (event.sessionId != currentSessionId) {
            staleEventsIgnored++
            return
        }
        when (event) {
            is SpeechEvent.Level -> if (s is VoiceState.Listening) {
                _state.value = s.copy(level = event.level)
                levelEvents++
                if (event.level >= SPEECH_LEVEL) {
                    heardSpeech = true
                    lastSpeechAtMs = clock()
                }
            }
            is SpeechEvent.Backlog -> when (s) {
                is VoiceState.Listening -> _state.value = s.copy(backlogSeconds = event.seconds)
                is VoiceState.Finalizing -> _state.value = s.copy(backlogSeconds = event.seconds)
                else -> Unit
            }
            is SpeechEvent.Loading -> if (s is VoiceState.Listening) {
                _state.value = s.copy(loading = event.loading)
            }
            is SpeechEvent.Partial -> if (s is VoiceState.Listening) {
                _state.value = s.copy(text = event.text)
                if (config().liveText) sink.showProvisional(event.text)
            }
            is SpeechEvent.Final -> commit(event, s)
            is SpeechEvent.Error -> cancel(CancelReason.ENGINE_ERROR, event.message)
        }
    }

    private fun commit(event: SpeechEvent.Final, s: VoiceState) {
        silenceJob?.cancel()
        captureJob = null
        val cfg = config()
        val formatted = DictationFormatter.format(
            raw = event.text,
            applyRules = cfg.applyPhraseRules,
            rules = cfg.rules,
            matchTermCase = cfg.matchTermCase,
            terms = cfg.terms,
            capitalize = if (cfg.autoCapitalize) sink.capitalizeAtInsertion() else null,
        )
        // The input level never reached speech: models can invent words ("You", "Thank you.")
        // from silence, so insert nothing.
        val silent = levelEvents > 0 && !heardSpeech
        val text = if (silent) "" else formatted.text
        val rulesApplied = formatted.rulesApplied
        val latency = (s as? VoiceState.Finalizing)?.let { clock() - it.stoppedAtMs }
        val outcome: VoiceOutcome
        if (text.isEmpty()) {
            sink.clearProvisional()
            outcome = VoiceOutcome.NoSpeech
            finish(event.sessionId, "no speech", 0, latency)
        } else if (!sink.commitFinal(text)) {
            sink.clearProvisional()
            val dropped = VoiceOutcome.Cancelled(CancelReason.CURSOR_MOVED, VoicePhase.FINALIZING, countWords(text))
            finish(event.sessionId, "cancelled (${CancelReason.CURSOR_MOVED.label})", dropped.wordsDiscarded, latency)
            _state.value = VoiceState.Idle(dropped)
            return
        } else {
            val words = countWords(text)
            outcome = VoiceOutcome.Inserted(words, rulesApplied)
            val label = if (rulesApplied > 0) "committed ($rulesApplied rule${if (rulesApplied > 1) "s" else ""})" else "committed"
            finish(event.sessionId, label, words, latency)
        }
        _state.value = VoiceState.Idle(outcome)
        onDictationCompleted(outcome)
        if (cfg.continuous) start()
    }

    /**
     * Auto-stop: after speech has been heard, stops once the input level has stayed below
     * [SPEECH_LEVEL] for the configured time. Measured on audio, not on recognized text, so slow
     * or final-only models aren't stopped mid-sentence.
     */
    private fun armSilenceTimer(sessionId: Long) {
        silenceJob?.cancel()
        val ms = config().autoStopMs
        if (ms <= 0) return
        silenceJob = scope.launch {
            while (currentSessionId == sessionId && _state.value is VoiceState.Listening) {
                delay(SILENCE_POLL_MS)
                if (heardSpeech && clock() - lastSpeechAtMs >= ms && currentSessionId == sessionId) {
                    stop()
                    break
                }
            }
        }
    }

    private fun finish(id: Long, last: String, words: Int, latency: Long?) {
        transitions += last
        onSessionFinished(VoiceSessionRecord(id, sessionStartedAtMs, transitions.toList(), words, latency))
    }

    companion object {
        /** Input level (see `levelOf`) treated as speech: RMS of about 0.013. */
        const val SPEECH_LEVEL = 0.08f
        private const val SILENCE_POLL_MS = 200L

        fun countWords(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }
}
