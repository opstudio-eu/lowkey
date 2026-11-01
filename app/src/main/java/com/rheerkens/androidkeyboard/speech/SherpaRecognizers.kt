package com.rheerkens.androidkeyboard.speech

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.rheerkens.androidkeyboard.models.EngineSpec
import com.rheerkens.androidkeyboard.models.ModelInfo
import java.io.File

/**
 * Whisper [Recognizer]s on sherpa-onnx (Apache 2.0), built for speech recognition only by
 * scripts/build-sherpa-onnx.sh and linked to ONNX Runtime from Maven Central. Configuration
 * follows the sherpa-onnx Whisper exports: 16 kHz, greedy search, CPU.
 */
object SherpaRecognizers {
    private val threads: Int get() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    /** Silence after Stop for whole-clip models, so the last word isn't clipped. */
    private val TAIL_PADDING = FloatArray(SAMPLE_RATE / 5)

    /** Longest piece a whole-clip model decodes at once when it has no tighter limit. */
    const val DEFAULT_MAX_CHUNK_SECONDS = 60

    /**
     * Non-speech annotations some models (notably Whisper) emit for silence or noise:
     * `[BLANK_AUDIO]`, `[Music]`, `(dramatic music)`, `(applause)`, `*sigh*`.
     */
    private val SOUND_ANNOTATION = Regex(
        "\\s*(\\[[^\\]]{1,40}]|\\((?:[a-z]+ ){0,3}(?:music|applause|laughter|laughs|silence|noise|" +
            "static|inaudible|blank audio|sighs?|coughs?|beeps?|background noise)\\)|\\*[a-z ]{1,20}\\*)\\s*",
        RegexOption.IGNORE_CASE,
    )

    fun load(model: ModelInfo, dir: File, language: String?): Recognizer {
        fun path(name: String) = File(dir, name).absolutePath
        return when (val engine = model.engine) {
            is EngineSpec.SherpaWhisper -> WholeClipRecognizer(
                OfflineRecognizer(
                    assetManager = null,
                    config = OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0f),
                        modelConfig = OfflineModelConfig(
                            whisper = OfflineWhisperModelConfig(
                                encoder = path("${engine.prefix}-encoder.int8.onnx"),
                                decoder = path("${engine.prefix}-decoder.int8.onnx"),
                                // Empty lets multilingual Whisper detect the language.
                                language = when {
                                    engine.englishOnly -> "en"
                                    language == null || language == "auto" -> ""
                                    else -> language
                                },
                                task = "transcribe",
                            ),
                            tokens = path("${engine.prefix}-tokens.txt"),
                            numThreads = threads,
                            provider = "cpu",
                        ),
                    ),
                ),
                // Whisper reads at most 30 s at a time.
                maxChunkSamples = 28 * SAMPLE_RATE,
            )
        }
    }

    fun cleanText(text: String): String =
        SOUND_ANNOTATION.replace(text, " ").trim().replace(Regex("\\s+"), " ")

    /**
     * Final-only recognition. Audio is kept until [finish], except that whenever more than one
     * piece is buffered, the oldest piece (cut at a quiet point) is decoded right away. That keeps
     * memory bounded, spreads the work over the capture, and leaves at most one piece for Stop.
     */
    private class WholeClipRecognizer(private val recognizer: OfflineRecognizer, private val maxChunkSamples: Int) : Recognizer {
        private val audio = SampleBuffer()
        private val texts = mutableListOf<String>()
        private var onLine: ((Long, String) -> Unit)? = null

        override fun start(onLine: (Long, String) -> Unit, onError: (Throwable) -> Unit) {
            audio.clear()
            texts.clear()
            this.onLine = onLine
        }

        override fun addAudio(samples: FloatArray) {
            audio.append(samples)
            while (audio.size > maxChunkSamples + AudioChunker.SEARCH_SAMPLES) {
                val cut = AudioChunker.cutPoint(audio.view(), audio.size, maxChunkSamples)
                texts += decode(audio.view().copyOf(cut))
                audio.dropFront(cut)
                onLine?.invoke(0L, joined())
            }
        }

        override fun finish() {
            audio.append(TAIL_PADDING)
            AudioChunker.split(audio.toArray(), maxChunkSamples).forEach { texts += decode(it) }
            audio.clear()
            onLine?.invoke(0L, joined())
            onLine = null
        }

        private fun decode(piece: FloatArray): String {
            // Silent pieces make whole-clip models invent text; skip them.
            if (!AudioChunker.hasSpeech(piece)) return ""
            val s = recognizer.createStream()
            try {
                s.acceptWaveform(piece, SAMPLE_RATE)
                recognizer.decode(s)
                return cleanText(recognizer.getResult(s).text)
            } finally {
                s.release()
            }
        }

        private fun joined() = texts.filter { it.isNotBlank() }.joinToString(" ").trim()

        override fun abort() {
            audio.clear()
            texts.clear()
            onLine = null
        }

        override fun setKeyterms(terms: List<String>) = Unit

        override fun close() {
            abort()
            recognizer.release()
        }
    }
}

/** Growable float buffer for whole-clip models (about 3.8 MB per minute of audio). */
class SampleBuffer {
    private var data = FloatArray(SAMPLE_RATE * 10)
    var size = 0
        private set

    fun append(samples: FloatArray) {
        if (size + samples.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + samples.size))
        System.arraycopy(samples, 0, data, size, samples.size)
        size += samples.size
    }

    fun toArray(): FloatArray = data.copyOf(size)

    /** The backing array; only the first [size] samples are valid. */
    fun view(): FloatArray = data

    /** Removes the first [n] samples. */
    fun dropFront(n: Int) {
        val count = n.coerceIn(0, size)
        System.arraycopy(data, count, data, 0, size - count)
        size -= count
    }

    fun clear() {
        size = 0
        if (data.size > SAMPLE_RATE * 120) data = FloatArray(SAMPLE_RATE * 10)
    }
}
