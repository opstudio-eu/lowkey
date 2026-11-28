package com.rheerkens.androidkeyboard.ai

import org.json.JSONObject

/** Whether the words are going out to the service, or a result is about to be shown. */
enum class SafetyStage { INPUT, OUTPUT }

/**
 * Outcome of a safety check. [Blocked.failedClosed] means the check did not finish, so the content
 * is held back rather than shown. [Blocked.refused] means the API key or token was turned down.
 */
sealed interface SafetyDecision {
    data object Allow : SafetyDecision
    data class Blocked(
        val message: String,
        val failedClosed: Boolean = false,
        val refused: Boolean = false,
    ) : SafetyDecision
}

/**
 * How strict the checks are. Sexual content involving a child is always blocked.
 * [PLAY_STORE_BUILD] is the switch a Play build should force on (there is no Play product flavor
 * in this tree yet): while it is true, [strict] ignores a saved "off" and the settings switch is hidden.
 */
object ContentSafetyPolicy {
    val PLAY_STORE_BUILD: Boolean = false

    fun strict(setting: Boolean, playStoreBuild: Boolean = PLAY_STORE_BUILD): Boolean = playStoreBuild || setting

    fun allowsRelaxing(playStoreBuild: Boolean = PLAY_STORE_BUILD): Boolean = !playStoreBuild
}

/**
 * Messages and the decision for a provider safety check. No network here.
 * OpenAI's moderation endpoint is used only for `api.openai.com`; another server has no such call.
 */
object ContentSafety {
    const val PROVIDER_REFUSAL = "The AI service refused this for safety. Nothing was kept."

    fun usesProviderModeration(endpoint: String): Boolean {
        val base = AiConnection.OPENAI_BASE
        return endpoint == base || endpoint.startsWith("$base/")
    }

    /** The moderation URL for an images or chat endpoint under the same `/v1` base. */
    fun moderationsEndpoint(apiEndpoint: String): String {
        val trimmed = apiEndpoint.trimEnd('/')
        val base = when {
            "/v1/" in trimmed -> trimmed.substringBefore("/v1/") + "/v1"
            trimmed.endsWith("/v1") -> trimmed
            else -> trimmed.substringBeforeLast('/')
        }
        return "$base/${ModerationApi.PATH}"
    }

    fun childMessage(stage: SafetyStage): String = when (stage) {
        SafetyStage.INPUT -> "Not sent: it looks like sexual content involving a child."
        SafetyStage.OUTPUT -> "Not shown: it looks like sexual content involving a child."
    }

    fun strictMessage(stage: SafetyStage): String = when (stage) {
        SafetyStage.INPUT -> "Not sent: Lowkey's safety check doesn't allow this."
        SafetyStage.OUTPUT -> "Not shown: Lowkey's safety check doesn't allow this."
    }

    fun consentMessage(stage: SafetyStage): String = when (stage) {
        SafetyStage.INPUT -> "Not sent: it looks like sexual content made without consent."
        SafetyStage.OUTPUT -> "Not shown: it looks like sexual content made without consent."
    }

    fun flaggedMessage(stage: SafetyStage): String = when (stage) {
        SafetyStage.INPUT -> "Not sent: the safety check flagged it."
        SafetyStage.OUTPUT -> "Not shown: the safety check flagged it."
    }

    /** [detail] is a short reason already written for the user, such as a failed connection. */
    fun keptBack(stage: SafetyStage, detail: String): String {
        val head = if (stage == SafetyStage.INPUT) "Not sent" else "Not shown"
        val reason = detail.trim().trimEnd('.')
        return "$head. $reason, so Lowkey kept it back."
    }

    fun fromModeration(reply: ModerationReply, strict: Boolean, stage: SafetyStage): SafetyDecision = when (reply) {
        is ModerationReply.Failed -> if (reply.refused) {
            SafetyDecision.Blocked("The API key or token was refused", failedClosed = true, refused = true)
        } else {
            SafetyDecision.Blocked(keptBack(stage, reply.message), failedClosed = true)
        }
        is ModerationReply.Result -> when {
            !reply.flagged -> SafetyDecision.Allow
            reply.categories.any { "minor" in it.lowercase() } -> SafetyDecision.Blocked(childMessage(stage))
            reply.categories.isEmpty() -> SafetyDecision.Blocked(flaggedMessage(stage), failedClosed = true)
            strict -> SafetyDecision.Blocked(flaggedMessage(stage))
            else -> SafetyDecision.Allow
        }
    }

    /** The provider's own safety refusal (not a bad key, a limit, or a generic 400). */
    fun isProviderSafetyRefusal(code: Int, error: JSONObject?): Boolean {
        if (code !in 400..499 || code == 401 || code == 429) return false
        val codeField = (error?.opt("code") as? String)?.lowercase().orEmpty()
        val type = (error?.opt("type") as? String)?.lowercase().orEmpty()
        val message = (error?.opt("message") as? String)?.lowercase().orEmpty()
        return codeField == "content_policy_violation" ||
            codeField == "moderation_blocked" ||
            "content_policy" in codeField ||
            "content_policy" in type ||
            "safety system" in message ||
            "content policy" in message ||
            ("moderation" in message && ("blocked" in message || "rejected" in message))
    }
}

/**
 * On-device list for prompts and improved text, used before a provider call and for servers that
 * have no moderation endpoint. It looks for sexual content involving a child (always), sexual
 * content made without consent (always) and, when [strict], a short list of other sexual phrases.
 * It does not understand pictures.
 */
object LocalBlocklist {
    fun check(text: String, strict: Boolean, stage: SafetyStage): SafetyDecision.Blocked? {
        val spaced = normalize(text.take(ImageApi.MAX_PROMPT_CHARS))
        if (spaced.isEmpty()) return null
        val tokens = spaced.split(' ')
        val child = standaloneAlways(tokens) || glued(tokens) ||
            (hasMinor(tokens, spaced) && hasSexual(tokens)) ||
            (underage(spaced, tokens) && hasSexual(tokens)) ||
            alwaysPhrase(spaced)
        if (child) return SafetyDecision.Blocked(ContentSafety.childMessage(stage))
        if (consentPhrase(spaced)) return SafetyDecision.Blocked(ContentSafety.consentMessage(stage))
        if (strict && strictTerm(tokens)) return SafetyDecision.Blocked(ContentSafety.strictMessage(stage))
        return null
    }

    private fun standaloneAlways(tokens: List<String>): Boolean = tokens.any { it in ALWAYS_ALONE }

    private fun alwaysPhrase(spaced: String): Boolean = ALWAYS_PHRASES.any { it in spaced }

    private fun consentPhrase(spaced: String): Boolean = CONSENT_PHRASES.any { it in spaced }

    private fun strictTerm(tokens: List<String>): Boolean = tokens.any { it in STRICT_ALONE || it.startsWith("porn") }

    private fun hasMinor(tokens: List<String>, spaced: String): Boolean =
        tokens.any { it in MINOR } || MINOR_PHRASES.any { it in spaced }

    private fun hasSexual(tokens: List<String>): Boolean = tokens.any { token ->
        token in SEXUAL || SEXUAL_PREFIX.any { token.startsWith(it) }
    }

    /** One glued token such as a leetspeak evasion, not two ordinary words. */
    private fun glued(tokens: List<String>): Boolean = tokens.any { token ->
        if (MINOR_STEMS.none { it in token }) return@any false
        val sexual = SEXUAL_STEMS.any { it in token }
        val sex = "sex" in token && "essex" !in token && "unisex" !in token && "middlesex" !in token
        sexual || sex
    }

    private fun underage(spaced: String, tokens: List<String>): Boolean {
        if (AGE.findAll(spaced).any { (it.groupValues[1].toIntOrNull() ?: 99) in 0..17 }) return true
        return tokens.any { token ->
            val match = YO.matchEntire(token) ?: return@any false
            (match.groupValues[1].toIntOrNull() ?: 99) in 0..17
        }
    }

    /** Letters and digits, with light leetspeak only inside a mostly-letter token (so "16yo" stays). */
    internal fun normalize(raw: String): String {
        val stripped = raw.replace(ZERO_WIDTH, "")
        val rough = buildString {
            for (ch in stripped) {
                when {
                    ch.isLetter() -> append(ch.lowercaseChar())
                    ch.isDigit() -> append(ch)
                    else -> append(' ')
                }
            }
        }
        return rough.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { token ->
            val letters = token.count { it.isLetter() }
            val digits = token.count { it.isDigit() }
            if (letters >= 3 && digits > 0 && letters > digits) token.map { leet(it) }.joinToString("") else token
        }
    }

    private fun leet(ch: Char) = when (ch) {
        '0' -> 'o'
        '1' -> 'i'
        '3' -> 'e'
        '4' -> 'a'
        '5' -> 's'
        '7' -> 't'
        else -> ch
    }

    private val ZERO_WIDTH = Regex("[\\u200B-\\u200D\\uFEFF]")
    private val AGE = Regex("(?<!\\d)(\\d{1,2}) years? old")
    private val YO = Regex("(\\d{1,2})yo")

    private val ALWAYS_ALONE = setOf(
        "csam", "jailbait", "lolicon", "shotacon", "loli", "shota", "lolita",
    )
    private val ALWAYS_PHRASES = listOf("child porn", "kiddie porn", "underage porn", "preteen porn")
    private val MINOR = setOf(
        "child", "children", "kid", "kids", "minor", "minors", "underage", "preteen", "toddler", "toddlers",
        "infant", "infants", "baby", "babies", "newborn", "teen", "teens", "teenager", "teenagers",
        "schoolgirl", "schoolgirls", "schoolboy", "schoolboys",
    )
    private val MINOR_PHRASES = listOf("pre teen")
    private val MINOR_STEMS = listOf("child", "underage", "preteen", "toddler", "lolicon", "shotacon")
    private val SEXUAL = setOf(
        "nude", "nudes", "nudity", "naked", "porn", "porno", "pornography", "sex", "sexual", "sexually",
        "erotic", "erotica", "nsfw", "lingerie", "genital", "genitals", "penis", "vagina", "intercourse",
        "orgasm", "fetish", "hentai", "xxx",
    )
    private val SEXUAL_PREFIX = listOf("sexual", "pornograph")
    private val SEXUAL_STEMS = listOf("porn", "nude", "naked", "nsfw", "sexual", "hentai")
    private val STRICT_ALONE = setOf("porn", "porno", "pornography", "hentai", "nsfw", "xxx", "erotic", "erotica")
    private val CONSENT_PHRASES = listOf(
        "revenge porn", "deepfake nude", "deepfake porn", "deepfake naked", "without consent", "non consensual",
    )
}
