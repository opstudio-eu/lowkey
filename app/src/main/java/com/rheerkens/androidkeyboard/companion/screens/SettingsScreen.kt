package com.rheerkens.androidkeyboard.companion.screens

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.clipboard.PhotoAccess
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.dictionary.LanguagePackState
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.models.SpeechLanguage
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.models.speechLanguageCode
import com.rheerkens.androidkeyboard.settings.AutoStop
import com.rheerkens.androidkeyboard.settings.KeyArrangement
import com.rheerkens.androidkeyboard.settings.BottomKey
import com.rheerkens.androidkeyboard.settings.LoupeCorners
import com.rheerkens.androidkeyboard.settings.ModelRetention
import com.rheerkens.androidkeyboard.settings.Settings
import com.rheerkens.androidkeyboard.settings.ThemeMode
import com.rheerkens.androidkeyboard.ui.SlimIcons
import com.rheerkens.androidkeyboard.updates.acceptsReleaseUpdates

/** Which settings page to show. Each value is its own page; none of them scrolls to an index. */
enum class SettingsFocus { TOP, KEYBOARDS, VOICE, KEYBOARD, TYPING, CUSTOMIZE, CLIPBOARD, APPEARANCE, STORAGE, UPDATES }

private typealias Update = ((Settings) -> Settings) -> Unit

@Composable
fun SettingsScreen(modifier: Modifier, focus: SettingsFocus, navigate: (Destination) -> Unit, resizeKeyboard: () -> Unit = {}) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    // Recomposes the layout grid and language summary when a download finishes or a word list is deleted.
    val packs by AppGraph.languagePacks.state.collectAsStateWithLifecycle()
    val keyboardTheme by AppGraph.keyboardTheme.theme.collectAsStateWithLifecycle()
    val update: Update = { t -> AppGraph.settings.update(t) }
    val photos = rememberPhotoAccess()
    val photosGranted = photos.access == PhotoAccess.ALL

    LazyColumn(modifier.fillMaxWidth()) {
        when (focus) {
            SettingsFocus.TOP -> topPage(s, models, photosGranted, keyboardTheme.enabled, navigate)
            SettingsFocus.KEYBOARD -> keyboardPage(s, update, navigate, resizeKeyboard)
            SettingsFocus.TYPING -> typingPage(s, update, navigate)
            SettingsFocus.CUSTOMIZE -> customizePage(s, update)
            SettingsFocus.KEYBOARDS -> item { KeyboardsSetting(s, packs, update, navigate) }
            SettingsFocus.VOICE -> voicePage(s, models, update, navigate)
            SettingsFocus.STORAGE -> item { StoragePage(s, models, update) }
            SettingsFocus.CLIPBOARD -> {
                item {
                    SwitchRow(
                        "Recent screenshots", s.recentScreenshots && photosGranted,
                        { v ->
                            if (v && !photosGranted) photos.request()
                            else update { it.copy(recentScreenshots = v) }
                        },
                        subtitle = "Show screenshots from the last day next to copied clips in the minimized bar. " +
                            if (photosGranted) "Access to all photos is allowed." else "New screenshots need access to all photos.",
                    )
                }
                if (s.recentScreenshots && !photosGranted) item {
                    PhotoAccessCard(photos, { update { it.copy(recentScreenshots = false) } }, Modifier.padding(horizontal = 16.dp))
                }
                item { SectionTitle("Image editor") }
                item {
                    ChoiceRow(
                        "Magnifier position", LoupeCorners.entries, s.loupeCorners, { it.label }, { v -> update { it.copy(loupeCorners = v) } },
                        subtitle = "Where the magnifier sits while you draw: the top corners, the left ones (for a right hand) or the right ones (for a left hand).",
                    )
                }
            }
            SettingsFocus.APPEARANCE -> {
                item {
                    ChoiceRow(
                        "Theme", ThemeMode.entries, s.theme, { it.label }, { v -> update { it.copy(theme = v) } },
                        subtitle = "For the app and the keyboard. System follows your phone's dark theme.",
                    )
                }
                item(key = "keyboard-theme") {
                    LinkRow("Keyboard theme", keyboardThemeSummary(keyboardTheme.enabled)) {
                        navigate(Destination.KEYBOARD_THEME)
                    }
                }
            }
            SettingsFocus.UPDATES -> item { UpdatesSection() }
        }
        item { Spacer(Modifier.padding(12.dp)) }
    }
}

private fun LazyListScope.topPage(
    s: Settings,
    models: ModelStoreState,
    photosGranted: Boolean,
    customKeyboardTheme: Boolean,
    navigate: (Destination) -> Unit,
) {
    item(key = "keyboard") {
        LinkRow("Keyboard", "Size, position, typing and key feedback") { navigate(Destination.SETTINGS_KEYBOARD) }
    }
    item(key = "languages") {
        LinkRow("Languages & layouts", AppGraph.currentKeyboard(s).fullLabel) { navigate(Destination.LANGUAGES) }
    }
    item(key = "voice") {
        LinkRow("Voice", "Opens the Voice tab · " + (models.activeModel?.name ?: "not set up")) { navigate(Destination.MODELS) }
    }
    item(key = "clipboard") {
        LinkRow("Clipboard & images", "Recent screenshots ${onOff(s.recentScreenshots && photosGranted)} · image editor") {
            navigate(Destination.SETTINGS_CLIPBOARD)
        }
    }
    item(key = "appearance") {
        val keyboard = if (customKeyboardTheme) " · custom keyboard colors" else ""
        LinkRow("Appearance", "Theme: ${s.theme.label}$keyboard") { navigate(Destination.SETTINGS_APPEARANCE) }
    }
    item(key = "updates") {
        LinkRow("Updates", if (acceptsReleaseUpdates(BuildConfig.APPLICATION_ID)) "Automatic checks ${onOff(s.checkForUpdates)}" else "Manual APK updates for this preview") { navigate(Destination.SETTINGS_UPDATES) }
    }
    item(key = "help") { SectionTitle("Help and info") }
    item(key = "try") { LinkRow("Try it", "Type and dictate in a test field") { navigate(Destination.TRY_IT) } }
    item(key = "diagnostics") { LinkRow("Diagnostics", "Voice session log and audio input") { navigate(Destination.DIAGNOSTICS) } }
    item(key = "about") { LinkRow("About and licenses") { navigate(Destination.ABOUT) } }
    item(key = "credit") { OpStudioCredit(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
}

private fun LazyListScope.keyboardPage(s: Settings, update: Update, navigate: (Destination) -> Unit, resizeKeyboard: () -> Unit) {
    item {
        LinkRow("Size and position", "Resize and move the keyboard directly", resizeKeyboard)
    }
    item {
        LinkRow("Reset size and position", "Restore default sizes and positions; keep your keyboard modes") {
            update { it.copy(placement = it.placement.resetGeometry()) }
        }
    }
    item {
        SwitchRow("Automatically split on large screens", s.placement.automaticSplit,
            { value -> update { it.copy(placement = it.placement.withAutomaticSplit(value)) } },
            "Use split by default on large screens; remember manual choices")
    }
    item {
        SwitchRow("Number row", s.numberRow, { value -> update { it.copy(numberRow = value) } },
            "Show a dedicated 1–0 row above the letters")
    }
    item {
        ChoiceRow("Key beside Space", BottomKey.entries, s.bottomKey, { it.label },
            { value -> update { it.copy(bottomKey = value) } })
    }
    item {
        LinkRow("Typing", "Capitals ${onOff(s.autoCapitalize)} · autocorrect ${onOff(s.autocorrect)} · suggestions ${onOff(s.suggestionStrip)}") {
            navigate(Destination.SETTINGS_TYPING)
        }
    }
    item { LinkRow("Key feedback", "Vibration ${onOff(s.vibrateOnKeyPress)}") { navigate(Destination.SETTINGS_CUSTOMIZE) } }
    item { SectionTitle("Physical keyboard") }
    item {
        SwitchRow(
            "Show with physical keyboard", s.showWithPhysicalKeyboard, { v -> update { it.copy(showWithPhysicalKeyboard = v) } },
            subtitle = "Keep the keyboard on screen so voice apps like Wispr Flow can appear. It opens minimized; tap the keyboard button to expand it.",
        )
    }
}

private fun LazyListScope.typingPage(s: Settings, update: Update, navigate: (Destination) -> Unit) {
    item {
        SwitchRow(
            "Auto-capitalize", s.autoCapitalize, { v -> update { it.copy(autoCapitalize = v) } },
            subtitle = "Capitalize the start of sentences and of dictated text",
        )
    }
    item {
        SwitchRow(
            "Autocorrect", s.autocorrect, { v -> update { it.copy(autocorrect = v) } },
            subtitle = "When you press space, fix common typos and restore the exact spelling of your saved words",
        )
    }
    item {
        SwitchRow(
            "Suggestion strip", s.suggestionStrip, { v -> update { it.copy(suggestionStrip = v) } },
            subtitle = "Show word suggestions above the keys",
        )
    }
    item { LinkRow("My words", "Words that keep their exact spelling · Dictionary tab") { navigate(Destination.DICTIONARY) } }
}

private fun LazyListScope.customizePage(s: Settings, update: Update) {
    item {
        SwitchRow(
            "Vibrate on key press", s.vibrateOnKeyPress, { v -> update { it.copy(vibrateOnKeyPress = v) } },
            subtitle = "A short vibration for each key you tap",
        )
    }
}

private fun LazyListScope.voicePage(s: Settings, models: ModelStoreState, update: Update, navigate: (Destination) -> Unit) {
    val keysFirst = s.keyboardFirstDictation
    item { SectionTitle("Starting dictation") }
    item {
        SwitchRow(
            "Dictate from the keys", keysFirst, { v -> update { it.copy(keyboardFirstDictation = v) } },
            subtitle = "The keyboard opens with keys. Tap the mic to record; the keys come back once the text is in. " +
                "Off: the mic opens a separate voice mode.",
        )
    }
    item {
        SwitchRow(
            "Start recording right away", s.autoStartVoice, { v -> update { it.copy(autoStartVoice = v) } },
            subtitle = if (keysFirst) "Tapping the mic already starts recording. ${keptChoiceNote(s.autoStartVoice)}"
                else "Start listening as soon as voice mode opens",
            enabled = !keysFirst,
        )
    }
    item {
        SwitchRow(
            "Remember keys or voice", s.rememberLastMode, { v -> update { it.copy(rememberLastMode = v) } },
            subtitle = if (keysFirst) "The keyboard always opens with keys. ${keptChoiceNote(s.rememberLastMode)}"
                else "Reopen the keyboard in keys or voice mode, whichever you used last",
            enabled = !keysFirst,
        )
    }
    item {
        SwitchRow(
            "Keep dictating", s.continuousDictation, { v -> update { it.copy(continuousDictation = v) } },
            subtitle = if (keysFirst) "Each recording ends when the text is in. ${keptChoiceNote(s.continuousDictation)}"
                else "Start a new recording after each insert",
            enabled = !keysFirst,
        )
    }
    item { SectionTitle("While you speak") }
    item {
        SwitchRow(
            "Show words as you speak", s.liveTextInEditor, { v -> update { it.copy(liveTextInEditor = v) } },
            subtitle = "Provisional words appear underlined in the text field until the final text replaces them",
        )
    }
    item {
        ChoiceRow(
            "Stop after silence", AutoStop.entries, s.autoStop, { it.label }, { v -> update { it.copy(autoStop = v) } },
            subtitle = "Recording stops by itself after this much silence. Off: stop it yourself.",
        )
    }
    item { SectionTitle("Language") }
    item { VoiceLanguage(s, models, update) }
    item { SectionTitle("Related") }
    item {
        LinkRow("Dictionary", "Speech rules and the spelling of your words · Dictionary tab") { navigate(Destination.DICTIONARY) }
    }
    item { LinkRow("Storage & performance", "Model memory and space on this device") { navigate(Destination.SETTINGS_STORAGE) } }
}

@Composable
private fun VoiceLanguage(s: Settings, models: ModelStoreState, update: Update) {
    val active = models.activeModel
    val chosen = active?.resolveLanguage(AppGraph.speechLanguage(s))
    if (active != null && chosen != null) {
        val keyboardLanguage = AppGraph.currentLanguage(s)
        val follow = SpeechLanguage(Settings.FOLLOW_KEYBOARD, "Same as keyboard")
        // What following the keyboard gives with this model now, e.g. "German".
        val followed = active.resolveLanguage(speechLanguageCode(Settings.FOLLOW_KEYBOARD, keyboardLanguage))
        DropdownRow(
            "Voice language", listOf(follow) + active.languages,
            if (s.speechLanguage == Settings.FOLLOW_KEYBOARD) follow else chosen,
            { if (it == follow) "${it.label} (${followed?.label ?: chosen.label})" else it.label },
            { v -> update { it.copy(speechLanguage = v.code) } },
            subtitle = "For ${active.name}. Same as keyboard follows the globe key and reloads the model.",
        )
    } else {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Voice language", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (active == null) "Set by the speech model. Choose one on the Voice tab."
                else "${active.language}, set by the model. Multilingual models let you choose here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoragePage(s: Settings, models: ModelStoreState, update: Update) {
    val context = LocalContext.current
    // Recomputed whenever models are installed, deleted, or downloads change.
    val freeBytes = remember(models.installed, models.downloads.keys, models.errors) {
        StatFs(context.filesDir.absolutePath).availableBytes
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        SectionTitle("Model memory")
        ModelMemoryCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        DropdownRow(
            "Keep model loaded", ModelRetention.entries, s.modelRetention, { it.label },
            { v -> update { it.copy(modelRetention = v) } },
            subtitle = "Counts from the end of a dictation, even with keys open. Longer saves loading time but uses memory. " +
                "Android may release it sooner when memory runs low, and closing the app always releases it.",
        )
        SectionTitle("Space on this device")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Speech models: ${models.usedMb} MB · ${countLabel(models.installed.size, "model", "models")} downloaded",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Free space: ${Formatter.formatShortFileSize(context, freeBytes)}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Models download from Hugging Face, pinned to exact versions and checked with SHA-256 checksums. " +
                    "They are stored privately in the app, not in the APK. Each model's source and license are under " +
                    "Details on the Voice tab; delete models you don't use there.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The languages on the phone, each with a chip per letter layout: its own first, then (for
 * Latin-script languages) the alternatives. Every chip that's on is one stop of the globe key's
 * cycle (in the order shown) and a row of the space bar's long-press chooser. Chips sit in a fixed
 * grid, so a checkmark appearing never moves the others. More languages download on their own page.
 */
@Composable
private fun KeyboardsSetting(s: Settings, packs: LanguagePackState, update: Update, navigate: (Destination) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fontScale = LocalDensity.current.fontScale
    // A lazy item stacks its children, so the grid and the link share one column.
    Column {
    val installed = remember(packs.installed) { AppGraph.installedKeyboards() }
    val usableIds = installed.map { it.id }
    DropdownRow(
        "Keyboard in use", installed.filter { it.id in s.enabledKeyboards }, AppGraph.currentKeyboard(s),
        { it.fullLabel }, { keyboard -> update { it.selectKeyboard(keyboard.id, usableIds) } },
        subtitle = "Your choice is remembered. Turn layouts on or off below.",
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            "Turn on the layouts you type with, then choose Keyboard in use above to switch. " +
                "You can also tap the globe key or long-press the space bar while typing. " +
                "Long-press the globe key to switch to another keyboard app.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        val onlyOne = usableIds.count { it in s.enabledKeyboards } == 1
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                fontScale >= 1.6f -> 1
                fontScale >= 1.2f -> 2
                maxWidth >= 560.dp -> KeyArrangement.entries.size
                else -> 3
            }
            Column {
                installed.groupBy { it.language.id }.values.forEach { options ->
                    val language = options.first().language
                    Text(language.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    options.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { keyboard ->
                                val on = keyboard.id in s.enabledKeyboards
                                val required = on && onlyOne
                                FilterChip(
                                    selected = on,
                                    onClick = { update { it.toggleKeyboard(keyboard.id, usableIds) } },
                                    enabled = !required,
                                    // The chip's description names the language too; don't read the label twice.
                                    label = { Text(keyboard.layoutLabel, Modifier.clearAndSetSemantics {}, maxLines = 1) },
                                    leadingIcon = if (on) ({ Icon(SlimIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                                    modifier = Modifier.weight(1f).semantics {
                                        contentDescription = "${language.name}, ${keyboard.layoutLabel}"
                                        if (required) stateDescription = "On, required: the last layout that's on"
                                    },
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (onlyOne) {
            Text("Keep at least one layout on.", style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            "Current keyboard: ${AppGraph.currentKeyboard(s).fullLabel}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "Layouts change only the letter keys; suggestions and autocorrect follow the language.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
    LinkRow(
        "More languages",
        moreLanguagesSummary(AppGraph.languages.languages.filter { !it.bundled && it.id !in packs.installed }.map { it.englishName }),
    ) { navigate(Destination.LANGUAGES) }
    }
}

internal fun onOff(on: Boolean): String = if (on) "on" else "off"

/** Tells that a setting that doesn't apply right now still has its saved value. */
internal fun keptChoiceNote(stored: Boolean): String =
    "Applies in separate voice mode, where it's ${onOff(stored)}."

/** "Download Dutch, Polish, German and 15 more, or delete them", naming what isn't on the phone yet. */
internal fun moreLanguagesSummary(available: List<String>): String = when {
    available.isEmpty() -> "Every language is on the phone; delete the ones you don't use"
    available.size == 1 -> "Download ${available.single()}, or delete languages"
    available.size <= 4 -> "Download ${available.dropLast(1).joinToString(", ")} or ${available.last()}, or delete languages"
    else -> "Download ${available.take(3).joinToString(", ")} and ${available.size - 3} more, or delete languages"
}

/** The Keyboard theme link's summary on the Appearance page. */
internal fun keyboardThemeSummary(custom: Boolean): String =
    if (custom) "Custom colors" else "Standard colors, following the app theme"
