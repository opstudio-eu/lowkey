package com.rheerkens.androidkeyboard.settings

/** Keyboard key-row height preset. */
enum class KeyboardHeight(val label: String, val rowHeightDp: Int) {
    COMPACT("Compact", 42),
    NORMAL("Normal", 48),
    TALL("Tall", 56),
}

/** Typing layouts offered by the globe key. Voice dictation stays English-only. */
enum class TypingLayout(val label: String, val shortLabel: String, val localeTag: String) {
    EN_US("English (US)", "EN", "en-US"),
    NL_NL("Nederlands", "NL", "nl-NL"),
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
    val enabledLayouts: Set<TypingLayout> = TypingLayout.entries.toSet(),
    val currentLayout: TypingLayout = TypingLayout.EN_US,
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
    // Image editor
    val loupeCorners: LoupeCorners = LoupeCorners.TOP,
    // Diagnostics
    val audioInput: AudioInput = AudioInput.MICROPHONE,
    // Keyboard state that is remembered between sessions
    val lastMode: InputMode = InputMode.KEYS,
    /** Show only the slim bar until the user expands it, remembered with and without a physical keyboard. */
    val minimizedWithPhysicalKeyboard: Boolean = true,
    val minimizedWithoutPhysicalKeyboard: Boolean = false,
)
