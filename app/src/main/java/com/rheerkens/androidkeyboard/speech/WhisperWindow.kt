package com.rheerkens.androidkeyboard.speech

/**
 * Re-decodes the current audio window after speech resumes, allowing Whisper to revise the
 * preview with audio on both sides of a pause. Only a cut at the model limit freezes text.
 */
internal class WhisperWindow(private val maxSamples: Int, private val decode: (FloatArray) -> String) {
    private val audio = SampleBuffer()
    private val completed = mutableListOf<String>()
    private var preview = ""
    private var previewEnd = 0
    private var attemptedEnd = 0

    fun addAudio(samples: FloatArray): String? {
        audio.append(samples)
        var cutAtLimit = false
        while (audio.size > maxSamples) {
            val cut = if (previewEnd > 0 && previewEnd >= maxSamples - AudioChunker.SEARCH_SAMPLES) {
                previewEnd
            } else {
                AudioChunker.cutPoint(audio.view(), audio.size, maxSamples)
            }
            completed += if (cut == previewEnd) preview else decodeIfSpoken(cut)
            audio.dropFront(cut)
            preview = ""
            previewEnd = 0
            attemptedEnd = 0
            cutAtLimit = true
        }

        val pause = AudioChunker.pauseCut(audio.view(), audio.size)
        if (pause != null) {
            if (AudioChunker.hasSpeech(audio.view(), attemptedEnd, pause)) {
                attemptedEnd = pause
                val text = decode(audio.view().copyOf(pause))
                if (text.isNotBlank()) {
                    preview = text
                    previewEnd = pause
                    return transcript(preview)
                }
            } else if (attemptedEnd == 0) {
                audio.dropFront(pause)
            } else if (attemptedEnd == previewEnd) {
                // Keep a natural gap across the boundary, without filling the window with silence.
                audio.truncate(previewEnd + AudioChunker.PAUSE_SAMPLES / 2)
            }
        }
        return if (cutAtLimit) transcript(preview) else null
    }

    fun finish(): String {
        val tail = if (AudioChunker.hasSpeech(audio.view(), previewEnd, audio.size)) {
            val paddedLength = minOf(maxSamples, audio.size + SAMPLE_RATE / 5)
            val padded = audio.view().copyOf(paddedLength)
            padded.fill(0f, fromIndex = audio.size)
            decode(padded).ifBlank { preview }
        } else {
            preview
        }
        return transcript(tail).also { clear() }
    }

    fun clear() {
        audio.clear()
        completed.clear()
        preview = ""
        previewEnd = 0
        attemptedEnd = 0
    }

    private fun decodeIfSpoken(length: Int): String =
        if (AudioChunker.hasSpeech(audio.view(), length = length)) decode(audio.view().copyOf(length)) else ""

    private fun transcript(tail: String): String =
        (completed + tail).filter { it.isNotBlank() }.joinToString(" ")
}
