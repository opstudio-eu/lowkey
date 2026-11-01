package com.rheerkens.androidkeyboard.speech

import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.settings.ModelRetention
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
class LocalSpeechEngineTest {

    /** Each audio chunk's first sample encodes a word index; the recognizer "hears" that word. */
    private class FakeRecognizer(
        private val words: List<String>,
        private val failStart: Boolean = false,
        private val onKeyterms: () -> Unit = {},
    ) : Recognizer {
        var onLine: ((Long, String) -> Unit)? = null
        val heard = mutableListOf<String>()
        var finished = 0
        var aborted = 0
        var closed = false
        var open = false
        val misuse = mutableListOf<String>()
        var terms: List<String> = emptyList()

        override fun start(onLine: (Long, String) -> Unit, onError: (Throwable) -> Unit) {
            if (failStart) throw IllegalStateException("start failed")
            this.onLine = onLine
            heard.clear()
            open = true
        }
        override fun addAudio(samples: FloatArray) {
            if (!open || closed) misuse += "addAudio on closed stream"
            heard += words[samples[0].toInt()]
            onLine?.invoke(1L, heard.joinToString(" "))
        }
        override fun finish() {
            if (!open || closed) misuse += "finish on closed stream"
            finished++
            open = false
            onLine?.invoke(1L, heard.joinToString(" ") + ".")
        }
        override fun abort() { aborted++; open = false }
        override fun setKeyterms(terms: List<String>) { onKeyterms(); this.terms = terms }
        override fun close() {
            if (open) misuse += "close with open stream"
            closed = true
        }
    }

    private class Harness(scope: TestScope) {
        val audio = Channel<FloatArray>(Channel.UNLIMITED)
        val recognizers = mutableListOf<FakeRecognizer>()
        var language: String? = null
        var failStart = false
        /** The crash-detection marker, as written by the engine. */
        var marker: String? = null
        var markerAtKeyterms: String? = null
        val engine = LocalSpeechEngine(
            audioSource = { AudioSource { audio.receiveAsFlow() } },
            activeModel = { LoadableModel(ModelCatalog.models.first(), File("unused"), language) },
            keyterms = { listOf("T3 Code") },
            factory = { _, _, _ ->
                FakeRecognizer(listOf("run", "the", "tests"), failStart) { markerAtKeyterms = marker }.also { recognizers += it }
            },
            native = StandardTestDispatcher(scope.testScheduler),
            engineWork = { marker = it },
            nowMillis = { scope.testScheduler.currentTime },
        )
        fun say(index: Int) = audio.trySend(floatArrayOf(index.toFloat()))
    }

    @Test
    fun audioBeforeLoadIsKeptPartialsStreamAndStopFinalizesOnce() = runTest {
        val h = Harness(this)
        val events = mutableListOf<SpeechEvent>()
        h.say(0) // spoken while the model is still loading
        val job = launch { h.engine.start(7).collect { events += it } }
        runCurrent()
        assertTrue(events.contains(SpeechEvent.Loading(7, true)))
        assertTrue(events.contains(SpeechEvent.Loading(7, false)))
        h.say(1); h.say(2); runCurrent()
        val partials = events.filterIsInstance<SpeechEvent.Partial>().map { it.text }
        assertEquals("run the tests", partials.last())
        assertEquals(listOf("T3 Code"), h.recognizers.single().terms)
        h.engine.stop(7); h.engine.stop(7); runCurrent()
        assertEquals(listOf(SpeechEvent.Final(7, "run the tests.")), events.filterIsInstance<SpeechEvent.Final>())
        assertEquals(1, h.recognizers.single().finished)
        assertEquals(0, h.recognizers.single().aborted)
        assertFalse(job.isActive)
        assertTrue(events.all { it.sessionId == 7L })
    }

    @Test
    fun nativeWorkIsMarkedUntilTheStreamCloses() = runTest {
        val h = Harness(this)
        val id = ModelCatalog.models.first().id
        val job = launch { h.engine.start(3).collect {} }
        runCurrent()
        assertEquals("loading $id", h.markerAtKeyterms)
        assertEquals("running $id", h.marker)
        h.engine.stop(3); runCurrent()
        assertFalse(job.isActive)
        assertEquals(null, h.marker)
    }

    @Test
    fun failedStreamStartClearsTheMarker() = runTest {
        val h = Harness(this)
        h.failStart = true
        val events = mutableListOf<SpeechEvent>()
        val job = launch { runCatching { h.engine.start(4).collect { events += it } } }
        runCurrent()
        assertFalse(job.isActive)
        assertTrue(events.none { it is SpeechEvent.Final })
        assertEquals(null, h.marker)
    }

    @Test
    fun cancellingCollectionAbortsWithoutFinal() = runTest {
        val h = Harness(this)
        val events = mutableListOf<SpeechEvent>()
        val job = launch { h.engine.start(1).collect { events += it } }
        h.say(0); runCurrent()
        h.engine.cancel(1)
        job.cancel(); runCurrent()
        assertTrue(events.none { it is SpeechEvent.Final })
        assertEquals(1, h.recognizers.single().aborted)
        assertEquals(0, h.recognizers.single().finished)
    }

    @Test
    fun modelStaysWarmBetweenSessionsUntilUnloaded() = runTest {
        val h = Harness(this)
        val first = launch { h.engine.start(1).collect {} }
        runCurrent()
        h.engine.stop(1); runCurrent()
        assertTrue(first.isCompleted)
        assertTrue(h.engine.isWarm)
        val events = mutableListOf<SpeechEvent>()
        val second = launch { h.engine.start(2).collect { events += it } }
        runCurrent()
        assertTrue(events.none { it is SpeechEvent.Loading })
        h.engine.stop(2); runCurrent()
        assertTrue(second.isCompleted)
        assertEquals(1, h.recognizers.size)
        h.engine.unload(); runCurrent()
        assertTrue(h.recognizers.single().closed)
        assertFalse(h.engine.isWarm)
    }

    @Test
    fun stopBeforeCollectionStartsIsKept() = runTest {
        val h = Harness(this)
        val flow = h.engine.start(3)
        h.engine.stop(3)
        val events = mutableListOf<SpeechEvent>()
        val job = launch { flow.collect { events += it } }
        runCurrent()
        assertFalse(job.isActive)
        assertEquals(1, events.filterIsInstance<SpeechEvent.Final>().size)
    }

    @Test
    fun stopEndsCaptureSoLaterAudioIsNotTranscribed() = runTest {
        val h = Harness(this)
        h.say(0)
        val events = mutableListOf<SpeechEvent>()
        val job = launch { h.engine.start(4).collect { events += it } }
        runCurrent()
        h.engine.stop(4)
        h.say(1); h.say(2) // arrives after Stop
        runCurrent()
        assertFalse(job.isActive)
        assertEquals(listOf(SpeechEvent.Final(4, "run.")), events.filterIsInstance<SpeechEvent.Final>())
    }

    @Test
    fun cleanupOfACancelledSessionNeverAbortsTheNextOne() = runTest {
        val h = Harness(this)
        val first = launch { h.engine.start(1).collect {} }
        runCurrent()
        // Cancel session 1 and start session 2 before any queued work runs.
        h.engine.cancel(1)
        first.cancel()
        val events = mutableListOf<SpeechEvent>()
        val second = launch { h.engine.start(2).collect { events += it } }
        runCurrent()
        h.say(1); h.say(2); runCurrent()
        h.engine.stop(2); runCurrent()
        assertFalse(second.isActive)
        assertEquals(listOf(SpeechEvent.Final(2, "the tests.")), events.filterIsInstance<SpeechEvent.Final>())
        assertEquals(emptyList<String>(), h.recognizers.single().misuse)
    }

    @Test
    fun unloadWhileStreamingIsDeferredUntilTheSessionEnds() = runTest {
        val h = Harness(this)
        val events = mutableListOf<SpeechEvent>()
        val job = launch { h.engine.start(1).collect { events += it } }
        runCurrent()
        h.engine.unload(); runCurrent()
        assertFalse(h.recognizers.single().closed)
        h.say(0); runCurrent()
        h.engine.stop(1); runCurrent()
        assertFalse(job.isActive)
        assertEquals(listOf(SpeechEvent.Final(1, "run.")), events.filterIsInstance<SpeechEvent.Final>())
        assertTrue(h.recognizers.single().closed)
        assertEquals(emptyList<String>(), h.recognizers.single().misuse)
    }

    @Test
    fun changingTheLanguageReloadsTheModel() = runTest {
        val h = Harness(this)
        h.language = "en"
        launch { h.engine.start(1).collect {} }
        runCurrent(); h.engine.stop(1); runCurrent()
        assertTrue(h.engine.isWarm)
        h.language = "nl"
        assertFalse(h.engine.isWarm)
        launch { h.engine.start(2).collect {} }
        runCurrent(); h.engine.stop(2); runCurrent()
        assertEquals(2, h.recognizers.size)
        assertTrue(h.recognizers.first().closed)
    }

    @Test
    fun defaultRetentionExpiresTwoMinutesAfterDictationWithoutHidingKeyboard() = runTest {
        val h = Harness(this)
        launch { h.engine.start(1).collect {} }
        runCurrent(); h.engine.stop(1); runCurrent()
        advanceTimeBy(119_999); runCurrent()
        assertTrue(h.engine.isWarm)
        advanceTimeBy(1); runCurrent()
        assertFalse(h.engine.isWarm)
        assertTrue(h.recognizers.single().closed)
        assertEquals(ModelMemoryState(), h.engine.memory.value)
    }

    @Test
    fun newCaptureCancelsTheOldDeadlineAndGetsItsOwnIdleTime() = runTest {
        val h = Harness(this)
        h.engine.setRetention(ModelRetention.THIRTY_SECONDS)
        launch { h.engine.start(1).collect {} }
        runCurrent(); h.engine.stop(1); runCurrent()
        advanceTimeBy(20_000)
        launch { h.engine.start(2).collect {} }; runCurrent()
        advanceTimeBy(40_000); runCurrent()
        assertTrue(h.engine.memory.value.inUse)
        assertFalse(h.recognizers.single().closed)
        h.say(0); runCurrent(); h.engine.stop(2); runCurrent()
        advanceTimeBy(29_999); runCurrent()
        assertTrue(h.engine.isWarm)
        advanceTimeBy(1); runCurrent()
        assertFalse(h.engine.isWarm)
        assertEquals(emptyList<String>(), h.recognizers.single().misuse)
    }

    @Test
    fun immediateReleaseWaitsForTranscriptionAndKeepsFinalText() = runTest {
        val h = Harness(this)
        h.engine.setRetention(ModelRetention.IMMEDIATE)
        val events = mutableListOf<SpeechEvent>()
        launch { h.engine.start(1).collect { events += it } }; runCurrent()
        assertTrue(h.engine.isWarm)
        assertTrue(h.engine.memory.value.inUse)
        h.say(0); runCurrent(); h.engine.stop(1); runCurrent()
        assertEquals(listOf(SpeechEvent.Final(1, "run.")), events.filterIsInstance<SpeechEvent.Final>())
        assertTrue(h.recognizers.single().closed)
        assertEquals(emptyList<String>(), h.recognizers.single().misuse)
    }

    @Test
    fun shorteningRetentionUsesElapsedIdleTimeRatherThanRestartingIt() = runTest {
        val h = Harness(this)
        h.engine.setRetention(ModelRetention.UNTIL_CLOSED)
        launch { h.engine.start(1).collect {} }
        runCurrent(); h.engine.stop(1); runCurrent()
        advanceTimeBy(300_000); runCurrent()
        assertTrue(h.engine.isWarm)
        h.engine.setRetention(ModelRetention.THIRTY_SECONDS); runCurrent()
        assertFalse(h.engine.isWarm)
    }

    @Test
    fun extendingRetentionCancelsTheEarlierRelease() = runTest {
        val h = Harness(this)
        launch { h.engine.start(1).collect {} }
        runCurrent(); h.engine.stop(1); runCurrent()
        advanceTimeBy(60_000)
        h.engine.setRetention(ModelRetention.FIVE_MINUTES); runCurrent()
        advanceTimeBy(239_999); runCurrent()
        assertTrue(h.engine.isWarm)
        advanceTimeBy(1); runCurrent()
        assertFalse(h.engine.isWarm)
    }

    @Test
    fun cancellationReleasesImmediatelyAndAPressureUnloadOverridesKeepLoaded() = runTest {
        val h = Harness(this)
        h.engine.setRetention(ModelRetention.IMMEDIATE)
        val first = launch { h.engine.start(1).collect {} }; runCurrent()
        h.engine.cancel(1); first.cancel(); runCurrent()
        assertTrue(h.recognizers.single().closed)
        h.engine.setRetention(ModelRetention.UNTIL_CLOSED)
        launch { h.engine.start(2).collect {} }; runCurrent()
        h.engine.unload(); runCurrent()
        assertFalse(h.recognizers.last().closed)
        h.engine.stop(2); runCurrent()
        assertTrue(h.recognizers.last().closed)
        assertEquals(emptyList<String>(), h.recognizers.last().misuse)
    }

    @Test
    fun aLaggingModelReportsItsBacklog() = runTest {
        val h = Harness(this)
        repeat(30) { h.say(0) } // 3 s of audio waiting before the model runs
        val events = mutableListOf<SpeechEvent>()
        launch { h.engine.start(9).collect { events += it } }
        runCurrent()
        val backlog = events.filterIsInstance<SpeechEvent.Backlog>().map { it.seconds }
        assertTrue("backlog $backlog", backlog.isNotEmpty() && backlog.first() >= 2f)
        assertEquals(0f, backlog.last())
        h.engine.stop(9); runCurrent()
    }

    @Test
    fun levelReflectsLoudness() {
        assertEquals(0f, levelOf(FloatArray(10)))
        assertEquals(1f, levelOf(FloatArray(10) { 0.9f }))
    }

    @Test
    fun decodesPcm16MonoWav() {
        val samples = shortArrayOf(0, 16384, -32768)
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        val wav = wav(SAMPLE_RATE, 1, data.array())
        val decoded = ClipAudioSource.decodeWav(ByteArrayInputStream(wav))
        assertEquals(listOf(0f, 0.5f, -1f), decoded.toList())
    }

    @Test(expected = java.io.IOException::class)
    fun rejectsWrongSampleRate() {
        ClipAudioSource.decodeWav(ByteArrayInputStream(wav(44_100, 1, ByteArray(4))))
    }

    private fun wav(rate: Int, channels: Int, pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(n: Int, bytes: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array().copyOf(bytes)
        out.write("RIFF".toByteArray()); out.write(le(36 + pcm.size, 4)); out.write("WAVE".toByteArray())
        out.write("LIST".toByteArray()); out.write(le(4, 4)); out.write("INFO".toByteArray())
        out.write("fmt ".toByteArray()); out.write(le(16, 4)); out.write(le(1, 2)); out.write(le(channels, 2))
        out.write(le(rate, 4)); out.write(le(rate * channels * 2, 4)); out.write(le(channels * 2, 2)); out.write(le(16, 2))
        out.write("data".toByteArray()); out.write(le(pcm.size, 4)); out.write(pcm)
        return out.toByteArray()
    }
}
