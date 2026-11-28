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
    /** Pictures added to the prompt: sent as a multipart upload to `images/edits` ([endpoint]). */
    val images: List<InputImage> = emptyList(),
)

/** Improve in the prompt window: the prompt to rewrite, and where. */
data class PromptJob(
    val endpoint: String,
    val token: String?,
    val model: String?,
    /** OpenAI itself, which takes [PromptApi]'s OpenAI-only options. */
    val openAi: Boolean,
    val prompt: String,
    /** How many pictures go with the prompt; the pictures themselves aren't sent to rewrite it. */
    val imageCount: Int,
)

/**
 * Sends one request to the AI service: an image for a prompt ([generate]) or a better prompt
 * ([improve]). Blocking: call on an IO dispatcher. [cancel] drops the connection, which ends a
 * blocking read; callers still drop late results themselves.
 */
class AiClient(
    private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) {
    /** Guards [active] and [generation]: a cancel and a request registering a connection never interleave. */
    private val lock = Any()
    private var active: HttpURLConnection? = null
    /** Counts cancels; a request started before the latest one opens no further connection. */
    private var generation = 0

    /**
     * Set by [cancel] and never cleared: the keyboard uses one client per request, so a Stop that comes
     * before the request has even started still keeps it from being sent.
     */
    private var cancelled = false

    /** Stops the request in progress, if any. The service may still have made (and billed) the result. */
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

    fun generate(job: ImageJob): ImageReply = request({ ImageReply.Failed(failure(it, "image service")) }) {
        val reply = if (job.images.isEmpty()) {
            val body = ImageApi.requestBody(job.prompt, job.model, job.quality).toByteArray(Charsets.UTF_8)
            post(job.endpoint, job.token, "application/json", body, MAX_RESPONSE_BYTES, READ_TIMEOUT_MS, ImageApi::parse)
        } else {
            val boundary = "lowkey-" + java.util.UUID.randomUUID().toString()
            val body = ImageApi.editBody(boundary, job.prompt, job.model, job.quality, job.images)
            post(job.endpoint, job.token, "multipart/form-data; boundary=$boundary", body, MAX_RESPONSE_BYTES, READ_TIMEOUT_MS,
                ImageApi::parse)
        }
        if (reply is ImageReply.Link) download(reply.url) else reply
    }

    /**
     * One call to the provider's safety check. Used only for OpenAI. A failure is a [ModerationReply.Failed],
     * which holds the content back; this does not retry.
     */
    fun moderate(endpoint: String, token: String?, body: String): ModerationReply =
        request({ ModerationReply.Failed(failure(it, "safety check")) }) {
            post(endpoint, token, "application/json", body.toByteArray(Charsets.UTF_8),
                MAX_MODERATION_RESPONSE, MODERATION_TIMEOUT_MS, ModerationApi::parse)
        }

    /** A rewritten prompt for [job]'s, from the service's text model. */
    fun improve(job: PromptJob): PromptReply = request({ PromptReply.Failed(failure(it, "AI service")) }) {
        val body = PromptApi.requestBody(job.prompt, job.model, job.openAi, job.imageCount).toByteArray(Charsets.UTF_8)
        post(job.endpoint, job.token, "application/json", body, MAX_PROMPT_RESPONSE_BYTES, PROMPT_READ_TIMEOUT_MS, PromptApi::parse)
    }

    /** Runs [send] for this thread's request, turning a failed connection into [failed]'s reply. */
    private inline fun <R> request(failed: (Exception) -> R, send: () -> R): R = try {
        opened.set(mutableListOf())
        started.set(synchronized(lock) { generation })
        send()
    } catch (e: IOException) {
        failed(e)
    } catch (e: SecurityException) {
        failed(e)
    } catch (e: IllegalArgumentException) {
        failed(e)
    } finally {
        synchronized(lock) { if (opened.get()?.any { it === active } == true) active = null }
        opened.remove()
        started.remove()
    }

    /** What went wrong with the connection to the [service], for the user. */
    private fun failure(e: Exception, service: String): String = when (e) {
        is SocketTimeoutException -> "The $service took too long"
        is UnknownHostException -> "Can't reach the $service. Check the address and your connection"
        is SSLException -> "The $service's secure connection failed"
        is TooLarge -> if (service == "image service") "The image is too large" else "The $service sent too much"
        is IOException ->
            if (e.message?.contains("Cleartext", ignoreCase = true) == true) "Use https:// for your server" else "Can't reach the $service"
        is SecurityException -> "Lowkey isn't allowed to use the internet"
        // A malformed link in the answer, or an address the platform refuses.
        else -> "The $service sent a bad link"
    }

    private class TooLarge : IOException("Response too large")

    private fun <R> post(endpoint: String, token: String?, contentType: String, body: ByteArray, maxResponse: Int,
        readTimeout: Int, parse: (Int, String) -> R): R {
        val connection = connect(endpoint)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = readTimeout
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("Accept", "application/json")
            token?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Authorization", "Bearer ${it.trim()}") }
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
            val text = stream?.use { readLimited(it, maxResponse) }?.toString(Charsets.UTF_8) ?: ""
            return parse(code, text)
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
        /** A moderation answer is a short JSON object, not an image. */
        private const val MAX_MODERATION_RESPONSE = 256 * 1024
        private const val MODERATION_TIMEOUT_MS = 30_000
        /** A short text answer; longer means the text model hangs. */
        private const val PROMPT_READ_TIMEOUT_MS = 60_000
        /** A rewritten prompt is a few hundred bytes; the rest of a chat answer is small too. */
        private const val MAX_PROMPT_RESPONSE_BYTES = 256 * 1024
    }
}
