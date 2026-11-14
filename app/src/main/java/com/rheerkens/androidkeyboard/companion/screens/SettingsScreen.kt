package com.rheerkens.androidkeyboard.companion.screens

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.ImeAction
import com.rheerkens.androidkeyboard.companion.SettingEntry
import com.rheerkens.androidkeyboard.companion.searchSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
fun SettingsScreen(
    modifier: Modifier,
    focus: SettingsFocus,
    navigate: (Destination) -> Unit,
    resizeKeyboard: () -> Unit = {},
    search: SettingsSearch? = null,
) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    // Recomposes the layout grid and language summary when a download finishes or a word list is deleted.
    val packs by AppGraph.languagePacks.state.collectAsStateWithLifecycle()
    val keyboardTheme by AppGraph.keyboardTheme.theme.collectAsStateWithLifecycle()
    val update: Update = { t -> AppGraph.settings.update(t) }
    val photos = rememberPhotoAccess()
    val photosGranted = photos.access == PhotoAccess.ALL

    val list = rememberLazyListState()
    RevealSettingHighlight(list)
    LazyColumn(modifier.fillMaxWidth(), state = list) {
        when (focus) {
            SettingsFocus.TOP -> {
                if (search != null) searchField(search)
                if (search != null && search.query.isNotBlank()) searchResults(search)
                else topPage(s, models, photosGranted, keyboardTheme.enabled, navigate)
            }
            SettingsFocus.KEYBOARD -> keyboardPage(s, update, navigate, resizeKeyboard)
            SettingsFocus.TYPING -> typingPage(s, update, navigate)
            SettingsFocus.CUSTOMIZE -> customizePage(s, update)
            SettingsFocus.KEYBOARDS -> item { KeyboardsSetting(s, packs, update, navigate) }
            SettingsFocus.VOICE -> voicePage(s, models, update, navigate)
            SettingsFocus.STORAGE -> item { StoragePage(s, models, update, navigate) }
            SettingsFocus.CLIPBOARD -> {
                item {
                    SettingsGroup("Clipboard") {
                        SwitchRow(
                            "Recent screenshots", s.recentScreenshots && photosGranted,
                            { v ->
                                if (v && !photosGranted) photos.request()
                                else update { it.copy(recentScreenshots = v) }
                            },
                            subtitle = "Show screenshots from the last day beside copied clips" +
                                if (photosGranted) "" else ". Needs access to all photos.",
                        )
                    }
                }
                if (s.recentScreenshots && !photosGranted) item {
                    PhotoAccessCard(
                        photos, { update { it.copy(recentScreenshots = false) } },
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item {
                    SettingsGroup("Image editor") {
                        ChoiceDialogRow(
                            "Magnifier position", LoupeCorners.entries, s.loupeCorners, { it.label },
                            { v -> update { it.copy(loupeCorners = v) } },
                            subtitle = "Where the magnifier sits while you draw",
                            detail = { loupeDetail(it) },
                        )
                    }
                }
            }
            SettingsFocus.APPEARANCE -> item {
                SettingsGroup {
                    ChoiceDialogRow(
                        "Theme", ThemeMode.entries, s.theme, { it.label }, { v -> update { it.copy(theme = v) } },
                        subtitle = "For the app and the keyboard",
                        detail = { if (it == ThemeMode.SYSTEM) "Follows your phone's dark theme" else null },
                    )
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

/** The Settings tab's search: the query lives in the app shell, so Back from a result returns to the results. */
class SettingsSearch(val query: String, val onQueryChange: (String) -> Unit, val onOpen: (SettingEntry) -> Unit)

private fun LazyListScope.searchField(search: SettingsSearch) {
    item(key = "search") {
        val keyboard = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = search.query,
            onValueChange = search.onQueryChange,
            placeholder = { Text("Search settings") },
            leadingIcon = { Icon(SlimIcons.Search, null) },
            trailingIcon = if (search.query.isNotEmpty()) ({
                IconButton(onClick = { search.onQueryChange("") }) { Icon(SlimIcons.Close, "Clear search") }
            }) else null,
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            // The placeholder goes once there's a query; TalkBack keeps naming the field.
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { contentDescription = "Search settings" },
        )
    }
}

private fun LazyListScope.searchResults(search: SettingsSearch) {
    val results = searchSettings(search.query)
    item(key = "results") {
        if (results.isEmpty()) {
            EmptyState(
                "No settings found",
                "Nothing matches “${search.query.trim()}”. Try another word, such as dark, vibration or language.",
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Column {
                // Announced as the count changes while typing.
                GroupTitle(countLabel(results.size, "result", "results"), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                SettingsGroup {
                    results.forEach { entry -> LinkRow(entry.title, entry.location) { search.onOpen(entry) } }
                }
            }
        }
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
        SettingsGroup("Keyboard") {
            LinkRow("Keyboard", "Size, number row, typing and key feedback") { navigate(Destination.SETTINGS_KEYBOARD) }
            LinkRow("Languages & layouts", AppGraph.currentKeyboard(s).fullLabel) { navigate(Destination.LANGUAGES) }
            val keyboard = if (customKeyboardTheme) " · custom keyboard colors" else ""
            LinkRow("Appearance", "Theme: ${s.theme.label}$keyboard") { navigate(Destination.SETTINGS_APPEARANCE) }
            LinkRow("Clipboard & images", "Recent screenshots ${onOff(s.recentScreenshots && photosGranted)}") {
                navigate(Destination.SETTINGS_CLIPBOARD)
            }
        }
    }
    item(key = "voice") {
        SettingsGroup("Voice") {
            LinkRow("Voice typing", models.activeModel?.name ?: "Not set up") { navigate(Destination.MODELS) }
        }
    }
    item(key = "app") {
        SettingsGroup("App") {
            LinkRow(
                "Updates",
                if (acceptsReleaseUpdates(BuildConfig.APPLICATION_ID)) "Automatic checks ${onOff(s.checkForUpdates)}"
                else "Preview build · install updates by hand",
            ) { navigate(Destination.SETTINGS_UPDATES) }
            LinkRow("Try it", "Test typing and dictation") { navigate(Destination.TRY_IT) }
            LinkRow("Diagnostics", "Crash reports and voice sessions") { navigate(Destination.DIAGNOSTICS) }
            LinkRow("About", "Version ${BuildConfig.VERSION_NAME} · privacy and licenses") { navigate(Destination.ABOUT) }
        }
    }
    item(key = "credit") { OpStudioCredit(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
}

private fun LazyListScope.keyboardPage(s: Settings, update: Update, navigate: (Destination) -> Unit, resizeKeyboard: () -> Unit) {
    item {
        var confirmReset by rememberSaveable { mutableStateOf(false) }
        SettingsGroup("Size and position") {
            LinkRow("Resize and move", "Adjust it directly on the keyboard", resizeKeyboard)
            SwitchRow(
                "Split on large screens", s.placement.automaticSplit,
                { value -> update { it.copy(placement = it.placement.withAutomaticSplit(value)) } },
                "Your manual choice is remembered",
            )
            ActionRow("Reset size and position") { confirmReset = true }
        }
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Reset size and position?") },
                text = { Text("The keyboard returns to its default sizes and positions. Your keyboard modes stay.") },
                confirmButton = {
                    TextButton(onClick = {
                        update { it.copy(placement = it.placement.resetGeometry()) }
                        confirmReset = false
                    }) { Text("Reset") }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            )
        }
    }
    item {
        SettingsGroup("Keys") {
            SwitchRow("Number row", s.numberRow, { value -> update { it.copy(numberRow = value) } }, "A 1–0 row above the letters")
            ChoiceDialogRow(
                "Key beside Space", BottomKey.entries, s.bottomKey, { it.label },
                { value -> update { it.copy(bottomKey = value) } },
                detail = { bottomKeyDetail(it) },
            )
        }
    }
    item {
        SettingsGroup("Typing and feedback") {
            LinkRow("Typing", "Capitals ${onOff(s.autoCapitalize)} · autocorrect ${onOff(s.autocorrect)} · suggestions ${onOff(s.suggestionStrip)}") {
                navigate(Destination.SETTINGS_TYPING)
            }
            LinkRow("Key feedback", "Vibration ${onOff(s.vibrateOnKeyPress)}") { navigate(Destination.SETTINGS_CUSTOMIZE) }
        }
    }
    item {
        SettingsGroup("Physical keyboard") {
            SwitchRow(
                "Show with a physical keyboard", s.showWithPhysicalKeyboard, { v -> update { it.copy(showWithPhysicalKeyboard = v) } },
                subtitle = "Stays on screen, minimized, so voice input remains at hand. Tap the keyboard button to expand it.",
            )
        }
    }
}

private fun LazyListScope.typingPage(s: Settings, update: Update, navigate: (Destination) -> Unit) {
    item {
        SettingsGroup {
            SwitchRow(
                "Auto-capitalize", s.autoCapitalize, { v -> update { it.copy(autoCapitalize = v) } },
                subtitle = "Capitals at the start of sentences and dictated text",
            )
            SwitchRow(
                "Autocorrect", s.autocorrect, { v -> update { it.copy(autocorrect = v) } },
                subtitle = "When you press space, fix common typos and restore the spelling of your saved words",
            )
            SwitchRow(
                "Suggestion strip", s.suggestionStrip, { v -> update { it.copy(suggestionStrip = v) } },
                subtitle = "Word suggestions above the keys",
            )
        }
    }
    item {
        SettingsGroup {
            LinkRow("My words", "Words that keep their exact spelling") { navigate(Destination.DICTIONARY) }
        }
    }
}

private fun LazyListScope.customizePage(s: Settings, update: Update) {
    item {
        SettingsGroup {
            SwitchRow(
                "Vibrate on key press", s.vibrateOnKeyPress, { v -> update { it.copy(vibrateOnKeyPress = v) } },
                subtitle = "A short vibration for each key you tap",
            )
        }
    }
}

private fun LazyListScope.voicePage(s: Settings, models: ModelStoreState, update: Update, navigate: (Destination) -> Unit) {
    val keysFirst = s.keyboardFirstDictation
    item {
        SettingsGroup("Starting") {
            SwitchRow(
                "Dictate from the keys", keysFirst, { v -> update { it.copy(keyboardFirstDictation = v) } },
                subtitle = if (keysFirst) "The mic records right on the keyboard; the keys return when the text is in"
                else "The mic opens a separate voice mode",
            )
        }
    }
    item {
        SettingsGroup(
            "Separate voice mode",
            footer = if (keysFirst) "Used when Dictate from the keys is off. Your choices here are kept." else null,
        ) {
            SwitchRow(
                "Start recording right away", s.autoStartVoice, { v -> update { it.copy(autoStartVoice = v) } },
                subtitle = "Listen as soon as voice mode opens",
                enabled = !keysFirst,
            )
            SwitchRow(
                "Remember keys or voice", s.rememberLastMode, { v -> update { it.copy(rememberLastMode = v) } },
                subtitle = "Reopen in the mode you used last",
                enabled = !keysFirst,
            )
            SwitchRow(
                "Keep dictating", s.continuousDictation, { v -> update { it.copy(continuousDictation = v) } },
                subtitle = "Start a new recording after each insert",
                enabled = !keysFirst,
            )
        }
    }
    item {
        SettingsGroup("While you speak") {
            SwitchRow(
                "Show words as you speak", s.liveTextInEditor, { v -> update { it.copy(liveTextInEditor = v) } },
                subtitle = "Provisional words appear underlined until the final text replaces them",
            )
            ChoiceDialogRow(
                "Stop after silence", AutoStop.entries, s.autoStop, { it.label }, { v -> update { it.copy(autoStop = v) } },
                detail = { if (it == AutoStop.OFF) "You stop recording yourself" else "Stops by itself after this much silence" },
            )
        }
    }
    item { SettingsGroup("Language") { VoiceLanguage(s, models, update) } }
    item {
        SettingsGroup("Related") {
            LinkRow("Dictionary", "Speech rules and the spelling of your words") { navigate(Destination.DICTIONARY) }
            LinkRow("Storage & performance", "Model memory and space on this device") { navigate(Destination.SETTINGS_STORAGE) }
        }
    }
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
        ChoiceDialogRow(
            "Voice language", listOf(follow) + active.languages,
            if (s.speechLanguage == Settings.FOLLOW_KEYBOARD) follow else chosen,
            { if (it == follow) "${it.label} (${followed?.label ?: chosen.label})" else it.label },
            { v -> update { it.copy(speechLanguage = v.code) } },
            subtitle = "For ${active.name}. Same as keyboard follows the language you type in; switching reloads the model.",
        )
    } else {
        Column(settingHighlight("Voice language").fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
private fun StoragePage(s: Settings, models: ModelStoreState, update: Update, navigate: (Destination) -> Unit) {
    val context = LocalContext.current
    // Recomputed whenever models are installed, deleted, or downloads change.
    val freeBytes = remember(models.installed, models.downloads.keys, models.errors) {
        StatFs(context.filesDir.absolutePath).availableBytes
    }
    var memoryDetails by rememberSaveable { mutableStateOf(false) }
    var downloadDetails by rememberSaveable { mutableStateOf(false) }
    Column {
        ModelMemoryCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        SettingsGroup("Model memory") {
            ChoiceDialogRow(
                "Keep model loaded", ModelRetention.entries, s.modelRetention, { it.label },
                { v -> update { it.copy(modelRetention = v) } },
                subtitle = "Longer starts faster but uses more memory",
            )
            DisclosureRow("How this works", memoryDetails, { memoryDetails = !memoryDetails })
            if (memoryDetails) {
                GroupText(
                    "The time counts from the end of a dictation, even with the keys open. Android may release " +
                        "the model sooner when memory runs low, and closing the app always releases it.",
                )
            }
        }
        SettingsGroup("Space on this device") {
            InfoRow("Speech models", "${models.usedMb} MB · ${countLabel(models.installed.size, "model", "models")}")
            InfoRow("Free space", Formatter.formatShortFileSize(context, freeBytes))
            LinkRow("Manage speech models", "Change or delete models on the Voice tab") { navigate(Destination.MODELS) }
            DisclosureRow("About model downloads", downloadDetails, { downloadDetails = !downloadDetails })
            if (downloadDetails) {
                GroupText(
                    "Models download from Hugging Face, pinned to exact versions and checked with SHA-256 checksums. " +
                        "They are stored privately in the app, not in the APK. Each model's source and license are " +
                        "listed when you choose a model on the Voice tab.",
                )
            }
        }
    }
}

/** A label with its value, for read-only facts. */
@Composable
private fun InfoRow(title: String, value: String) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        if (maxWidth / LocalDensity.current.fontScale < 300.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A row that runs a command (it doesn't open a page), so it has no chevron. */
@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = settingHighlight(title)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

/**
 * The languages on the phone, each with a chip per letter layout: its own first, then (for
 * Latin-script languages) the alternatives. Every chip that's on is one stop of the language
 * switcher (in the order shown). Chips sit in a fixed grid, so a checkmark appearing never moves
 * the others. More languages download on their own page.
 */
@Composable
private fun KeyboardsSetting(s: Settings, packs: LanguagePackState, update: Update, navigate: (Destination) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fontScale = LocalDensity.current.fontScale
    // A lazy item stacks its children, so the groups share one column.
    Column {
        val installed = remember(packs.installed) { AppGraph.installedKeyboards() }
        val usableIds = installed.map { it.id }
        val onlyOne = usableIds.count { it in s.enabledKeyboards } == 1
        SettingsGroup(footer = switchHint(s.bottomKey).takeUnless { onlyOne }) {
            ChoiceDialogRow(
                "Keyboard in use", installed.filter { it.id in s.enabledKeyboards }, AppGraph.currentKeyboard(s),
                { it.fullLabel }, { keyboard -> update { it.selectKeyboard(keyboard.id, usableIds) } },
            )
        }
        SettingsGroup(
            "Layouts you switch between",
            footer = (if (onlyOne) "Keep at least one layout on. " else "") +
                "Layouts change only the letter keys; suggestions and autocorrect follow the language.",
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                val columns = when {
                    fontScale >= 1.6f -> 1
                    fontScale >= 1.2f -> 2
                    maxWidth >= 560.dp -> KeyArrangement.entries.size
                    else -> 3
                }
                Column {
                    installed.groupBy { it.language.id }.values.forEach { options ->
                        val language = options.first().language
                        Text(
                            language.name, style = MaterialTheme.typography.labelLarge, color = muted,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
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
        }
        SettingsGroup {
            LinkRow(
                "Keyboard languages",
                moreLanguagesSummary(AppGraph.languages.languages.filter { !it.bundled && it.id !in packs.installed }.map { it.englishName }),
            ) { navigate(Destination.LANGUAGES) }
        }
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

/**
 * How to switch keyboard languages while typing, for the key beside Space: holding the emoji key
 * opens the language menu; the language-switch (globe) key moves to the next one on a tap.
 */
internal fun switchHint(bottomKey: BottomKey): String = when (bottomKey) {
    BottomKey.EMOJI -> "While typing, hold the emoji key to switch between the layouts that are on."
    BottomKey.LANGUAGE -> "While typing, tap the globe key to switch between the layouts that are on."
}

private fun bottomKeyDetail(key: BottomKey): String = when (key) {
    BottomKey.EMOJI -> "Tap for emoji; hold to switch language"
    BottomKey.LANGUAGE -> "A globe key; tap to switch language"
}

private fun loupeDetail(corners: LoupeCorners): String = when (corners) {
    LoupeCorners.TOP -> "Clear of either hand"
    LoupeCorners.LEFT -> "For drawing with your right hand"
    LoupeCorners.RIGHT -> "For drawing with your left hand"
}
