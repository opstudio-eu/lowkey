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
    /** Where the setting lives, e.g. "Settings › Keyboard › Typing", for the result's second line. */
    val location: String
        get() = destinationPath(destination)
            // A page result names where the page is; anything on a page names the page.
            .let { if (highlight || section != null) it else it.dropLast(1) }
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
    page("Keyboard", Destination.SETTINGS_KEYBOARD, "keys", "size"),
    row("Resize and move", Destination.SETTINGS_KEYBOARD, "size", "height", "width", "position", "bigger", "smaller", "one-handed", "floating"),
    row("Split on large screens", Destination.SETTINGS_KEYBOARD, "split keyboard", "tablet", "foldable"),
    row("Reset size and position", Destination.SETTINGS_KEYBOARD, "default size", "restore"),
    row("Number row", Destination.SETTINGS_KEYBOARD, "numbers", "digits", "1-0"),
    row("Key beside Space", Destination.SETTINGS_KEYBOARD, "emoji key", "globe", "language switch key", "smiley", "spacebar"),
    row("Show with a physical keyboard", Destination.SETTINGS_KEYBOARD, "hardware keyboard", "bluetooth", "external keyboard", "minimized"),
    page("Typing", Destination.SETTINGS_TYPING),
    row("Auto-capitalize", Destination.SETTINGS_TYPING, "capitals", "capital letters", "uppercase", "sentence case", "shift"),
    row("Autocorrect", Destination.SETTINGS_TYPING, "auto-correct", "typos", "spelling", "correction", "spell check"),
    row("Suggestion strip", Destination.SETTINGS_TYPING, "word suggestions", "predictions", "next word", "autocomplete"),
    page("Key feedback", Destination.SETTINGS_CUSTOMIZE),
    row("Vibrate on key press", Destination.SETTINGS_CUSTOMIZE, "vibration", "haptic", "haptics", "feedback", "buzz"),
    // Languages
    page("Keyboard languages", Destination.LANGUAGES, "languages & layouts", "add a language", "download", "remove", "word list", "dictionary"),
    page("Keyboard layouts", Destination.SETTINGS_KEYBOARDS, "qwerty", "azerty", "qwertz", "dvorak", "colemak", "switch language"),
    row("Keyboard in use", Destination.SETTINGS_KEYBOARDS, "current layout", "language", "layout"),
    // Appearance
    page("Appearance", Destination.SETTINGS_APPEARANCE, "look", "colors", "colours"),
    row("Theme", Destination.SETTINGS_APPEARANCE, "dark mode", "light mode", "night", "dark theme", "system"),
    page("Keyboard theme", Destination.KEYBOARD_THEME, "custom colors", "colours", "standard colors"),
    // Shown on the Keyboard theme page once Custom is chosen.
    themePart("Colors", "colours", "swatches", "hex", *ColorRole.entries.filter { it.seed != null }.map { it.label }.toTypedArray()),
    themePart("Background image", "wallpaper", "photo", "picture", "dimming", "remove image"),
    themePart("Advanced colors", "colours", "override", *ColorRole.overridable.map { it.label }.toTypedArray()),
    themePart("Contrast", "readable", "readability", "legibility"),
    themePart("Restore default colors", "reset colors", "standard colors"),
    // Clipboard
    page("Clipboard & images", Destination.SETTINGS_CLIPBOARD, "copy", "paste", "clips"),
    row("Recent screenshots", Destination.SETTINGS_CLIPBOARD, "screenshot", "photos", "images", "clipboard", "photo access"),
    row("Magnifier position", Destination.SETTINGS_CLIPBOARD, "loupe", "zoom", "image editor", "draw", "markup"),
    // Voice
    page("Voice typing", Destination.MODELS, "speech model", "whisper", "download model", "speech recognition", "voice", "microphone", "mic"),
    page("Dictation", Destination.SETTINGS_VOICE, "dictation settings", "voice settings", "speech", "microphone"),
    row("Dictate from the keys", Destination.SETTINGS_VOICE, "keyboard-first", "mic", "microphone", "voice mode"),
    row("Start recording right away", Destination.SETTINGS_VOICE, "auto-start", "autostart", "listen", "voice mode"),
    row("Remember keys or voice", Destination.SETTINGS_VOICE, "last mode", "reopen", "voice mode"),
    row("Keep dictating", Destination.SETTINGS_VOICE, "continuous dictation", "continuous", "voice mode"),
    row("Show words as you speak", Destination.SETTINGS_VOICE, "live text", "preview", "provisional", "underline"),
    row("Stop after silence", Destination.SETTINGS_VOICE, "auto stop", "silence", "timeout", "pause"),
    row("Voice language", Destination.SETTINGS_VOICE, "speech language", "dictation language", "same as keyboard"),
    page("Storage & performance", Destination.SETTINGS_STORAGE, "space", "free space", "disk", "speech models"),
    row("Keep model loaded", Destination.SETTINGS_STORAGE, "memory", "ram", "unload", "model memory", "speed", "faster"),
    // Dictionary
    page("My words", Destination.DICTIONARY, "dictionary", "personal dictionary", "custom words", "names", "add word"),
    row("Match the spelling of my words", Destination.DICTIONARY, "capitals", "case", "dictation", "spelling").copy(section = "My words"),
    row("Use speech rules", Destination.DICTIONARY, "speech rules", "replace", "phrase", "replacement", "dictation").copy(section = "Speech rules"),
    // App
    page("Updates", Destination.SETTINGS_UPDATES, "check for updates", "new version", "upgrade", "install update"),
    // UI Preview builds install updates by hand, so their Updates page has no automatic checks.
    row("Check for updates automatically", Destination.SETTINGS_UPDATES, "automatic updates", "notifications", "auto update")
        .takeIf { acceptsReleaseUpdates(BuildConfig.APPLICATION_ID) },
    page("Try it", Destination.TRY_IT, "test", "test typing", "test dictation", "test fields"),
    page("Diagnostics", Destination.DIAGNOSTICS, "crash reports", "crashes", "voice sessions", "logs", "debug", "bug report"),
    // Debug builds only, where the bundled test clips exist.
    row("Dictation audio", Destination.DIAGNOSTICS, "test clips", "microphone", "emulator").takeIf { BuildConfig.DEBUG },
    page("About and licenses", Destination.ABOUT, "about", "version", "privacy", "licenses", "open source", "source code", "credits"),
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
