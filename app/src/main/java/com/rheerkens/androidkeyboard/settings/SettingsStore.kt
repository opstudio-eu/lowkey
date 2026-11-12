package com.rheerkens.androidkeyboard.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.rheerkens.androidkeyboard.keyboard.PlacementPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Persistent settings shared by the companion app and the input method (same process).
 * Reads once at start-up and writes through on every [update].
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    /**
     * Applies [transform] and saves the result as one step, so updates from other threads (a
     * finished download enabling its language) can't save an older snapshot over a newer one.
     */
    fun update(transform: (Settings) -> Settings) = synchronized(lock) {
        _settings.update(transform)
        save(_settings.value)
    }

    private val lock = Any()

    private fun load(): Settings {
        val d = Settings()
        // Before keyboards had arrangements, only languages were stored (always QWERTY).
        val (enabled, current) = loadKeyboards(
            enabledIds = prefs.getStringSet(K_KEYBOARDS, null) ?: prefs.getStringSet(K_LAYOUTS, null),
            currentId = prefs.getString(K_KEYBOARD, null) ?: prefs.getString(K_LAYOUT, null),
            default = d,
        )
        return Settings(
            placement = PlacementPreferences.decode(
                prefs.getString("keyboard_placement", null),
                enumOr(prefs.getString(K_HEIGHT, null), d.keyboardHeight).rowHeightDp.toFloat(),
            ),
            keyboardHeight = enumOr(prefs.getString(K_HEIGHT, null), d.keyboardHeight),
            numberRow = prefs.getBoolean(K_NUMBER_ROW, d.numberRow),
            vibrateOnKeyPress = prefs.getBoolean(K_VIBRATE, d.vibrateOnKeyPress),
            autoCapitalize = prefs.getBoolean(K_AUTOCAP, d.autoCapitalize),
            autocorrect = prefs.getBoolean(K_AUTOCORRECT, d.autocorrect),
            suggestionStrip = prefs.getBoolean(K_STRIP, d.suggestionStrip),
            enabledKeyboards = enabled,
            currentKeyboard = current,
            showWithPhysicalKeyboard = prefs.getBoolean(K_SHOW_WITH_PHYSICAL, d.showWithPhysicalKeyboard),
            recentScreenshots = prefs.getBoolean(K_SCREENSHOTS, d.recentScreenshots),
            autoStartVoice = prefs.getBoolean(K_AUTOSTART, d.autoStartVoice),
            keyboardFirstDictation = prefs.getBoolean(K_KEYBOARD_FIRST, d.keyboardFirstDictation),
            rememberLastMode = prefs.getBoolean(K_REMEMBER, d.rememberLastMode),
            liveTextInEditor = prefs.getBoolean(K_LIVE, d.liveTextInEditor),
            autoStop = enumOr(prefs.getString(K_AUTOSTOP, null), d.autoStop),
            continuousDictation = prefs.getBoolean(K_CONTINUOUS, d.continuousDictation),
            applyPhraseRules = prefs.getBoolean(K_RULES, d.applyPhraseRules),
            theme = enumOr(prefs.getString(K_THEME, null), d.theme),
            loupeCorners = enumOr(prefs.getString(K_LOUPE_CORNERS, null), d.loupeCorners),
            checkForUpdates = prefs.getBoolean(K_CHECK_UPDATES, d.checkForUpdates),
            matchTermCase = prefs.getBoolean(K_TERM_CASE, d.matchTermCase),
            speechLanguage = prefs.getString(K_SPEECH_LANGUAGE, null) ?: d.speechLanguage,
            modelRetention = enumOr(prefs.getString(K_MODEL_RETENTION, null), d.modelRetention),
            audioInput = enumOr(prefs.getString(K_AUDIO_INPUT, null), d.audioInput),
            lastMode = enumOr(prefs.getString(K_LAST_MODE, null), d.lastMode),
            minimizedWithPhysicalKeyboard = prefs.getBoolean(K_MINIMIZED_PHYSICAL, d.minimizedWithPhysicalKeyboard),
            minimizedWithoutPhysicalKeyboard = prefs.getBoolean(K_MINIMIZED_TOUCH, d.minimizedWithoutPhysicalKeyboard),
        )
    }

    private fun save(s: Settings) = prefs.edit {
        putString("keyboard_placement", s.placement.encode())
        putString(K_HEIGHT, s.keyboardHeight.name)
        putBoolean(K_NUMBER_ROW, s.numberRow)
        putBoolean(K_VIBRATE, s.vibrateOnKeyPress)
        putBoolean(K_AUTOCAP, s.autoCapitalize)
        putBoolean(K_AUTOCORRECT, s.autocorrect)
        putBoolean(K_STRIP, s.suggestionStrip)
        putStringSet(K_KEYBOARDS, s.enabledKeyboards)
        putString(K_KEYBOARD, s.currentKeyboard)
        remove(K_LAYOUTS)
        remove(K_LAYOUT)
        putBoolean(K_SHOW_WITH_PHYSICAL, s.showWithPhysicalKeyboard)
        putBoolean(K_SCREENSHOTS, s.recentScreenshots)
        putBoolean(K_AUTOSTART, s.autoStartVoice)
        putBoolean(K_KEYBOARD_FIRST, s.keyboardFirstDictation)
        putBoolean(K_REMEMBER, s.rememberLastMode)
        putBoolean(K_LIVE, s.liveTextInEditor)
        putString(K_AUTOSTOP, s.autoStop.name)
        putBoolean(K_CONTINUOUS, s.continuousDictation)
        putBoolean(K_RULES, s.applyPhraseRules)
        putString(K_THEME, s.theme.name)
        putString(K_LOUPE_CORNERS, s.loupeCorners.name)
        putBoolean(K_CHECK_UPDATES, s.checkForUpdates)
        putBoolean(K_TERM_CASE, s.matchTermCase)
        putString(K_SPEECH_LANGUAGE, s.speechLanguage)
        putString(K_MODEL_RETENTION, s.modelRetention.name)
        putString(K_AUDIO_INPUT, s.audioInput.name)
        putString(K_LAST_MODE, s.lastMode.name)
        putBoolean(K_MINIMIZED_PHYSICAL, s.minimizedWithPhysicalKeyboard)
        putBoolean(K_MINIMIZED_TOUCH, s.minimizedWithoutPhysicalKeyboard)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    internal companion object {
        /**
         * Enabled and current keyboard ids from what is stored, in today's form (see
         * `TypingKeyboard.id`). Nothing stored falls back to [default]'s, and the current keyboard
         * is always one of the enabled ones. Ids the catalog doesn't know are dropped later, when
         * the keyboard resolves them.
         */
        fun loadKeyboards(enabledIds: Set<String>?, currentId: String?, default: Settings): Pair<Set<String>, String> {
            val enabled = enabledIds?.map(::keyboardId)?.toSet()?.ifEmpty { null } ?: default.enabledKeyboards
            val current = currentId?.let(::keyboardId)?.takeIf { it in enabled }
                ?: default.currentKeyboard.takeIf { it in enabled }
                ?: enabled.sorted().first()
            return enabled to current
        }

        /**
         * A stored keyboard id in today's form. Up to 0.5.5 only English (US) and Dutch existed,
         * stored as `EN_US` or `EN_US:AZERTY`; both are QWERTY languages, so `:QWERTY` means their
         * own layout (`en_US`).
         */
        fun keyboardId(stored: String): String {
            val language = stored.substringBefore(':')
            val legacy = LEGACY_LANGUAGES[language] ?: return stored
            val arrangement = stored.substringAfter(':', "")
            return if (arrangement.isEmpty() || arrangement == "QWERTY") legacy else "$legacy:$arrangement"
        }

        private val LEGACY_LANGUAGES = mapOf("EN_US" to "en_US", "NL_NL" to "nl")

        const val K_HEIGHT = "keyboard_height"
        const val K_NUMBER_ROW = "number_row"
        const val K_LOUPE_CORNERS = "loupe_corner_choice"
        const val K_CHECK_UPDATES = "check_for_updates"
        const val K_VIBRATE = "vibrate"
        const val K_AUTOCAP = "auto_capitalize"
        const val K_AUTOCORRECT = "autocorrect"
        const val K_STRIP = "suggestion_strip"
        /** Language-only keys from before arrangements; read once to migrate. */
        const val K_LAYOUTS = "enabled_layouts"
        const val K_LAYOUT = "current_layout"
        const val K_KEYBOARDS = "enabled_keyboards"
        const val K_KEYBOARD = "current_keyboard"
        const val K_AUTOSTART = "auto_start_voice"
        const val K_KEYBOARD_FIRST = "keyboard_first_dictation"
        const val K_REMEMBER = "remember_last_mode"
        const val K_LIVE = "live_text"
        const val K_AUTOSTOP = "auto_stop"
        const val K_CONTINUOUS = "continuous"
        const val K_RULES = "apply_phrase_rules"
        const val K_THEME = "theme"
        const val K_TERM_CASE = "match_term_case"
        const val K_SPEECH_LANGUAGE = "speech_language"
        const val K_MODEL_RETENTION = "model_retention"
        const val K_AUDIO_INPUT = "audio_input"
        const val K_LAST_MODE = "last_mode"
        const val K_SHOW_WITH_PHYSICAL = "show_with_physical_keyboard"
        const val K_SCREENSHOTS = "recent_screenshots"
        const val K_MINIMIZED_PHYSICAL = "minimized_with_physical_keyboard"
        const val K_MINIMIZED_TOUCH = "minimized_without_physical_keyboard"
    }
}
