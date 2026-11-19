package com.rheerkens.androidkeyboard.ai

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Everything one request needs, read from Settings and the secret store when the user taps Create. */
data class ImageJob(
    val endpoint: String,
    /** Sent as `Authorization: Bearer …`; null for an own server without a token. */
    val token: String?,
    /** Null leaves `model` out, for a server that picks its own. */
    val model: String?,
    val quality: ImageQuality?,
    val prompt: String,
)

/**
 * Sends one prompt to the Image API and returns the image. Blocking: call on an IO dispatcher.
 * [cancel] drops the connection, which ends a blocking read; callers still drop late results
 * themselves.
 */
class ImageGenerator(
    private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) {
    /** Guards [active] and [generation]: a cancel and a request registering a connection never interleave. */
    private val lock = Any()
    private var active: HttpURLConnection? = null
    /** Counts cancels; a request started before the latest one opens no further connection. */
    private var generation = 0

    /**
     * Set by [cancel] and never cleared: the keyboard uses one generator per image, so a Stop that comes
     * before the request has even started still keeps it from being sent.
     */
    private var cancelled = false

    /** Stops the request in progress, if any. The service may still have made (and billed) the image. */
    fun cancel() {
        val connection = synchronized(lock) {
            cancelled = true
            generation++
            active.also { active = null }
        }
        connection?.disconnect()
    }

    /** Connections this thread opened, so a late finish can't forget a newer request's connection. */
    private val opened = ThreadLocal<MutableList<HttpURLConnection>>()
    private val started = ThreadLocal<Int>()

    /** Throws if [cancel] ran since this thread's request started. */
    private fun ensureNotCancelled() = synchronized(lock) {
        if (cancelled || started.get() != generation) throw IOException("Cancelled")
    }

    /** Opening only creates the connection object; nothing is sent until it is used. */
    private fun connect(url: String): HttpURLConnection = synchronized(lock) {
        if (cancelled || started.get() != generation) throw IOException("Cancelled")
        open(url).also { active = it; opened.get()?.add(it) }
    }

    fun generate(job: ImageJob): ImageReply = try {
        opened.set(mutableListOf())
        started.set(synchronized(lock) { generation })
        val reply = post(job)
        if (reply is ImageReply.Link) download(reply.url) else reply
    } catch (_: SocketTimeoutException) {
        ImageReply.Failed("The image service took too long")
    } catch (_: UnknownHostException) {
        ImageReply.Failed("Can't reach the image service. Check the address and your connection")
    } catch (_: SSLException) {
        ImageReply.Failed("The image service's secure connection failed")
    } catch (e: IOException) {
        ImageReply.Failed(when {
            e is TooLarge -> "The image is too large"
            e.message?.contains("Cleartext", ignoreCase = true) == true -> "Use https:// for your server"
            else -> "Can't reach the image service"
        })
    } catch (_: SecurityException) {
        ImageReply.Failed("Lowkey isn't allowed to use the internet")
    } catch (_: IllegalArgumentException) {
        // A malformed link in the answer, or an address the platform refuses.
        ImageReply.Failed("The image service sent a bad link")
    } finally {
        synchronized(lock) { if (opened.get()?.any { it === active } == true) active = null }
        opened.remove()
        started.remove()
    }

    private class TooLarge : IOException("Response too large")

    private fun post(job: ImageJob): ImageReply {
        val connection = connect(job.endpoint)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            job.token?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer ${it.trim()}") }
            val body = ImageApi.requestBody(job.prompt, job.model, job.quality).toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            // disconnect() does nothing before the connection has started, so a Stop just before it is
            // checked here; one while it starts is caught after, before the prompt (the body) is sent.
            ensureNotCancelled()
            connection.outputStream.use { out ->
                ensureNotCancelled()
                out.write(body)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { readLimited(it, MAX_RESPONSE_BYTES) }?.toString(Charsets.UTF_8) ?: ""
            return ImageApi.parse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    /** An own server's image link; only over HTTPS, and only an image. The token isn't sent along. */
    private fun download(url: String): ImageReply {
        val connection = connect(url)
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            ensureNotCancelled()
            if (connection.responseCode !in 200..299) return ImageReply.Failed("Couldn't download the image")
            val bytes = connection.inputStream.use { readLimited(it, ImageApi.MAX_IMAGE_BYTES) }
            val mime = ImageApi.sniffMime(bytes) ?: return ImageReply.Failed("The service sent something that isn't an image")
            return ImageReply.Image(bytes, mime)
        } catch (e: IOException) {
            if (e is TooLarge) throw e
            return ImageReply.Failed("Couldn't download the image")
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > max) throw TooLarge()
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        /** Large images at high quality, or an agent behind an own server, can take minutes. */
        private const val READ_TIMEOUT_MS = 240_000
        /** Base64 makes the JSON about a third larger than the image, which the history caps. */
        private const val MAX_RESPONSE_BYTES = ImageApi.MAX_IMAGE_BYTES / 3 * 4 + 64 * 1024
    }
}
