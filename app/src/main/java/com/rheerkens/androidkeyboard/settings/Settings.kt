package com.rheerkens.androidkeyboard.settings

/** Keyboard key-row height preset. */
enum class KeyboardHeight(val label: String, val rowHeightDp: Int) {
    COMPACT("Compact", 42),
    NORMAL("Normal", 48),
    TALL("Tall", 56),
}

/**
 * Alternative letter arrangements for Latin-script keyboard languages (`TypingKeyboard`), as
 * three rows of key labels (top to bottom), in
 * their usual phone form: the letter rows of the traditional layout, with the keys around them
 * (Shift, Backspace, the bottom row, numbers and symbols) the same for all of them. Rows with
 * fewer than ten keys are centred; the third row sits between Shift and Backspace. The top row's
 * keys also offer the digits 1–0 on long-press. Accented letters stay on long-press of their
 * base letter, also for AZERTY and QWERTZ, whose desktop forms have keys of their own for them.
 */
enum class KeyArrangement(val label: String, val rows: List<String>) {
    QWERTY("QWERTY", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")),
    /** French and Belgian. Phones put the apostrophe after N, where desktop AZERTY has the comma. */
    AZERTY("AZERTY", listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")),
    /** German, Austrian, Swiss and most of Central Europe. */
    QWERTZ("QWERTZ", listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")),
    /** Simplified Dvorak; the semicolon before Q is left out (it's on the symbols layer). */
    DVORAK("Dvorak", listOf("',.pyfgcrl", "aoeuidhtns", "qjkxbmwvz")),
    COLEMAK("Colemak", listOf("qwfpgjluy;", "arstdhneio", "zxcvbkm")),
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
    /**
     * Ids of the keyboards the globe key cycles through (`TypingKeyboard.id`: `pl` for Polish
     * on its own layout, `en_US:AZERTY` for an alternative one). Only English (US) is built in;
     * a keyboard counts once its language's word list is on the phone.
     */
    val enabledKeyboards: Set<String> = setOf(DEFAULT_KEYBOARD),
    val currentKeyboard: String = DEFAULT_KEYBOARD,
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
    /**
     * Preferred speech language code for multilingual models (`auto` = detect,
     * [FOLLOW_KEYBOARD] = the current keyboard language's).
     */
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
    /**
     * Turns keyboard [id] on or off. [usable] lists, in globe order, the ids of every keyboard
     * whose word list is on the phone: the last usable one that is on stays on, and the current
     * keyboard moves to the first usable one if it is turned off.
     */
    fun toggleKeyboard(id: String, usable: List<String>): Settings {
        val next = if (id in enabledKeyboards) enabledKeyboards - id else enabledKeyboards + id
        val stillUsable = usable.filter { it in next }
        if (stillUsable.isEmpty()) return this
        val current = if (currentKeyboard in stillUsable) currentKeyboard else stillUsable.first()
        return copy(enabledKeyboards = next, currentKeyboard = current)
    }

    companion object {
        /** English (US) on its own (QWERTY) layout: built in, so always usable. */
        const val DEFAULT_KEYBOARD = "en_US"

        /** [speechLanguage] value: dictate in the language the keyboard is set to. */
        const val FOLLOW_KEYBOARD = "keyboard"
    }
}
