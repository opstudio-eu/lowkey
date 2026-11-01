package com.rheerkens.androidkeyboard.voice

import com.rheerkens.androidkeyboard.dictionary.PhraseRule
import com.rheerkens.androidkeyboard.speech.SpeechEngine
import com.rheerkens.androidkeyboard.speech.SpeechEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionControllerTest {

    /** Records everything the controller asks the editor to do. */
    private class RecordingSink : VoiceTextSink {
        var provisional: String? = null
        val commits = mutableListOf<String>()
        var clears = 0
        override fun showProvisional(text: String) { provisional = text }
        var editorKeepsText = false
        override fun clearProvisional(): Boolean {
            val had = provisional != null
            provisional = null
            clears++
            return !(had && editorKeepsText)
        }
        var accept = true
        var starts = 0
        override fun commitFinal(text: String): Boolean {
            if (!accept) return false
            commits += text
            provisional = null
            return true
        }
        override fun sessionStarted() { starts++ }
        var caps = true
        override fun capitalizeAtInsertion() = caps
    }

    /** Engine driven manually by the test through per-session channels. */
    private class ManualEngine : SpeechEngine {
        val channels = mutableMapOf<Long, Channel<SpeechEvent>>()
        val stopped = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()
        override fun start(sessionId: Long): Flow<SpeechEvent> =
            Channel<SpeechEvent>(Channel.UNLIMITED).also { channels[sessionId] = it }.consumeAsFlow()
        override fun stop(sessionId: Long) { stopped += sessionId }
        override fun cancel(sessionId: Long) { cancelled += sessionId }
        override fun release() = Unit
        fun emit(event: SpeechEvent) { channels.getValue(event.sessionId).trySend(event) }
    }

    private class Harness(
        scope: CoroutineScope,
        testScope: TestScope,
        val engine: SpeechEngine,
        var config: VoiceConfig = VoiceConfig(autoCapitalize = false),
        var readiness: VoiceReadiness = VoiceReadiness.READY,
    ) {
        val sink = RecordingSink()
        val records = mutableListOf<VoiceSessionRecord>()
        val completions = mutableListOf<VoiceOutcome>()
        val commitsAtCompletion = mutableListOf<List<String>>()
        val controller = VoiceSessionController(
            scope = scope,
            engine = engine,
            sink = sink,
            config = { config },
            readiness = { readiness },
            clock = { testScope.testScheduler.currentTime },
            firstSessionId = 1,
            onSessionFinished = { records += it },
            onDictationCompleted = {
                completions += it
                commitsAtCompletion += sink.commits.toList()
            },
        )
    }

    private fun TestScope.manual(config: VoiceConfig = VoiceConfig(autoCapitalize = false)): Pair<Harness, ManualEngine> {
        val engine = ManualEngine()
        return Harness(backgroundScope, this, engine, config) to engine
    }

    @Test
    fun completionWaitsForFinalTextAndFiresOnceAfterInsertion() = runTest {
        val (h, engine) = manual()
        h.controller.start(); runCurrent()
        val id = h.controller.currentSessionId!!
        engine.emit(SpeechEvent.Partial(id, "correct this")); runCurrent()
        h.controller.stop(); runCurrent()
        assertTrue(h.completions.isEmpty())
        assertTrue(h.controller.state.value is VoiceState.Finalizing)
        engine.emit(SpeechEvent.Final(id, "correct this text")); runCurrent()
        assertEquals(listOf(VoiceOutcome.Inserted(3, 0)), h.completions)
        assertEquals(listOf(listOf("correct this text")), h.commitsAtCompletion)
        h.controller.deliver(SpeechEvent.Final(id, "duplicate"))
        assertEquals(1, h.completions.size)
    }

    @Test
    fun cancelledSessionAndLateFinalNeverTriggerCompletion() = runTest {
        val (h, _) = manual()
        h.controller.start(); runCurrent()
        val id = h.controller.currentSessionId!!
        h.controller.cancel(CancelReason.SWITCHED_TO_KEYS)
        h.controller.deliver(SpeechEvent.Final(id, "late text"))
        assertTrue(h.completions.isEmpty())
        assertTrue(h.sink.commits.isEmpty())
    }

    @Test
    fun noSpeechCompletesWithoutInsertingAnything() = runTest {
        val (h, engine) = manual()
        h.controller.start(); runCurrent()
        val id = h.controller.currentSessionId!!
        h.controller.stop()
        engine.emit(SpeechEvent.Final(id, "")); runCurrent()
        assertEquals(listOf(VoiceOutcome.NoSpeech), h.completions)
        assertTrue(h.sink.commits.isEmpty())
    }

    @Test
    fun listeningStopFinalizeCommitsExactlyOnce() = runTest {
        val (h, engine) = manual()
        assertTrue(h.controller.start())
        runCurrent()
        val id = h.controller.currentSessionId!!
        engine.emit(SpeechEvent.Partial(id, "refactor the"))
        runCurrent()
        assertEquals("refactor the", h.sink.provisional)
        assertEquals("refactor the", (h.controller.state.value as VoiceState.Listening).text)

        assertTrue(h.controller.stop())
        assertFalse("repeated Stop is a no-op", h.controller.stop())
        assertTrue(h.controller.state.value is VoiceState.Finalizing)
        assertEquals(listOf(id), engine.stopped)

        engine.emit(SpeechEvent.Final(id, "refactor the view model"))
        engine.emit(SpeechEvent.Final(id, "refactor the view model"))
        runCurrent()
        assertEquals(listOf("refactor the view model"), h.sink.commits)
        assertEquals(VoiceState.Idle(VoiceOutcome.Inserted(4, 0)), h.controller.state.value)
        assertEquals(listOf("listening", "finalizing", "committed"), h.records.single().transitions)
        assertEquals(1, h.controller.staleEventsIgnored)
    }

    @Test
    fun rejectedCommitIsRecordedAsCursorMovedCancel() = runTest {
        val (h, engine) = manual()
        h.controller.start()
        runCurrent()
        assertEquals(1, h.sink.starts)
        val id = h.controller.currentSessionId!!
        h.controller.stop()
        h.sink.accept = false
        engine.emit(SpeechEvent.Final(id, "run the tests"))
        runCurrent()
        assertTrue(h.sink.commits.isEmpty())
        assertEquals(
            VoiceState.Idle(VoiceOutcome.Cancelled(CancelReason.CURSOR_MOVED, VoicePhase.FINALIZING, 3)),
            h.controller.state.value,
        )
        assertEquals("cancelled (cursor moved)", h.records.single().transitions.last())
    }

    @Test
    fun cancelWhileListeningDiscardsProvisionalTextAndIgnoresLateEvents() = runTest {
        val (h, engine) = manual()
        h.controller.start()
        runCurrent()
        val id = h.controller.currentSessionId!!
        engine.emit(SpeechEvent.Partial(id, "explain what this kotlin"))
        runCurrent()

        val outcome = h.controller.cancel(CancelReason.SWITCHED_TO_KEYS)
        assertEquals(VoiceOutcome.Cancelled(CancelReason.SWITCHED_TO_KEYS, VoicePhase.LISTENING, 4), outcome)
        assertNull(h.sink.provisional)
        assertEquals(1, h.sink.clears)
        assertEquals(listOf(id), engine.cancelled)
        assertEquals(listOf("listening", "cancelled (switched to keys)"), h.records.single().transitions)

        // A late final from the cancelled session must never insert text.
        h.controller.deliver(SpeechEvent.Final(id, "explain what this kotlin coroutine"))
        assertTrue(h.sink.commits.isEmpty())
        assertEquals(1, h.controller.staleEventsIgnored)
        assertNull("cancel when idle does nothing", h.controller.cancel(CancelReason.KEYBOARD_HIDDEN))
    }

    @Test
    fun cancelWhileFinalizingNeverCommits() = runTest {
        val (h, engine) = manual()
        h.controller.start()
        runCurrent()
        val id = h.controller.currentSessionId!!
        engine.emit(SpeechEvent.Partial(id, "why does adb"))
        runCurrent()
        h.controller.stop()
        val outcome = h.controller.cancel(CancelReason.KEYBOARD_HIDDEN)
        assertEquals(VoicePhase.FINALIZING, outcome!!.phase)
        engine.emit(SpeechEvent.Final(id, "why does adb install fail"))
        runCurrent()
        assertTrue(h.sink.commits.isEmpty())
        assertEquals(listOf("listening", "finalizing", "cancelled (keyboard hidden)"), h.records.single().transitions)
    }

    @Test
    fun eventsFromAnOlderSessionAreRejected() = runTest {
        val (h, engine) = manual()
        h.controller.start()
        runCurrent()
        val first = h.controller.currentSessionId!!
        h.controller.cancel(CancelReason.SWITCHED_TO_KEYS)
        h.controller.start()
        runCurrent()
        val second = h.controller.currentSessionId!!
        assertTrue(second > first)

        h.controller.deliver(SpeechEvent.Partial(first, "stale words"))
        h.controller.deliver(SpeechEvent.Final(first, "stale words"))
        assertEquals(2, h.controller.staleEventsIgnored)
        assertTrue(h.sink.commits.isEmpty())
        assertEquals(second, (h.controller.state.value as VoiceState.Listening).sessionId)

        engine.emit(SpeechEvent.Partial(second, "fresh"))
        runCurrent()
        assertEquals("fresh", h.sink.provisional)
    }

    @Test
    fun blockingStatesWhenNotReady() = runTest {
        val (h, _) = manual()
        h.readiness = VoiceReadiness.NO_MODEL
        assertFalse(h.controller.start())
        assertEquals(VoiceState.NoModel, h.controller.state.value)
        assertFalse(h.controller.stop())

        h.readiness = VoiceReadiness.NO_PERMISSION
        h.controller.refreshReadiness()
        assertEquals(VoiceState.NoPermission, h.controller.state.value)

        h.readiness = VoiceReadiness.READY
        h.controller.refreshReadiness()
        assertEquals(VoiceState.Idle(), h.controller.state.value)
        assertTrue(h.controller.start())
    }

    @Test
    fun liveTextOffKeepsEditorUntouchedUntilFinal() = runTest {
        val (h, engine) = manual(VoiceConfig(liveText = false, autoCapitalize = false))
        h.controller.start()
        runCurrent()
        val id = h.controller.currentSessionId!!
        engine.emit(SpeechEvent.Partial(id, "run the"))
        runCurrent()
        assertNull(h.sink.provisional)
        h.controller.stop()
        engine.emit(SpeechEvent.Final(id, "run the tests"))
        runCurrent()
        assertEquals(listOf("run the tests"), h.sink.commits)
    }

    @Test
    fun fakeEngineScriptWithPhraseRulesAndAutoCapitalize() = runTest {
        val engine = ScriptedSpeechEngine()
        engine.nextPhraseIndex = 2 // "run the t3 code lint task and show me the errors"
        val rules = listOf(
            PhraseRule(1, "t3 code", "T3 Code"),
            PhraseRule(2, "view model", "ViewModel"),
        )
        val h = Harness(
            backgroundScope, this, engine,
            VoiceConfig(applyPhraseRules = true, autoCapitalize = true, rules = rules),
        )
        h.controller.start()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("run the t3 code lint task and show me the errors", h.sink.provisional)

        h.controller.stop()
        advanceTimeBy(engine.finalizeDelayMs + 50)
        runCurrent()
        assertEquals(listOf("Run the T3 Code lint task and show me the errors"), h.sink.commits)
        assertEquals(VoiceState.Idle(VoiceOutcome.Inserted(11, 1)), h.controller.state.value)
        val record = h.records.single()
        assertEquals(listOf("listening", "finalizing", "committed (1 rule)"), record.transitions)
        assertEquals(engine.finalizeDelayMs, record.finalizeLatencyMs)
    }

    @Test
    fun continuousModeStartsNextCaptureAfterCommit() = runTest {
        val engine = ScriptedSpeechEngine()
        val h = Harness(backgroundScope, this, engine, VoiceConfig(continuous = true, autoCapitalize = false))
        h.controller.start()
        advanceTimeBy(500)
        h.controller.stop()
        advanceTimeBy(engine.finalizeDelayMs + 50)
        runCurrent()
        assertEquals(1, h.sink.commits.size)
        val s = h.controller.state.value
        assertTrue(s is VoiceState.Listening && s.sessionId == 2L)
    }

    @Test
    fun autoStopAfterSilenceFinalizes() = runTest {
        val engine = ScriptedSpeechEngine()
        val h = Harness(backgroundScope, this, engine, VoiceConfig(autoStopMs = 2_000, autoCapitalize = false))
        h.controller.start()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, h.sink.commits.size)
        assertEquals(ScriptedSpeechEngine.SCRIPTED_PHRASES[0], h.sink.commits.single())
    }

    @Test
    fun autoStopWaitsForAudioSilenceNotForText() = runTest {
        val (h, engine) = manual(VoiceConfig(autoStopMs = 2_000, autoCapitalize = false))
        h.controller.start()
        runCurrent()
        val id = h.controller.currentSessionId!!
        // A final-only model: loud audio for 5 s, no partial text at all.
        repeat(50) {
            engine.emit(SpeechEvent.Level(id, 0.6f))
            advanceTimeBy(100)
            runCurrent()
        }
        assertTrue("still listening during speech", h.controller.state.value is VoiceState.Listening)
        // Then silence: stops about 2 s later.
        repeat(15) {
            engine.emit(SpeechEvent.Level(id, 0.01f))
            advanceTimeBy(100)
            runCurrent()
        }
        assertTrue(h.controller.state.value is VoiceState.Listening)
        repeat(8) {
            engine.emit(SpeechEvent.Level(id, 0.01f))
            advanceTimeBy(100)
            runCurrent()
        }
        assertTrue(h.controller.state.value is VoiceState.Finalizing)
        assertEquals(listOf(id), engine.stopped)
    }

    @Test
    fun aCaptureWithoutSpeechInsertsNothing() = runTest {
        val (h, engine) = manual()
        h.controller.start()
        runCurrent()
        val id = h.controller.currentSessionId!!
        repeat(20) { engine.emit(SpeechEvent.Level(id, 0.02f)) }
        runCurrent()
        h.controller.stop()
        engine.emit(SpeechEvent.Final(id, "You"))
        runCurrent()
        assertTrue(h.sink.commits.isEmpty())
        assertEquals(VoiceState.Idle(VoiceOutcome.NoSpeech), h.controller.state.value)
    }

    @Test
    fun wordsTheEditorKeptAreReportedAsLeftNotDiscarded() = runTest {
        val (h, engine) = manual()
        h.controller.start()
        runCurrent()
        val id = h.controller.currentSessionId!!
        engine.emit(SpeechEvent.Partial(id, "refactor the view model"))
        runCurrent()
        h.sink.editorKeepsText = true
        val outcome = h.controller.cancel(CancelReason.EDITOR_CHANGED)!!
        assertTrue(outcome.leftInEditor)
        assertEquals("Stopped · 4 words left in the field", VoiceNotes.outcome(outcome))
        assertEquals("cancelled (editor changed, words left in field)", h.records.single().transitions.last())
    }

    @Test
    fun openingPickerThenSwitchingModelRestartsCleanly() = runTest {
        val (h, engine) = manual()
        h.controller.start()
        runCurrent()
        val first = h.controller.currentSessionId!!
        engine.emit(SpeechEvent.Partial(first, "refactor the view model"))
        runCurrent()

        // Opening the picker cancels like switching to keys.
        val outcome = h.controller.cancel(CancelReason.MODEL_PICKER)!!
        assertEquals(4, outcome.wordsDiscarded)
        assertEquals("Cancelled · 4 words discarded", VoiceNotes.outcome(outcome))
        assertNull(h.sink.provisional)
        // Selecting a model: a second cancel is a no-op, then a fresh capture starts.
        assertNull(h.controller.cancel(CancelReason.MODEL_CHANGED))
        h.controller.clearOutcome()
        assertEquals(VoiceState.Idle(), h.controller.state.value)
        assertTrue(h.controller.start())
        runCurrent()
        val second = h.controller.currentSessionId!!
        assertTrue(second > first)

        // Late text from the old model's session never reaches the editor.
        h.controller.deliver(SpeechEvent.Final(first, "refactor the view model so"))
        assertTrue(h.sink.commits.isEmpty())
        assertEquals(listOf("listening", "cancelled (opened model picker)"), h.records.single().transitions)

        engine.emit(SpeechEvent.Partial(second, "run the"))
        runCurrent()
        h.controller.stop()
        engine.emit(SpeechEvent.Final(second, "run the lint task"))
        runCurrent()
        assertEquals(listOf("run the lint task"), h.sink.commits)
        assertEquals("4 words", VoiceNotes.outcome((h.controller.state.value as VoiceState.Idle).last!!))
    }

    @Test
    fun voiceNotesAreShort() {
        assertEquals("1 word", VoiceNotes.outcome(VoiceOutcome.Inserted(1, 0)))
        assertEquals("6 words · 2 rules", VoiceNotes.outcome(VoiceOutcome.Inserted(6, 2)))
        assertEquals("No speech", VoiceNotes.outcome(VoiceOutcome.NoSpeech))
        assertEquals("Cancelled", VoiceNotes.cancelled(0))
        assertEquals("Cancelled · 1 word discarded", VoiceNotes.cancelled(1))
        assertEquals("0:07", VoiceNotes.elapsed(7_900))
        assertEquals("1:05", VoiceNotes.elapsed(65_000))
        assertEquals("0:00", VoiceNotes.elapsed(-5))
    }
}
