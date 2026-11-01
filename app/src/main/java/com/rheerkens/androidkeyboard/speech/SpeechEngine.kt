package com.rheerkens.androidkeyboard.speech

import kotlinx.coroutines.flow.Flow

/** Events from one capture. Every event carries the [sessionId] passed to [SpeechEngine.start]. */
sealed interface SpeechEvent {
    val sessionId: Long

    /** Provisional text for the whole utterance so far (replaces, not appends). */
    data class Partial(override val sessionId: Long, val text: String) : SpeechEvent

    /** The model is loading ([loading] true) or ready (false). Audio captured meanwhile is kept. */
    data class Loading(override val sessionId: Long, val loading: Boolean) : SpeechEvent

    /** Seconds of captured audio still waiting for the model (it can lag on slow phones). */
    data class Backlog(override val sessionId: Long, val seconds: Float) : SpeechEvent

    /** Input level 0f..1f for the meter. */
    data class Level(override val sessionId: Long, val level: Float) : SpeechEvent

    /** Final text after [SpeechEngine.stop]. Emitted at most once, then the flow completes. */
    data class Final(override val sessionId: Long, val text: String) : SpeechEvent

    data class Error(override val sessionId: Long, val message: String) : SpeechEvent
}

/**
 * Speech recognition for one capture at a time. [LocalSpeechEngine] runs a local streaming
 * model fed by an [AudioSource]; tests use scripted engines.
 *
 * Contract:
 * - [start] begins capture and returns a flow of [SpeechEvent]s tagged with [sessionId].
 *   Cancelling collection of the flow must stop capture and release per-session resources.
 * - [stop] ends audio capture; the engine finalizes and emits one [SpeechEvent.Final].
 * - [cancel] aborts capture and finalization without a Final event.
 * - [release] frees the model; the engine can't be used afterwards.
 * Callers must still ignore events whose session ID isn't current (late callbacks).
 */
interface SpeechEngine {
    fun start(sessionId: Long): Flow<SpeechEvent>
    fun stop(sessionId: Long)
    fun cancel(sessionId: Long)
    fun release()
}
