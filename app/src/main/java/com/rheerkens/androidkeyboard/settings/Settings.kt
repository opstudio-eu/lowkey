package com.rheerkens.androidkeyboard.settings

/** Keyboard key-row height preset. */
enum class KeyboardHeight(val label: String, val rowHeightDp: Int) {
    COMPACT("Compact", 42),
    NORMAL("Normal", 48),
    TALL("Tall", 56),
}

/**
 * Typing languages: each picks the word list for suggestions and autocorrect. Which letters sit
 * where is the separate [KeyArrangement]. Voice dictation stays English-only.
 */
enum class TypingLayout(val label: String, val shortLabel: String, val localeTag: String) {
    EN_US("English (US)", "EN", "en-US"),
    NL_NL("Nederlands", "NL", "nl-NL"),
}

/**
 * Letter arrangements of the main key layer, as three rows of key labels (top to bottom).
 * Rows with fewer than ten keys are centred; the third row sits between Shift and Backspace.
 * The top row's keys also offer the digits 1–0 on long-press.
 */
enum class KeyArrangement(val label: String, val rows: List<String>) {
    QWERTY("QWERTY", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")),
    /** French and Belgian; the apostrophe takes the place of `m`, which moves up a row. */
    AZERTY("AZERTY", listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")),
    /** German, Austrian, Swiss and most of Central Europe. */
    QWERTZ("QWERTZ", listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")),
    DVORAK("Dvorak", listOf("',.pyfgcrl", "aoeuidhtns", "qjkxbmwvz")),
    COLEMAK("Colemak", listOf("qwfpgjluy'", "arstdhneio", "zxcvbkm")),
}

/** One entry of the globe key's cycle: a typing language shown with a letter arrangement. */
data class TypingKeyboard(val language: TypingLayout, val arrangement: KeyArrangement = KeyArrangement.QWERTY) {
    /** Space-bar and settings name; QWERTY, the usual arrangement, isn't spelled out. */
    val label: String
        get() = if (arrangement == KeyArrangement.QWERTY) language.label else "${language.label} · ${arrangement.label}"

    /** Stable preference value, e.g. `EN_US:AZERTY`. */
    val id: String get() = "${language.name}:${arrangement.name}"

    companion object {
        val DEFAULT = TypingKeyboard(TypingLayout.EN_US)

        /** Every combination, in globe order: by language, then by arrangement. */
        val ALL: List<TypingKeyboard> =
            TypingLayout.entries.flatMap { language -> KeyArrangement.entries.map { TypingKeyboard(language, it) } }

        /** Parses an [id]; a bare language name (older preferences) means QWERTY. */
        fun parse(id: String): TypingKeyboard? {
            val language = TypingLayout.entries.firstOrNull { it.name == id.substringBefore(':') } ?: return null
            if (':' !in id) return TypingKeyboard(language)
            val arrangement = KeyArrangement.entries.firstOrNull { it.name == id.substringAfter(':') } ?: return null
            return TypingKeyboard(language, arrangement)
        }

        /** [keyboards] in globe order. */
        fun ordered(keyboards: Collection<TypingKeyboard>): List<TypingKeyboard> = ALL.filter { it in keyboards }
    }
}

enum class AutoStop(val label: String, val millis: Long) {
    OFF("Off", 0),
    TWO_SECONDS("2 s", 2_000),
    FOUR_SECONDS("4 s", 4_000),
}

/** Time to keep native model resources after a dictation ends, not after hiding the IME. */
enum class ModelRetention(val label: String, val millis: Long?) {
    IMMEDIATE("Release after dictation", 0),
    THIRTY_SECONDS("30 seconds", 30_000),
    TWO_MINUTES("2 minutes", 120_000),
    FIVE_MINUTES("5 minutes", 300_000),
    FIFTEEN_MINUTES("15 minutes", 900_000),
    UNTIL_CLOSED("Until app closes", null),
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Where dictation audio comes from. Test clips exist only in debug builds (Diagnostics page). */
enum class AudioInput(val label: String) {
    MICROPHONE("Microphone"),
    TEST_CLIPS("Test clips (debug)"),
}

enum class InputMode { KEYS, VOICE }

/**
 * Which two canvas corners the image editor's loupe may dock in (indices as in
 * `LoupePlacement.corners`): the top ones stay clear of either hand, the left ones of a right hand
 * and the right ones of a left hand.
 */
enum class LoupeCorners(val label: String, val corners: List<Int>) {
    TOP("Top", listOf(0, 1)),
    LEFT("Left", listOf(0, 2)),
    RIGHT("Right", listOf(1, 3)),
}

/** Every user-visible setting. Immutable snapshot; see [SettingsStore]. */
data class Settings(
    // Keyboard
    val keyboardHeight: KeyboardHeight = KeyboardHeight.NORMAL,
    val vibrateOnKeyPress: Boolean = true,
    val autoCapitalize: Boolean = true,
    val autocorrect: Boolean = true,
    val suggestionStrip: Boolean = true,
    /** Keyboards the globe key cycles through; never empty. */
    val enabledKeyboards: Set<TypingKeyboard> = TypingLayout.entries.map { TypingKeyboard(it) }.toSet(),
    val currentKeyboard: TypingKeyboard = TypingKeyboard.DEFAULT,
    /** Keep the keyboard on screen with a physical keyboard connected (Android hides it otherwise). */
    val showWithPhysicalKeyboard: Boolean = true,
    /** Show recent screenshots next to clipboard history in the minimized bar (photos permission). */
    val recentScreenshots: Boolean = false,
    // Voice
    val keyboardFirstDictation: Boolean = true,
    val autoStartVoice: Boolean = true,
    val rememberLastMode: Boolean = true,
    val liveTextInEditor: Boolean = true,
    val autoStop: AutoStop = AutoStop.OFF,
    val continuousDictation: Boolean = false,
    val applyPhraseRules: Boolean = false,
    val matchTermCase: Boolean = true,
    /** Preferred speech language code for multilingual models (`auto` = detect). */
    val speechLanguage: String = "en",
    val modelRetention: ModelRetention = ModelRetention.TWO_MINUTES,
    // Appearance
    val theme: ThemeMode = ThemeMode.SYSTEM,
    // Updates
    /** Check GitHub for a newer release about once a day and when the app opens. */
    val checkForUpdates: Boolean = true,
    // Image editor
    val loupeCorners: LoupeCorners = LoupeCorners.TOP,
    // Diagnostics
    val audioInput: AudioInput = AudioInput.MICROPHONE,
    // Keyboard state that is remembered between sessions
    val lastMode: InputMode = InputMode.KEYS,
    /** Show only the slim bar until the user expands it, remembered with and without a physical keyboard. */
    val minimizedWithPhysicalKeyboard: Boolean = true,
    val minimizedWithoutPhysicalKeyboard: Boolean = false,
) {
    /** Turns [keyboard] on or off. The last enabled keyboard stays on; the current one moves on if needed. */
    fun toggleKeyboard(keyboard: TypingKeyboard): Settings {
        val next = if (keyboard in enabledKeyboards) enabledKeyboards - keyboard else enabledKeyboards + keyboard
        if (next.isEmpty()) return this
        val current = if (currentKeyboard in next) currentKeyboard else TypingKeyboard.ordered(next).first()
        return copy(enabledKeyboards = next, currentKeyboard = current)
    }
}
