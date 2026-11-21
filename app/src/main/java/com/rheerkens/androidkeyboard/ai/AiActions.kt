package com.rheerkens.androidkeyboard.ai

/**
 * What the keyboard's ✨ AI can do. Each action uses the composer above the strip (which gets an
 * action chip once there is more than one) and its own API path under the shared connection
 * ([AiConnection]); the service runs it as an [AiTask] and delivers its result. Planned next: cleaning up the text in the field, and commands on it ("make it
 * friendlier"); those will add a text result, delivered into the field.
 */
enum class AiAction(
    val label: String,
    /** One line about the action, for the action chip's list. */
    val detail: String,
    /** The prompt bar's placeholder. */
    val promptHint: String,
    /** The composer's send button, which Enter matches. */
    val button: String,
    /** Under the API base, e.g. `https://api.openai.com/v1` + `/` + this. */
    val path: String,
) {
    CREATE_IMAGE("Create image", "Describe it; it lands in your clipboard", "Describe an image…", "Create", "images/generations"),
    /** Create image with pictures added to the prompt: the service changes or combines them. */
    EDIT_IMAGE("Change image", "Add pictures and say what to change", "Describe the change…", "Create", "images/edits"),
}

/** Under the API base: Improve in the prompt window rewrites the prompt with a text model ([PromptApi]). */
const val CHAT_PATH = "chat/completions"

/** One action running in the background, listed in the composer with a Stop button. */
data class AiTask(val id: Long, val action: AiAction, val prompt: String, val startedAtMs: Long)

/** At most this many actions run at once, so a few stray taps can't run up a bill. */
const val MAX_AI_TASKS = 4

/**
 * Whether a finished image may go onto the system clipboard: only if the clip there is the one from
 * when its request was sent ([atSubmit]) or an image Lowkey put there itself since ([lastOwn]), never
 * over something the user copied meanwhile. Times are [android.content.ClipDescription] timestamps.
 */
fun mayReplaceClip(current: Long, atSubmit: Long, lastOwn: Long): Boolean =
    current == atSubmit || (lastOwn != 0L && current == lastOwn)

/** OpenAI image models offered in the menu and Settings; any other id can still be stored. */
enum class ImageModel(val id: String, val label: String, val short: String, val speed: String, val detail: String) {
    FLARE("gpt-image-2.6-flare", "GPT Image 2.6 Flare", "Flare", "Fast, cheapest",
        "Quick everyday pictures and memes, at the lowest price"),
    SUNBURST("gpt-image-2.6-sunburst", "GPT Image 2.6 Sunburst", "Sunburst", "Detailed, slower",
        "Finer detail and readable text in the picture; costs more"),
}

/**
 * The Image API's `quality`: lower is faster and cheaper. [cost] is a rough price for one square
 * image, from OpenAI's published GPT Image prices (low about $0.006, medium $0.05, high $0.21); the
 * model and the prompt change it, and OpenAI's usage page has the real figure.
 */
enum class ImageQuality(val label: String, val api: String, val detail: String, val cost: String) {
    LOW("Fast", "low", "Ready in seconds; fine for memes", "about $0.01"),
    MEDIUM("Balanced", "medium", "Sharper; takes longer", "about $0.05"),
    HIGH("Best", "high", "Most detail; slowest", "about $0.20"),
}
