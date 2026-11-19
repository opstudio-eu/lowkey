package com.rheerkens.androidkeyboard.ai

import java.net.URI
import java.net.URISyntaxException

/** Where the keyboard's AI actions go (Settings › Experimental). One connection serves every action. */
enum class AiService(val label: String) {
    /** OpenAI's API, with the user's own API key. */
    OPENAI("OpenAI"),
    /** A server the user runs that speaks OpenAI's API, such as an agent on a private network. */
    OWN_SERVER("Your own server"),
}

/** An API base URL, or why there is none ([problem], for the user). */
data class ApiBase(val url: String?, val problem: String?) {
    /** The endpoint for [path] under this base, such as `images/generations`. */
    fun endpoint(path: String): String? = url?.let { "$it/$path" }
}

/**
 * The connection the AI actions share: OpenAI, or the user's own OpenAI-compatible server, and how
 * a failed request reads to the user. Each action adds its own path ([AiAction.path]). No Android
 * or network code here, so it is tested on the JVM.
 */
object AiConnection {
    const val OPENAI_BASE = "https://api.openai.com/v1"

    /**
     * The API base for [service]. An own server's address may be the server (`/v1` is added), its
     * `/v1` base as SDKs take it, or the full endpoint of an action, which is cut back to its base.
     * It needs HTTPS (a private network's HTTPS name works, such as one from `tailscale serve`);
     * [allowLocalHttp] lets debug builds test against a server on the same device.
     */
    fun apiBase(service: AiService, serverUrl: String, allowLocalHttp: Boolean = false): ApiBase {
        if (service == AiService.OPENAI) return ApiBase(OPENAI_BASE, null)
        val text = serverUrl.trim()
        if (text.isEmpty()) return ApiBase(null, "Add your server's address")
        val uri = try { URI(text) } catch (_: URISyntaxException) { return ApiBase(null, "That isn't a web address") }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if (host.isNullOrEmpty() || (scheme != "https" && scheme != "http")) {
            return ApiBase(null, "Use a full address, like https://ai.example.net")
        }
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            // The address is a setting, so it goes into backups; secrets belong in Access token.
            return ApiBase(null, "Leave passwords and ?options out; put a token in Access token")
        }
        if (uri.port > 65_535) return ApiBase(null, "That port number doesn't exist")
        if (scheme == "http" && !(allowLocalHttp && host in LOCAL_HOSTS)) {
            return ApiBase(null, "Use https://. Lowkey doesn't send prompts over plain http")
        }
        val path = (uri.rawPath ?: "").trimEnd('/')
        val actionPath = AiAction.entries.map { "/" + it.path }.firstOrNull { path.endsWith(it) }
        val base = when {
            actionPath != null -> path.removeSuffix(actionPath)
            path.endsWith("/v1") -> path
            else -> "$path/v1"
        }
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return ApiBase("$scheme://${hostForUrl(host)}$port$base", null)
    }

    private fun hostForUrl(host: String) = if (':' in host && !host.startsWith("[")) "[$host]" else host

    /** Hosts a debug build may reach over plain http: this device (`adb reverse` forwards a port to it). */
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

    /** A short, plain message for a failed request, with the service's own reason when it gave one. */
    fun errorMessage(code: Int, serviceMessage: String?): String {
        val reason = serviceMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { one(it) }
        return when (code) {
            401 -> "The API key or token was refused"
            403 -> reason?.let { "Not allowed: $it" } ?: "This key may not use this model"
            404 -> "Nothing found at that address"
            429 -> reason?.let { "Limit reached: $it" } ?: "Too many requests or no credit left"
            400 -> reason?.let { "Not done: $it" } ?: "The service didn't accept the request"
            in 500..599 -> "The AI service had a problem ($code). Try again"
            else -> reason ?: "The AI service answered $code"
        }
    }

    /** The first sentence of a service message, cut to fit the keyboard's strip. */
    private fun one(text: String): String {
        val sentence = text.substringBefore(". ").trimEnd('.')
        return if (sentence.length <= 90) sentence else sentence.take(89).trimEnd() + "…"
    }
}

/**
 * What the chosen service still needs before it can run anything. The composer's Create (and Enter)
 * becomes [action], which opens Settings › Experimental at the row titled [setting], ready to edit.
 */
enum class AiSetup(
    /** After the service's name on the defaults chip: "OpenAI · key needed". */
    val chip: String,
    /** The options panel row that stands in for the model. */
    val row: String,
    val value: String,
    val action: String,
    /** The settings row to open, by its title. */
    val setting: String,
) {
    NO_KEY("key needed", "API key", "Not set", "Add key", "OpenAI API key"),
    NO_SERVER("address needed", "Server", "No address yet", "Add server", "Server address"),
    BAD_SERVER("check address", "Server", "That address doesn't work", "Fix server", "Server address"),
    /** The service turned the saved key down (401); it stays so until the key changes. */
    BAD_KEY("key refused", "API key", "Refused", "Fix key", "OpenAI API key"),
    BAD_TOKEN("token refused", "Token", "Refused", "Fix token", "Access token"),
}
