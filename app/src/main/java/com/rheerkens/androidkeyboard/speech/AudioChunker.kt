package com.rheerkens.androidkeyboard.speech

/**
 * Splits a recording into pieces no longer than a model's input limit (Whisper ~30 s,
 * Cohere 35 s). Each cut lands in the quietest 100 ms window of the last [searchSamples]
 * before the limit, so words are rarely cut in half. Pure Kotlin.
 */
object AudioChunker {
    private const val WINDOW = CHUNK_SAMPLES // 100 ms

    /** How far before the limit a cut may move to find a pause: 5 s. */
    const val SEARCH_SAMPLES = 5 * SAMPLE_RATE

    fun split(samples: FloatArray, maxSamples: Int, searchSamples: Int = SEARCH_SAMPLES): List<FloatArray> {
        require(maxSamples > WINDOW)
        val pieces = mutableListOf<FloatArray>()
        var start = 0
        while (samples.size - start > maxSamples) {
            val end = cutPoint(samples, start, maxSamples, searchSamples)
            pieces += samples.copyOfRange(start, end)
            start = end
        }
        if (start < samples.size) pieces += samples.copyOfRange(start, samples.size)
        return pieces
    }

    /** True if any 100 ms window of [samples] is louder than near-silence (RMS above 0.01). */
    fun hasSpeech(samples: FloatArray): Boolean {
        var w = 0
        while (w < samples.size) {
            val end = minOf(w + WINDOW, samples.size)
            var energy = 0.0
            for (i in w until end) energy += samples[i] * samples[i]
            if (energy / (end - w) > SPEECH_RMS * SPEECH_RMS) return true
            w = end
        }
        return false
    }

    private const val SPEECH_RMS = 0.01

    /** Quiet that ends a spoken piece: 0.7 s, longer than the gaps between words. */
    const val PAUSE_SAMPLES = 7 * WINDOW

    /** Shortest piece decoded at a pause, the pause included: shorter ones give Whisper too little to go on. */
    const val MIN_PAUSE_PIECE = 3 * SAMPLE_RATE

    /**
     * Where to end the first piece of the first [length] samples at a pause, or null: when the
     * last [PAUSE_SAMPLES] are quiet and the piece is at least [MIN_PAUSE_PIECE] long, the
     * middle of that pause. Checks only the pause itself, so it is cheap for every new chunk.
     * The piece may still be all quiet; [hasSpeech] tells.
     */
    fun pauseCut(samples: FloatArray, length: Int): Int? {
        if (length < MIN_PAUSE_PIECE) return null
        var w = length - PAUSE_SAMPLES
        while (w < length) {
            val end = minOf(w + WINDOW, length)
            var energy = 0.0
            for (i in w until end) energy += samples[i] * samples[i]
            if (energy / (end - w) > SPEECH_RMS * SPEECH_RMS) return null
            w = end
        }
        return length - PAUSE_SAMPLES / 2
    }

    /** Where to end the first piece of the first [length] samples (requires `length > maxSamples`). */
    fun cutPoint(samples: FloatArray, length: Int, maxSamples: Int): Int {
        require(length > maxSamples && maxSamples > WINDOW)
        return cutPoint(samples, 0, maxSamples, SEARCH_SAMPLES)
    }

    /** Midpoint of the quietest window in `[limit - search, limit)`, relative to the whole array. */
    private fun cutPoint(samples: FloatArray, start: Int, maxSamples: Int, searchSamples: Int): Int {
        val limit = start + maxSamples
        val searchStart = maxOf(start + WINDOW, limit - searchSamples)
        var best = limit
        var bestEnergy = Double.MAX_VALUE
        var w = searchStart
        while (w + WINDOW <= limit) {
            var energy = 0.0
            for (i in w until w + WINDOW) energy += samples[i] * samples[i]
            if (energy < bestEnergy) {
                bestEnergy = energy
                best = w + WINDOW / 2
            }
            w += WINDOW / 2
        }
        return best
    }
}
