package com.rheerkens.androidkeyboard.companion

import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.keyboard.ui.ColorRole
import com.rheerkens.androidkeyboard.updates.acceptsReleaseUpdates
import java.text.Normalizer

/**
 * One searchable setting or settings page. [title] is the row's exact label on [destination], so
 * opening a result can point at that row; a page itself has [highlight] off. [keywords] hold the
 * other words people use for it ("dark mode" for Theme).
 */
data class SettingEntry(
    val title: String,
    val destination: Destination,
    val keywords: List<String> = emptyList(),
    val highlight: Boolean = true,
    /** A part of the page the setting is in, such as a tab, added to [location]. */
    val section: String? = null,
) {
    /** Where the setting lives, e.g. "Settings › Keyboard", for the result's second line. */
    val location: String
        get() = destinationPath(destination)
            // A page result names where the page is; anything on a page names the page.
            .let { if (highlight || section != null || title != destination.title) it else it.dropLast(1) }
            .ifEmpty { listOf(destination) }
            .map { it.title }
            .plus(listOfNotNull(section))
            .joinToString(" › ")
}

private fun page(title: String, destination: Destination, vararg keywords: String) =
    SettingEntry(title, destination, keywords.toList(), highlight = false)

private fun themePart(title: String, vararg keywords: String) =
    SettingEntry(title, Destination.KEYBOARD_THEME, keywords.toList(), highlight = false, section = "Custom")

private fun row(title: String, destination: Destination, vararg keywords: String) =
    SettingEntry(title, destination, keywords.toList())

/**
 * Every setting in the companion app, in the order the pages show them. The titles must match the
 * rows on their pages; a unit test checks that each title appears on its screen.
 */
val settingsIndex: List<SettingEntry> = listOfNotNull(
    // Keyboard
    page("Keyboard", Destination.SETTINGS_KEYBOARD, "keys", "size", "typing"),
    row("Auto-capitalize", Destination.SETTINGS_KEYBOARD, "capitals", "capital letters", "uppercase", "sentence case", "shift", "typing"),
    row("Autocorrect", Destination.SETTINGS_KEYBOARD, "auto-correct", "typos", "spelling", "correction", "spell check", "typing"),
    row("Suggestion strip", Destination.SETTINGS_KEYBOARD, "word suggestions", "predictions", "next word", "autocomplete", "typing"),
    row("Learn from my typing", Destination.SETTINGS_KEYBOARD, "learning", "personal", "predict", "next word", "privacy"),
    row("Forget what Lowkey learned", Destination.SETTINGS_KEYBOARD, "clear learned words", "delete", "reset predictions", "privacy"),
    row("Number row", Destination.SETTINGS_KEYBOARD, "numbers", "digits", "1-0"),
    row("Pointing stick", Destination.SETTINGS_KEYBOARD, "trackpoint", "red dot", "cursor", "trackpad", "move the cursor", "slide", "g key"),
    row("Pointing stick speed", Destination.SETTINGS_KEYBOARD, "trackpoint speed", "cursor speed", "sensitivity", "faster", "slower", "red dot"),
    row("Key beside Space", Destination.SETTINGS_KEYBOARD, "emoji key", "globe", "language switch key", "smiley", "spacebar"),
    row("Vibrate on key press", Destination.SETTINGS_KEYBOARD, "vibration", "haptic", "haptics", "feedback", "buzz", "key feedback"),
    row("Resize and move", Destination.SETTINGS_KEYBOARD, "size", "height", "width", "position", "bigger", "smaller", "one-handed", "floating"),
    row("Split on large screens", Destination.SETTINGS_KEYBOARD, "split keyboard", "tablet", "foldable"),
    row("Reset size and position", Destination.SETTINGS_KEYBOARD, "default size", "restore"),
    row("Show with a physical keyboard", Destination.SETTINGS_KEYBOARD, "hardware keyboard", "bluetooth", "external keyboard", "minimized"),
    // Languages
    page("Languages & layouts", Destination.LANGUAGES, "keyboard languages", "add a language", "download", "remove", "word list", "dictionary"),
    // The layout chips under the language you type in.
    row("Layouts", Destination.LANGUAGES, "keyboard layouts", "qwerty", "azerty", "qwertz", "dvorak", "colemak", "switch language", "keyboard in use"),
    // Appearance
    page("Appearance", Destination.SETTINGS_APPEARANCE, "look", "colors", "colours"),
    row("Theme", Destination.SETTINGS_APPEARANCE, "dark mode", "light mode", "night", "dark theme", "system"),
    page("Keyboard theme", Destination.KEYBOARD_THEME, "custom colors", "colours", "standard colors"),
    row("Shaded number row", Destination.SETTINGS_APPEARANCE, "number row color", "digits", "1-0", "function keys", "colours"),
    // Shown on the Keyboard theme page once Custom is chosen.
    themePart("Colors", "colours", "swatches", "hex", *ColorRole.entries.filter { it.seed != null }.map { it.label }.toTypedArray()),
    themePart("Background image", "wallpaper", "photo", "picture", "dimming", "remove image"),
    themePart("Advanced colors", "colours", "override", *ColorRole.overridable.map { it.label }.toTypedArray()),
    themePart("Contrast", "readable", "readability", "legibility"),
    themePart("Restore default colors", "reset colors", "standard colors"),
    // Backup
    row("Export words and settings", Destination.SETTINGS, "backup", "save", "transfer", "new phone", "dictionary"),
    row("Import words and settings", Destination.SETTINGS, "restore", "backup", "transfer", "new phone", "dictionary"),
    // Clipboard
    page("Clipboard & images", Destination.SETTINGS_CLIPBOARD, "copy", "paste", "clips"),
    row("Keep copied clips", Destination.SETTINGS_CLIPBOARD, "clipboard history", "expire", "forget", "clips", "privacy"),
    row("Recent screenshots", Destination.SETTINGS_CLIPBOARD, "screenshot", "photos", "images", "clipboard", "photo access"),
    row("Magnifier position", Destination.SETTINGS_CLIPBOARD, "loupe", "zoom", "image editor", "draw", "markup"),
    // Experimental
    page("Experimental", Destination.SETTINGS_EXPERIMENTAL, "labs", "beta", "preview", "ai"),
    row("AI on the keyboard", Destination.SETTINGS_EXPERIMENTAL, "ai", "image generation", "generate image", "ai image",
        "openai", "picture", "meme", "sticker", "sparkle", "create image", "assistant"),
    row("AI service", Destination.SETTINGS_EXPERIMENTAL, "openai", "own server", "provider", "image generation"),
    row("OpenAI API key", Destination.SETTINGS_EXPERIMENTAL, "api key", "openai", "key", "image generation"),
    row("Image model", Destination.SETTINGS_EXPERIMENTAL, "gpt image", "flare", "sunburst", "image generation"),
    row("Server model", Destination.SETTINGS_EXPERIMENTAL, "model", "own server", "image generation"),
    row("Text model", Destination.SETTINGS_EXPERIMENTAL, "model", "own server", "improve prompt", "chat"),
    row("Image quality", Destination.SETTINGS_EXPERIMENTAL, "fast", "best", "cost", "image generation"),
    row("Access token", Destination.SETTINGS_EXPERIMENTAL, "bearer", "token", "password", "own server", "image generation"),
    row("Server address", Destination.SETTINGS_EXPERIMENTAL, "own server", "url", "endpoint", "tailscale", "image generation"),
    row("Save to Gallery", Destination.SETTINGS_EXPERIMENTAL, "gallery", "photos", "save image", "created images"),
    row("Stricter safety checks", Destination.SETTINGS_EXPERIMENTAL, "safety", "filter", "moderation", "blocked", "content"),
    row("Report address", Destination.SETTINGS_EXPERIMENTAL, "report", "flag", "offensive", "safety", "endpoint"),
    row("What a request sends", Destination.SETTINGS_EXPERIMENTAL, "disclosure", "ip address", "privacy", "what is sent", "continue"),
    // Voice
    page("Voice typing", Destination.MODELS, "speech recognition", "voice", "microphone", "mic"),
    page("Speech models", Destination.SPEECH_MODELS, "speech model", "whisper", "download model", "delete model", "change model", "offline",
        "storage", "space", "free space", "disk"),
    page("Dictation settings", Destination.SETTINGS_VOICE, "dictation", "voice settings", "speech", "microphone"),
    row("Dictate from the keys", Destination.SETTINGS_VOICE, "keyboard-first", "mic", "microphone", "voice mode"),
    row("Start recording right away", Destination.SETTINGS_VOICE, "auto-start", "autostart", "listen", "voice mode"),
    row("Remember keys or voice", Destination.SETTINGS_VOICE, "last mode", "reopen", "voice mode"),
    row("Keep dictating", Destination.SETTINGS_VOICE, "continuous dictation", "continuous", "voice mode"),
    row("Show words as you speak", Destination.SETTINGS_VOICE, "live text", "preview", "provisional", "underline"),
    row("Stop after silence", Destination.SETTINGS_VOICE, "auto stop", "silence", "timeout", "pause"),
    row("Voice language", Destination.SETTINGS_VOICE, "speech language", "dictation language", "same as keyboard"),
    row("Keep model loaded", Destination.SETTINGS_VOICE, "memory", "ram", "unload", "model memory", "speed", "faster", "performance"),
    // Dictionary
    page("My words", Destination.DICTIONARY, "dictionary", "personal dictionary", "custom words", "names", "add word"),
    row("Match the spelling of my words", Destination.DICTIONARY, "capitals", "case", "dictation", "spelling").copy(section = "My words"),
    row("Use speech rules", Destination.DICTIONARY, "speech rules", "replace", "phrase", "replacement", "dictation").copy(section = "Speech rules"),
    // App
    page("Try it", Destination.TRY_IT, "test", "test typing", "test dictation", "test fields"),
    page("Learn the gestures", Destination.TOUR, "tour", "tutorial", "help", "how to", "tips", "move the cursor", "select text",
        "delete words", "slide", "swipe", "trackpad"),
    page("About & updates", Destination.ABOUT, "about", "version", "privacy", "licenses", "open source", "source code", "credits"),
    // At the top of About & updates.
    page("Updates", Destination.ABOUT, "check for updates", "new version", "upgrade", "install update"),
    // UI Preview builds install updates by hand, so they have no automatic checks.
    row("Check for updates automatically", Destination.ABOUT, "automatic updates", "notifications", "auto update")
        .takeIf { acceptsReleaseUpdates(BuildConfig.APPLICATION_ID) },
    // At the end of About & updates.
    row("Diagnostics", Destination.ABOUT, "crash reports", "crashes", "voice sessions", "logs", "debug", "bug report"),
    // Debug builds only, where the bundled test clips exist.
    row("Dictation audio", Destination.ABOUT, "test clips", "microphone", "emulator").takeIf { BuildConfig.DEBUG },
)

/** The back stack for a result opened from the Settings search: the results, then the page. */
fun searchResultRoute(destination: Destination): List<Destination> =
    if (destination == Destination.SETTINGS) listOf(destination) else listOf(Destination.SETTINGS, destination)

/** Lowercase, without accents, and with punctuation as spaces: "Auto-capitalize" → "auto capitalize". */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace('&', ' ')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

/** Matches when [token] starts a word of [text], or occurs in it with the spaces taken out ("autocap"). */
private fun matchesToken(text: String, token: String): Boolean =
    text.split(' ').any { it.startsWith(token) } || token.length >= 3 && text.replace(" ", "").contains(token)

/**
 * Settings that match [query], best first. Every word of the query must start a word of the
 * title, a keyword or the page it's on, so "dark" finds Theme and "key vib" finds Vibrate on key
 * press. Title matches rank above keyword matches, which rank above page matches.
 */
fun searchSettings(query: String, entries: List<SettingEntry> = settingsIndex): List<SettingEntry> {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return emptyList()
    val tokens = q.split(' ')
    return entries.mapIndexedNotNull { index, entry ->
        val title = normalizeForSearch(entry.title)
        val keywords = entry.keywords.map(::normalizeForSearch)
        val location = normalizeForSearch(entry.location)
        val fields = listOf(title) + keywords + location
        if (!tokens.all { token -> fields.any { matchesToken(it, token) } }) return@mapIndexedNotNull null
        val score = when {
            title == q -> 6
            title.startsWith(q) -> 5
            tokens.all { matchesToken(title, it) } -> 4
            keywords.any { it == q || it.startsWith(q) } -> 3
            tokens.all { token -> (listOf(title) + keywords).any { matchesToken(it, token) } } -> 2
            else -> 1
        }
        Triple(score, index, entry)
    }.sortedWith(compareByDescending<Triple<Int, Int, SettingEntry>> { it.first }.thenBy { it.second }).map { it.third }
}
