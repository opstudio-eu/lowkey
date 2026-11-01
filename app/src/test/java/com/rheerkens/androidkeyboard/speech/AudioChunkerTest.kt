package com.rheerkens.androidkeyboard.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioChunkerTest {

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
