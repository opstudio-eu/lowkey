package com.rheerkens.androidkeyboard.voice

import com.rheerkens.androidkeyboard.speech.SpeechEngine
import com.rheerkens.androidkeyboard.speech.SpeechEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Test speech engine. Cycles through scripted English coding phrases, emitting them word by
 * word as partials, plus a fake input level. No audio is captured.
 */
class ScriptedSpeechEngine(
    private val wordDelayMs: Long = 120,
    val finalizeDelayMs: Long = 400,
    private val phrases: List<String> = SCRIPTED_PHRASES,
) : SpeechEngine {
    /** Index of the scripted phrase the next [start] will use (Diagnostics can change it). */
    @Volatile
    var nextPhraseIndex: Int = 0
        set(value) {
            field = Math.floorMod(value, phrases.size)
        }

    val phraseCount: Int get() = phrases.size

    fun phrase(index: Int): String = phrases[Math.floorMod(index, phrases.size)]
    private val stopSignals = HashMap<Long, CompletableDeferred<Unit>>()

    override fun start(sessionId: Long): Flow<SpeechEvent> {
        val words = phrase(nextPhraseIndex).split(' ')
        nextPhraseIndex += 1
        val stopSignal = CompletableDeferred<Unit>()
        synchronized(stopSignals) { stopSignals[sessionId] = stopSignal }
        return channelFlow {
            var heard = 0
            val producer = launch {
                delay(wordDelayMs * 2) // speech onset
                for (word in words) {
                    heard++
                    send(SpeechEvent.Partial(sessionId, words.take(heard).joinToString(" ")))
                    delay(wordDelayMs + if (word.length > 6) wordDelayMs / 2 else 0)
                }
            }
            val meter = launch {
                while (isActive) {
                    val speaking = producer.isActive
                    send(SpeechEvent.Level(sessionId, if (speaking) 0.3f + Random.nextFloat() * 0.7f else 0.05f))
                    delay(110)
                }
            }
            try {
                stopSignal.await()
                producer.cancel()
                meter.cancel()
                delay(finalizeDelayMs)
                send(SpeechEvent.Final(sessionId, words.take(heard).joinToString(" ")))
            } finally {
                synchronized(stopSignals) { stopSignals.remove(sessionId) }
            }
        }
    }

    override fun stop(sessionId: Long) {
        synchronized(stopSignals) { stopSignals[sessionId] }?.complete(Unit)
    }

    override fun cancel(sessionId: Long) {
        // Collection is cancelled by the caller; just drop the stop signal.
        synchronized(stopSignals) { stopSignals.remove(sessionId) }
    }

    override fun release() {
        synchronized(stopSignals) { stopSignals.clear() }
    }

    companion object {
        val SCRIPTED_PHRASES = listOf(
            "explain what this kotlin coroutine scope does and when it gets cancelled",
            "refactor the view model so the repository is injected",
            "run the t3 code lint task and show me the errors",
            "why does adb install fail with insufficient storage",
        )
    }
}
