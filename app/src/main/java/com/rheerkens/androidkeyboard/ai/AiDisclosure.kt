package com.rheerkens.androidkeyboard.ai

/** Create sends the pictures. Improve sends only how many there are. */
enum class AiSendKind { CREATE, IMPROVE }

/** The words shown before the first send to a service, and again from Settings. */
data class AiSendNotice(val provider: String, val lines: List<String>)

/**
 * What the first request to a service tells the user, and the value remembered once they continue.
 * One acceptance is for one service: OpenAI, or one address of the user's own server.
 */
object AiDisclosure {
    const val TITLE = "Before this is sent"

    fun acceptanceKey(service: AiService, serverUrl: String): String = when (service) {
        AiService.OPENAI -> "openai"
        AiService.OWN_SERVER -> "own:" + (AiConnection.apiBase(service, serverUrl, allowLocalHttp = false).url ?: serverUrl.trim())
    }

    fun accepted(saved: String, service: AiService, serverUrl: String): Boolean =
        saved.isNotBlank() && saved == acceptanceKey(service, serverUrl)

    fun notice(kind: AiSendKind, pictureCount: Int, service: AiService, serverHost: String): AiSendNotice {
        val count = pictureCount.coerceAtLeast(0)
        val pictures = when {
            count == 0 -> "No pictures are attached."
            kind == AiSendKind.CREATE && count == 1 -> "1 attached picture is sent with the prompt."
            kind == AiSendKind.CREATE -> "$count attached pictures are sent with the prompt."
            count == 1 -> "1 picture is attached. Improve sends that count, not the picture."
            else -> "$count pictures are attached. Improve sends that count, not the pictures."
        }
        val link = if (kind == AiSendKind.CREATE) {
            "If the service answers with a link to the image instead of the image, Lowkey downloads that follow-up link."
        } else {
            "Improve does not download a follow-up image link."
        }
        val safety = if (service == AiService.OPENAI) {
            "With OpenAI, the prompt and a finished image also go to OpenAI's safety check, using your API key."
        } else {
            "Your own server is not asked to look at the finished picture. An on-device list checks the words."
        }
        return AiSendNotice(
            provider = providerLine(service, serverHost),
            lines = listOf(
                "AI on the keyboard is experimental.",
                "The prompt is sent.",
                pictures,
                link,
                "Your IP address is sent with the request. The service can see it.",
                safety,
            ),
        )
    }

    /** The same facts, for Settings, without a particular request's picture count. */
    fun review(service: AiService, serverHost: String, accepted: Boolean): String = buildString {
        append(providerLine(service, serverHost))
        append(" A request can include the prompt, the pictures you attach and how many, ")
        append("a follow-up image link when the service answers with one instead of the image, ")
        append("and your IP address, which the service sees. ")
        append("Improve sends the prompt and the picture count, not the pictures. ")
        if (service == AiService.OPENAI) {
            append("The prompt and a finished image also go to OpenAI's safety check, using your API key. ")
        }
        append(if (accepted) "You continued past this for the service above." else "Lowkey asks before the first send to this service.")
    }

    private fun providerLine(service: AiService, serverHost: String): String = when (service) {
        AiService.OPENAI -> "This goes to OpenAI (api.openai.com), the service you chose."
        AiService.OWN_SERVER ->
            "This goes to your server at ${serverHost.ifBlank { "the address in Settings" }}, the service you chose."
    }
}
