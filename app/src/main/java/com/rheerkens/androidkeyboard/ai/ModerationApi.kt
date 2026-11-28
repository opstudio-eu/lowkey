package com.rheerkens.androidkeyboard.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

/** What the provider's safety check returned. No network here. */
sealed interface ModerationReply {
    /** [categories] are the ones the provider marked, such as `sexual/minors`. */
    data class Result(val flagged: Boolean, val categories: Set<String>) : ModerationReply
    data class Failed(val message: String, val refused: Boolean = false) : ModerationReply
}

/**
 * OpenAI's moderation endpoint (`POST …/moderations`), which reads text and images. Called with the
 * user's own key, and only for OpenAI itself. Another server is not asked: a missing endpoint would
 * otherwise hold back every result.
 */
object ModerationApi {
    const val PATH = "moderations"
    const val MODEL = "omni-moderation-latest"
    /** Larger than this is not sent to the check; the image is held back instead. */
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")

    fun textBody(text: String): String = JSONObject()
        .put("model", MODEL)
        .put("input", text.take(ImageApi.MAX_PROMPT_CHARS))
        .toString()

    /** The image, and [text] when the service rewrote the prompt, in one check. */
    fun imageBody(bytes: ByteArray, mime: String, text: String?): String {
        val input = JSONArray()
        val caption = text?.trim()?.takeIf { it.isNotEmpty() }?.take(ImageApi.MAX_PROMPT_CHARS)
        if (caption != null) input.put(JSONObject().put("type", "text").put("text", caption))
        val url = "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
        input.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url)))
        return JSONObject().put("model", MODEL).put("input", input).toString()
    }

    fun parse(code: Int, body: String): ModerationReply {
        val json = try { JSONObject(body) } catch (_: JSONException) { null }
        if (code !in 200..299) {
            return ModerationReply.Failed(
                AiConnection.errorMessage(code, json?.optJSONObject("error")?.opt("message") as? String),
                refused = code == 401,
            )
        }
        val results = json?.optJSONArray("results") ?: return ModerationReply.Failed("The safety check sent an unreadable answer")
        if (results.length() == 0) return ModerationReply.Failed("The safety check sent an unreadable answer")
        var flagged = false
        val categories = linkedSetOf<String>()
        for (i in 0 until results.length()) {
            val result = results.optJSONObject(i) ?: return ModerationReply.Failed("The safety check sent an unreadable answer")
            if (!result.has("flagged")) return ModerationReply.Failed("The safety check sent an unreadable answer")
            if (!result.optBoolean("flagged", false)) continue
            flagged = true
            val map = result.optJSONObject("categories")
            if (map != null) {
                val keys = map.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (map.optBoolean(key, false)) categories.add(key)
                }
            }
        }
        return ModerationReply.Result(flagged, categories)
    }
}
