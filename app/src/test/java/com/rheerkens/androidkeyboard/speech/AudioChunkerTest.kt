package com.rheerkens.androidkeyboard.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioChunkerTest {

    @Test
    fun whisperCanReviseTheEndOfAPreviewAfterSpeechResumes() {
        val inputs = mutableListOf<FloatArray>()
        val window = WhisperWindow(28 * SAMPLE_RATE) { samples ->
            inputs += samples
            if (samples.any { it == 0.4f }) "I think we should go." else "I think."
        }
        assertEquals("I think.", window.addAudio(audio(2.5 to 0.3f, 0.7 to 0f)))
        assertEquals(null, window.addAudio(audio(8.0 to 0f)))
        assertEquals("I think we should go.", window.addAudio(audio(1.0 to 0.4f, 0.7 to 0f)))
        assertEquals("I think we should go.", window.finish())
        assertEquals(2, inputs.size)
        assertTrue(inputs.last().any { it == 0.3f })
        assertTrue(inputs.last().size < 5 * SAMPLE_RATE)
    }

    @Test
    fun shortAudioStaysWhole() {
        val pieces = AudioChunker.split(FloatArray(SAMPLE_RATE * 10) { 0.3f }, maxSamples = SAMPLE_RATE * 30)
        assertEquals(1, pieces.size)
    }

    @Test
    fun longAudioIsCutAtTheQuietestWindowBeforeTheLimit() {
        val total = SAMPLE_RATE * 70
        val quietAt = SAMPLE_RATE * 27 // a pause 3 s before the 30 s limit
        val samples = FloatArray(total) { i -> if (i in quietAt until quietAt + CHUNK_SAMPLES) 0f else 0.5f }
        val pieces = AudioChunker.split(samples, maxSamples = SAMPLE_RATE * 30)
        assertTrue(pieces.size >= 3)
        assertTrue(pieces.all { it.size <= SAMPLE_RATE * 30 })
        assertEquals(total, pieces.sumOf { it.size })
        val firstCut = pieces[0].size
        assertTrue("cut at $firstCut", firstCut in quietAt until quietAt + CHUNK_SAMPLES + 1)
    }

    private fun audio(vararg parts: Pair<Double, Float>): FloatArray =
        parts.flatMap { (seconds, level) -> List((seconds * SAMPLE_RATE).toInt()) { i -> if (i % 2 == 0) level else -level } }.toFloatArray()

    @Test
    fun aPauseAfterEnoughSpeechEndsAPieceInItsMiddle() {
        val samples = audio(2.5 to 0.3f, 0.7 to 0f)
        val cut = AudioChunker.pauseCut(samples, samples.size)
        assertEquals(samples.size - AudioChunker.PAUSE_SAMPLES / 2, cut)
    }

    @Test
    fun aGapBetweenWordsOrTooLittleAudioIsNoPause() {
        // 0.4 s between words is not a pause.
        val gap = audio(2.5 to 0.3f, 0.4 to 0f)
        assertEquals(null, AudioChunker.pauseCut(gap, gap.size))
        // Still speaking.
        val speaking = audio(1.0 to 0.3f, 1.0 to 0f, 2.0 to 0.3f)
        assertEquals(null, AudioChunker.pauseCut(speaking, speaking.size))
        // A short word and a pause, before the piece is long enough.
        val short = audio(0.5 to 0.3f, 0.8 to 0f)
        assertEquals(null, AudioChunker.pauseCut(short, short.size))
    }

    @Test
    fun aQuietPieceIsReportedSoItCanBeDropped() {
        val quiet = audio(3.5 to 0.002f)
        val cut = AudioChunker.pauseCut(quiet, quiet.size)!!
        assertTrue(!AudioChunker.hasSpeech(quiet.copyOf(cut)))
    }

    @Test
    fun whitespaceIsCollapsed() {
        assertEquals("run the tests", SherpaRecognizers.cleanText("  run  the tests "))
    }

    @Test
    fun sampleBufferGrowsAndClears() {
        val b = SampleBuffer()
        repeat(200) { b.append(FloatArray(CHUNK_SAMPLES) { 1f }) }
        assertEquals(200 * CHUNK_SAMPLES, b.size)
        assertEquals(200 * CHUNK_SAMPLES, b.toArray().size)
        b.clear()
        assertEquals(0, b.size)
    }

    @Test
    fun incrementalCutsMatchTheWholeSplitAndBufferDropsFront() {
        val samples = FloatArray(SAMPLE_RATE * 40) { i -> if (i in SAMPLE_RATE * 27 until SAMPLE_RATE * 27 + CHUNK_SAMPLES) 0f else 0.4f }
        val b = SampleBuffer()
        b.append(samples)
        val cut = AudioChunker.cutPoint(b.view(), b.size, SAMPLE_RATE * 30)
        assertEquals(AudioChunker.split(samples, SAMPLE_RATE * 30).first().size, cut)
        b.dropFront(cut)
        assertEquals(samples.size - cut, b.size)
        assertEquals(0.4f, b.toArray().last())
    }

    @Test
    fun silenceHasNoSpeechAndSoundAnnotationsAreStripped() {
        assertTrue(!AudioChunker.hasSpeech(FloatArray(SAMPLE_RATE * 30)))
        assertTrue(!AudioChunker.hasSpeech(FloatArray(SAMPLE_RATE) { 0.003f }))
        assertTrue(AudioChunker.hasSpeech(FloatArray(SAMPLE_RATE) { if (it in 8000 until 9600) 0.2f else 0f }))
        assertEquals(
            "run the tests",
            SherpaRecognizers.cleanText("run the tests (dramatic music) (dramatic music) [BLANK_AUDIO]"),
        )
        assertEquals("keep (this) text", SherpaRecognizers.cleanText("keep (this) text"))
    }
}
