package com.rheerkens.androidkeyboard.privacy

/** A notice shown in the normal flow before Lowkey asks for a permission or sends data. */
data class Disclosure(val title: String, val body: String, val accept: String, val decline: String)

/**
 * The permission disclosures and the About › Privacy text, in one place so tests can check what they
 * promise. Each disclosure has a way to decline that leaves the app working without that feature.
 * The notice before the first AI request is [com.rheerkens.androidkeyboard.ai.AiDisclosure].
 */
object Disclosures {
    /** Before Android's microphone dialog. */
    val MICROPHONE = Disclosure(
        title = "Microphone for voice typing",
        body = "Lowkey uses the microphone only while you dictate, to turn your speech into text. " +
            "The audio is processed on this phone by the speech model you downloaded. It is not saved and not uploaded. " +
            "Next, Android asks whether to allow the microphone. Typing works without it.",
        accept = "Continue",
        decline = "Not now",
    )

    /** Before Android's photos dialog (Recent screenshots). */
    val PHOTOS = Disclosure(
        title = "Photos for recent screenshots",
        body = "To show screenshots from the last day beside your clipboard clips, Lowkey needs access to your photos. " +
            "It reads only recent screenshots, and they stay on this phone. " +
            "A picture is uploaded only if you attach it to an AI prompt and send it, and then only to the AI service you set up. " +
            "Next, Android asks whether to allow access to your photos. The keyboard works without it.",
        accept = "Continue",
        decline = "Not now",
    )

    /**
     * Settings › About › Privacy. [selfUpdate] is false in the Play build, which Google Play updates and
     * which never contacts GitHub.
     */
    fun aboutPrivacy(selfUpdate: Boolean): String = buildString {
        append("Typed text, audio, dictation and your dictionary stay on your phone. Audio is processed on your phone, ")
        append("only while you dictate, and is not saved or uploaded. Lowkey goes online only to download the speech ")
        append("models and keyboard languages you choose")
        if (selfUpdate) {
            append(", to check GitHub for a newer version about once a day (unless you turn it off above), and to ")
            append("download an update when you tap Update")
        }
        append(". ")
        if (!selfUpdate) append("Updates come through Google Play. ")
        append("Recent screenshots, if you allow photo access, are read on your phone and not uploaded.\n\n")
        append("AI on the keyboard (Settings › Experimental) is experimental and off until you set it up. When you use it, ")
        append("Lowkey sends your prompt, the pictures you attach, their number, and your IP address to the AI service ")
        append("you chose: OpenAI with your own key, or your own server. Lowkey asks before the first request. ")
        append("If you report an AI result, Lowkey sends the report to Op Studio, with the content only if you include it.")
    }
}
