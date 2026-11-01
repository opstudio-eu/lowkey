package com.rheerkens.androidkeyboard.speech

import com.rheerkens.androidkeyboard.models.ModelInfo
import java.io.File

/**
 * A loaded speech model. Not thread-safe: [LocalSpeechEngine] calls it from one thread.
 * Line callbacks arrive synchronously during [addAudio] and [finish].
 */
interface Recognizer {
    /** Begins a stream. [onLine] receives each line's ID and its latest text. */
    fun start(onLine: (id: Long, text: String) -> Unit, onError: (Throwable) -> Unit)
    fun addAudio(samples: FloatArray)

    /** Ends the stream and flushes the remaining audio; final line text arrives before it returns. */
    fun finish()

    /** Ends the stream without needing its output. */
    fun abort()

    /** Words and phrases the model should favor. Unsupported runtimes may ignore this. */
    fun setKeyterms(terms: List<String>)
    fun close()
}

fun interface RecognizerFactory {
    /** Loads [model] from [dir]. [language] is a code from the model's list, `auto`, or null. */
    fun load(model: ModelInfo, dir: File, language: String?): Recognizer
}

/** Loads every model on sherpa-onnx (the catalog only has Whisper models). */
object EngineRecognizerFactory : RecognizerFactory {
    override fun load(model: ModelInfo, dir: File, language: String?): Recognizer =
        SherpaRecognizers.load(model, dir, language)
}
