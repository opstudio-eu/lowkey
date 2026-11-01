package com.rheerkens.androidkeyboard.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

const val SAMPLE_RATE = 16_000

/** Number of samples per chunk: 100 ms at [SAMPLE_RATE]. */
const val CHUNK_SAMPLES = SAMPLE_RATE / 10

/** Mono float audio at [SAMPLE_RATE]. Collecting starts capture; cancelling collection stops it. */
fun interface AudioSource {
    fun chunks(): Flow<FloatArray>
}

/** Captures the microphone with Android's speech-tuned input. */
class MicAudioSource(private val context: Context) : AudioSource {
    override fun chunks(): Flow<FloatArray> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Microphone permission not granted")
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) throw IOException("Microphone unavailable ($minBuffer)")
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            max(minBuffer, CHUNK_SAMPLES * 4 * Float.SIZE_BYTES),
        )
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IOException("Microphone unavailable")
            record.startRecording()
            val buffer = FloatArray(CHUNK_SAMPLES)
            while (currentCoroutineContext().isActive) {
                val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (n < 0) throw IOException("Microphone read failed ($n)")
                if (n > 0) emit(buffer.copyOf(n))
            }
        } finally {
            if (record.state == AudioRecord.STATE_INITIALIZED) runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Debug audio input for emulators and repeatable demos: plays bundled 16 kHz mono PCM16 WAV
 * clips in real time, one clip per capture, followed by silence until the capture stops.
 */
class ClipAudioSource(
    private val clips: List<String>,
    private val open: (String) -> InputStream,
    private val nextIndex: () -> Int,
) : AudioSource {
    override fun chunks(): Flow<FloatArray> = flow {
        val samples = open(clips[Math.floorMod(nextIndex(), clips.size)]).use(::decodeWav)
        var offset = 0
        while (currentCoroutineContext().isActive) {
            val chunk = FloatArray(CHUNK_SAMPLES)
            val n = minOf(CHUNK_SAMPLES, samples.size - offset).coerceAtLeast(0)
            if (n > 0) System.arraycopy(samples, offset, chunk, 0, n)
            offset += n
            emit(chunk)
            delay(CHUNK_SAMPLES * 1000L / SAMPLE_RATE)
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /** Decodes a canonical PCM16 mono WAV at [SAMPLE_RATE] into floats in -1..1. */
        fun decodeWav(input: InputStream): FloatArray {
            val bytes = input.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") {
                throw IOException("Not a WAV file")
            }
            var pos = 12
            var channels = 0
            var rate = 0
            var bits = 0
            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4)
                val size = buf.getInt(pos + 4)
                val body = pos + 8
                when (id) {
                    "fmt " -> {
                        channels = buf.getShort(body + 2).toInt()
                        rate = buf.getInt(body + 4)
                        bits = buf.getShort(body + 14).toInt()
                    }
                    "data" -> {
                        if (channels != 1 || rate != SAMPLE_RATE || bits != 16) {
                            throw IOException("Need 16 kHz mono PCM16, got $rate Hz, $channels ch, $bits bit")
                        }
                        val count = minOf(size, bytes.size - body) / 2
                        return FloatArray(count) { buf.getShort(body + it * 2) / 32768f }
                    }
                }
                pos = body + size + (size and 1)
            }
            throw IOException("WAV has no data chunk")
        }
    }
}

/** Root-mean-square level of [samples], scaled so normal speech fills most of 0..1. */
fun levelOf(samples: FloatArray): Float {
    if (samples.isEmpty()) return 0f
    var sum = 0.0
    for (s in samples) sum += s * s
    return (sqrt(sum / samples.size).toFloat() * 6f).coerceIn(0f, 1f)
}
