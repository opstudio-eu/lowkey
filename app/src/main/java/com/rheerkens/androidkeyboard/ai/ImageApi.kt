package com.rheerkens.androidkeyboard.ai

import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

/** What a finished request gave: an image, an image to download, or a message for the user. */
sealed interface ImageReply {
    /** [revisedPrompt]: the words the service actually used, when it rewrote the prompt. */
    class Image(val bytes: ByteArray, val mimeType: String, val revisedPrompt: String? = null) : ImageReply
    /** A server that answers with a link instead of the image; the link must be HTTPS. */
    data class Link(val url: String) : ImageReply
    /**
     * [refused]: the API key or token was turned down (401), so trying again won't help until it changes.
     * [safety]: the provider refused the request for safety; the message is already the one to show.
     */
    data class Failed(val message: String, val refused: Boolean = false, val safety: Boolean = false) : ImageReply
}

/** A picture sent along with the prompt ([ImageApi.editBody]): already shrunk and encoded. */
class InputImage(val bytes: ByteArray, val mimeType: String)

/**
 * The OpenAI Image API (`POST …/images/generations`, [AiAction.CREATE_IMAGE], and `…/images/edits`
 * with input images, [AiAction.EDIT_IMAGE]), which "your own server" speaks as well: the request
 * body and how to read the answer. No Android or network code here, so it is tested on the JVM.
 */
object ImageApi {
    /** Most pictures sent with one prompt; the API takes up to 16, a keyboard prompt needs a few. */
    const val MAX_INPUT_IMAGES = 4
    /** Longest prompt sent; the API's own limit is far higher, a keyboard prompt is short. */
    const val MAX_PROMPT_CHARS = 2_000
    /** Largest image kept: the clipboard history's limit for an image. */
    const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

    /** The JSON request body: one image, as base64 (the only form newer models return). */
    fun requestBody(prompt: String, model: String?, quality: ImageQuality?): String =
        JSONObject()
            .apply { if (model != null) put("model", model) }
            .put("prompt", prompt.take(MAX_PROMPT_CHARS))
            .put("n", 1)
            .apply { if (quality != null) put("quality", quality.api) }
            .toString()

    /**
     * The `multipart/form-data` body for `images/edits`, split by [boundary]: the same fields as
     * [requestBody], then each picture as a file. One picture goes as `image`, several as `image[]`,
     * as OpenAI's own SDKs send them.
     */
    fun editBody(boundary: String, prompt: String, model: String?, quality: ImageQuality?, images: List<InputImage>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        fun field(name: String, value: String) {
            text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
        }
        if (model != null) field("model", model)
        field("prompt", prompt.take(MAX_PROMPT_CHARS))
        field("n", "1")
        if (quality != null) field("quality", quality.api)
        val name = if (images.size == 1) "image" else "image[]"
        images.take(MAX_INPUT_IMAGES).forEachIndexed { i, image ->
            val ext = image.mimeType.substringAfter('/').replace("jpeg", "jpg")
            text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"image-${i + 1}.$ext\"\r\n" +
                "Content-Type: ${image.mimeType}\r\n\r\n")
            out.write(image.bytes)
            text("\r\n")
        }
        text("--$boundary--\r\n")
        return out.toByteArray()
    }

    /** Reads the answer to a request: [code] is the HTTP status, [body] the response text. */
    fun parse(code: Int, body: String): ImageReply {
        val json = try { JSONObject(body) } catch (_: JSONException) { null }
        // opt(…) as? String: Android's optString turns a JSON null into the text "null".
        if (code !in 200..299) {
            val error = json?.optJSONObject("error")
            if (ContentSafety.isProviderSafetyRefusal(code, error)) {
                return ImageReply.Failed(ContentSafety.PROVIDER_REFUSAL, safety = true)
            }
            return ImageReply.Failed(AiConnection.errorMessage(code, error?.opt("message") as? String), refused = code == 401)
        }
        val first = json?.optJSONArray("data")?.optJSONObject(0)
            ?: return ImageReply.Failed("The service sent no image")
        val b64 = (first.opt("b64_json") as? String)?.takeIf { it.isNotEmpty() }
        if (b64 != null) {
            if (b64.length / 4 * 3 > MAX_IMAGE_BYTES) return ImageReply.Failed("The image is too large")
            val bytes = try { Base64.getMimeDecoder().decode(b64) } catch (_: IllegalArgumentException) { null }
                ?: return ImageReply.Failed("The service sent an unreadable image")
            val mime = sniffMime(bytes) ?: return ImageReply.Failed("The service sent something that isn't an image")
            return ImageReply.Image(bytes, mime, revisedPrompt(first))
        }
        val url = (first.opt("url") as? String)?.takeIf { it.startsWith("https://") }
        return if (url != null) ImageReply.Link(url) else ImageReply.Failed("The service sent no image")
    }

    /** The rewritten prompt, when the service sent one, trimmed to the length a prompt may be. */
    private fun revisedPrompt(item: JSONObject): String? =
        (item.opt("revised_prompt") as? String)?.trim()?.take(MAX_PROMPT_CHARS)?.takeIf { it.isNotEmpty() }

    /** The image type from the file's first bytes; null when it isn't PNG, JPEG, WebP or GIF. */
    fun sniffMime(bytes: ByteArray): String? {
        fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png"
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            at(0) == 'R'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == 'F'.code &&
                at(8) == 'W'.code && at(9) == 'E'.code && at(10) == 'B'.code && at(11) == 'P'.code -> "image/webp"
            at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code -> "image/gif"
            else -> null
        }
    }
}
