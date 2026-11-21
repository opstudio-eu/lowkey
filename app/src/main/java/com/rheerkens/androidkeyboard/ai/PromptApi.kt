package com.rheerkens.androidkeyboard.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What Improve got back: a better prompt, or a message for the user. */
sealed interface PromptReply {
    data class Text(val prompt: String) : PromptReply
    /** [refused]: the API key or token was turned down (401). */
    data class Failed(val message: String, val refused: Boolean = false) : PromptReply
}

/**
 * Improve in the prompt window: OpenAI's Chat Completions API (`POST …/chat/completions`), which
 * OpenAI-compatible servers speak too, asked to rewrite the prompt for the image model. No Android
 * or network code here, so it is tested on the JVM.
 */
object PromptApi {
    /** OpenAI's small, fast text model; an own server uses [com.rheerkens.androidkeyboard.settings.Settings.promptServerModel]. */
    const val OPENAI_MODEL = "gpt-5.6-luna"

    /** Enough for the rewritten prompt, which [INSTRUCTIONS] keeps short. */
    private const val MAX_TOKENS = 400

    private const val INSTRUCTIONS =
        "You improve prompts for an image generator. Rewrite the user's prompt so it describes a clearer, more vivid " +
            "picture: name the subject, setting, style, lighting and composition where the prompt leaves them open. " +
            "Keep everything the user asked for. Keep words meant to appear in the image exactly, in quotes. Write in " +
            "the language of the prompt. Reply with the new prompt only: one paragraph of at most 70 words, without " +
            "quotation marks around it and without any explanation."

    private fun picturesNote(count: Int) =
        " The user also adds ${if (count == 1) "a picture" else "$count pictures"} for the generator to change or " +
            "combine, which you can't see. Write the prompt as an instruction about " +
            "${if (count == 1) "that picture" else "those pictures"}, and keep what the user says about it."

    /**
     * The JSON request for [prompt]. [openAi] adds what only OpenAI's own models take (no reasoning
     * pass, for a quick answer, and a length cap); [imageCount] pictures go along with the prompt.
     */
    fun requestBody(prompt: String, model: String?, openAi: Boolean, imageCount: Int): String {
        val system = INSTRUCTIONS + if (imageCount > 0) picturesNote(imageCount) else ""
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", prompt.take(ImageApi.MAX_PROMPT_CHARS)))
        return JSONObject()
            .apply { if (model != null) put("model", model) }
            .put("messages", messages)
            .apply {
                if (openAi) {
                    put("reasoning_effort", "none")
                    put("max_completion_tokens", MAX_TOKENS)
                }
            }
            .toString()
    }

    /** Reads the answer: [code] is the HTTP status, [body] the response text. */
    fun parse(code: Int, body: String): PromptReply {
        val json = try { JSONObject(body) } catch (_: JSONException) { null }
        if (code !in 200..299) {
            return PromptReply.Failed(AiConnection.errorMessage(code, json?.optJSONObject("error")?.opt("message") as? String),
                refused = code == 401)
        }
        val message = json?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        val text = when (val content = message?.opt("content")) {
            is String -> content
            // Some servers answer with parts: [{"type": "text", "text": "…"}].
            is JSONArray -> (0 until content.length()).mapNotNull { content.optJSONObject(it)?.opt("text") as? String }.joinToString("")
            else -> null
        }
        val prompt = text?.let(::clean)?.takeIf { it.isNotEmpty() }
            ?: return PromptReply.Failed("The AI service sent no prompt")
        return PromptReply.Text(prompt)
    }

    /** One line for the prompt box, without quotation marks around it and within its length limit. */
    internal fun clean(text: String): String {
        var s = text.replace(Regex("\\s+"), " ").trim()
        val pairs = listOf("\"" to "\"", "“" to "”", "'" to "'", "«" to "»")
        pairs.firstOrNull { (open, close) -> s.length >= 2 && s.startsWith(open) && s.endsWith(close) }
            ?.let { (open, close) -> s = s.removePrefix(open).removeSuffix(close).trim() }
        return s.take(ImageApi.MAX_PROMPT_CHARS).trim()
    }
}
