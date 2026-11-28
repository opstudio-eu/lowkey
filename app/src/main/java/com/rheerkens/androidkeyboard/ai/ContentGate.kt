package com.rheerkens.androidkeyboard.ai

/**
 * Checks a prompt or a result before it is sent, shown or copied. The on-device list runs first.
 * OpenAI is also asked (with the user's key); if that call fails, the content is held back.
 * [moderate] performs one moderation request and does not retry.
 */
class ContentGate(
    private val moderate: (endpoint: String, token: String?, body: String) -> ModerationReply,
) {
    fun checkText(text: String, endpoint: String, token: String?, strict: Boolean, stage: SafetyStage): SafetyDecision {
        LocalBlocklist.check(text, strict, stage)?.let { return it }
        if (!ContentSafety.usesProviderModeration(endpoint)) return SafetyDecision.Allow
        val reply = moderate(ContentSafety.moderationsEndpoint(endpoint), token, ModerationApi.textBody(text))
        return ContentSafety.fromModeration(reply, strict, stage)
    }

    /**
     * A finished image. [revisedPrompt] is checked as text when the service rewrote the prompt.
     * Servers other than OpenAI are not asked to look at the pixels; the prompt was already checked.
     */
    fun checkImage(
        bytes: ByteArray, mime: String, revisedPrompt: String?, endpoint: String, token: String?, strict: Boolean,
    ): SafetyDecision {
        if (!revisedPrompt.isNullOrBlank()) {
            LocalBlocklist.check(revisedPrompt, strict, SafetyStage.OUTPUT)?.let { return it }
        }
        if (!ContentSafety.usesProviderModeration(endpoint)) return SafetyDecision.Allow
        if (mime !in ModerationApi.IMAGE_TYPES) {
            return SafetyDecision.Blocked(
                ContentSafety.keptBack(SafetyStage.OUTPUT, "The image isn't a kind of picture the safety check accepts"),
                failedClosed = true,
            )
        }
        if (bytes.size > ModerationApi.MAX_IMAGE_BYTES) {
            return SafetyDecision.Blocked(
                ContentSafety.keptBack(SafetyStage.OUTPUT, "This image is too large to check for safety"),
                failedClosed = true,
            )
        }
        val reply = moderate(
            ContentSafety.moderationsEndpoint(endpoint), token, ModerationApi.imageBody(bytes, mime, revisedPrompt),
        )
        return ContentSafety.fromModeration(reply, strict, SafetyStage.OUTPUT)
    }
}

/** A held-back prompt, for the prompt window's note. A refused key keeps the existing wording. */
fun SafetyDecision.Blocked.asPromptFailure(): PromptReply = if (refused) {
    PromptReply.Failed(message, refused = true)
} else {
    PromptReply.Failed(message, safety = true)
}
