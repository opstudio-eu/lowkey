package com.rheerkens.androidkeyboard.test

import android.content.Context
import android.os.SystemClock
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.speech.ClipAudioSource
import com.rheerkens.androidkeyboard.speech.SAMPLE_RATE
import com.rheerkens.androidkeyboard.speech.SherpaRecognizers

/** Compares separate decoding against a revisable window, using the app's actual native model. */
object WhisperContextProbe {
    @JvmStatic fun run(context: Context): String {
        AppGraph.init(context)
        val model = checkNotNull(ModelCatalog.byId("whisper-tiny.en"))
        val dir = checkNotNull(AppGraph.models.modelDir(model.id)) { "Install Whisper tiny.en first" }
        fun clip(id: String): FloatArray =
            context.assets.open("test-clips/clip-$id.wav").use(ClipAudioSource::decodeWav)

        val firstClip = clip("01")
        val split = (3.55 * SAMPLE_RATE).toInt()
        val pause = FloatArray(SAMPLE_RATE)
        val cases = listOf(
            Triple("mid-sentence", firstClip.copyOfRange(0, split) + pause, firstClip.copyOfRange(split, firstClip.size)),
            Triple("between sentences", firstClip, clip("02")),
        )
        val recognizer = SherpaRecognizers.load(model, dir, "en")
        try {
            var text = ""
            fun start() = recognizer.start({ _, line -> text = line }, { throw it })
            fun standalone(samples: FloatArray): String {
                start()
                recognizer.addAudio(samples)
                recognizer.finish()
                return text
            }
            standalone(firstClip) // Warm the model before timing.
            return buildString {
                appendLine("probe=whisper-context")
                for ((name, first, second) in cases) {
                    val firstText = standalone(first)
                    val before = SystemClock.elapsedRealtime()
                    val secondText = standalone(second)
                    val separateMs = SystemClock.elapsedRealtime() - before

                    start()
                    recognizer.addAudio(first)
                    val preview = text
                    val after = SystemClock.elapsedRealtime()
                    recognizer.addAudio(second)
                    recognizer.finish()
                    val contextualMs = SystemClock.elapsedRealtime() - after
                    check(preview.isNotBlank() && text.isNotBlank()) { "$name produced no preview or final text" }
                    appendLine("$name | separate ($separateMs ms): $firstText $secondText")
                    appendLine("$name | preview: $preview")
                    appendLine("$name | context ($contextualMs ms): $text")
                }
            }
        } finally {
            recognizer.close()
        }
    }
}
