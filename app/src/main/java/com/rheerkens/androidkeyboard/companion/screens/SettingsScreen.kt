package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import kotlinx.coroutines.launch
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.clipboard.PhotoAccess
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.models.SpeechLanguage
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.models.speechLanguageCode
import com.rheerkens.androidkeyboard.settings.AutoStop
import com.rheerkens.androidkeyboard.settings.ClipExpiry
import com.rheerkens.androidkeyboard.settings.BottomKey
import com.rheerkens.androidkeyboard.settings.LoupeCorners
import com.rheerkens.androidkeyboard.settings.ModelRetention
import com.rheerkens.androidkeyboard.settings.Settings
import com.rheerkens.androidkeyboard.settings.ThemeMode
import com.rheerkens.androidkeyboard.ui.SlimIcons
import com.rheerkens.androidkeyboard.updates.acceptsReleaseUpdates

/** Which settings page to show. Each value is its own page; none of them scrolls to an index. */
enum class SettingsFocus { TOP, VOICE, KEYBOARD, CLIPBOARD, APPEARANCE }

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
            SettingsFocus.VOICE -> voicePage(s, models, update, navigate)
            SettingsFocus.CLIPBOARD -> {
                item {
                    SettingsGroup("Clipboard") {
                        ChoiceDialogRow(
                            "Keep copied clips", ClipExpiry.entries, s.clipExpiry, { it.label },
                            { v -> update { it.copy(clipExpiry = v) } },
                            subtitle = "Since copied or last pasted. Nothing copied in password or private fields is kept.",
                            detail = { if (it.millis == null) "Kept until Android stops the keyboard" else "Then forgotten, images included" },
                        )
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
                    SwitchRow(
                        "Shaded number row", s.shadeNumberRow, { value -> update { it.copy(shadeNumberRow = value) } },
                        numberRowShadeSummary(keyboardTheme.enabled),
                    )
                }
            }
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
            LinkRow("Keyboard", "Typing, keys, size and position") { navigate(Destination.SETTINGS_KEYBOARD) }
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
    item(key = "experimental") {
        SettingsGroup("Experimental") {
            LinkRow("Experimental", experimentalSummary(s)) { navigate(Destination.SETTINGS_EXPERIMENTAL) }
        }
    }
    item(key = "backup") { BackupSettings() }
    item(key = "app") {
        SettingsGroup("App") {
            LinkRow("Try it", "Test typing and dictation") { navigate(Destination.TRY_IT) }
            LinkRow(
                "About & updates",
                "Version ${BuildConfig.VERSION_NAME} · " +
                    if (acceptsReleaseUpdates(BuildConfig.APPLICATION_ID)) "automatic update checks ${onOff(s.checkForUpdates)}"
                    else "preview build",
            ) { navigate(Destination.ABOUT) }
        }
    }
    item(key = "credit") { OpStudioCredit(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
}

/** Everything about typing on the keys, on one page: what you change most comes first. */
private fun LazyListScope.keyboardPage(s: Settings, update: Update, navigate: (Destination) -> Unit, resizeKeyboard: () -> Unit) {
    item {
        SettingsGroup("Typing") {
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
            LinkRow("My words", "Words that keep their exact spelling") { navigate(Destination.DICTIONARY) }
        }
    }
    item { LearningSettings(s, update) }
    item {
        SettingsGroup("Keys") {
            SwitchRow("Number row", s.numberRow, { value -> update { it.copy(numberRow = value) } }, "A 1–0 row above the letters")
            SwitchRow(
                "Pointing stick", s.pointingStick, { v -> update { it.copy(pointingStick = v) } },
                subtitle = "A red dot on G's corner (on most layouts): slide from it to move the cursor",
            )
            ChoiceDialogRow(
                "Key beside Space", BottomKey.entries, s.bottomKey, { it.label },
                { value -> update { it.copy(bottomKey = value) } },
                detail = { bottomKeyDetail(it) },
            )
            SwitchRow(
                "Vibrate on key press", s.vibrateOnKeyPress, { v -> update { it.copy(vibrateOnKeyPress = v) } },
                subtitle = "A short vibration for each key you tap",
            )
        }
    }
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
        SettingsGroup("Physical keyboard") {
            SwitchRow(
                "Show with a physical keyboard", s.showWithPhysicalKeyboard, { v -> update { it.copy(showWithPhysicalKeyboard = v) } },
                subtitle = "Stays on screen, minimized, so voice input remains at hand. Tap the keyboard button to expand it.",
            )
        }
    }
}

/** Learning from typing (opt-in), and forgetting what was learned. */
@Composable
private fun LearningSettings(s: Settings, update: Update) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    SettingsGroup("Learning") {
        SwitchRow(
            "Learn from my typing", s.learnFromTyping, { v -> update { it.copy(learnFromTyping = v) } },
            subtitle = "Suggests the words you usually type next and remembers corrections you undo. " +
                "Stays on this phone and is never backed up. Nothing is learned in password, private, email or web address fields.",
        )
        ActionRow("Forget what Lowkey learned") { confirm = true }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Forget what Lowkey learned?") },
            text = { Text("This removes the words Lowkey learned from your typing and the corrections you undid. Your saved words stay.") },
            confirmButton = { TextButton(onClick = { AppGraph.personalModel.forget(); confirm = false }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private fun LazyListScope.voicePage(s: Settings, models: ModelStoreState, update: Update, navigate: (Destination) -> Unit) {
    val keysFirst = s.keyboardFirstDictation
    item {
        SettingsGroup(
            "Starting",
            // The settings of the separate voice mode show only once it is in use.
            footer = if (keysFirst) "Turn this off for a separate voice mode, with its own settings for starting and continuing." else null,
        ) {
            SwitchRow(
                "Dictate from the keys", keysFirst, { v -> update { it.copy(keyboardFirstDictation = v) } },
                subtitle = if (keysFirst) "The mic records right on the keyboard; the keys return when the text is in"
                else "The mic opens a separate voice mode",
            )
        }
    }
    if (!keysFirst) item {
        SettingsGroup("Separate voice mode") {
            SwitchRow(
                "Start recording right away", s.autoStartVoice, { v -> update { it.copy(autoStartVoice = v) } },
                subtitle = "Listen as soon as voice mode opens",
            )
            SwitchRow(
                "Remember keys or voice", s.rememberLastMode, { v -> update { it.copy(rememberLastMode = v) } },
                subtitle = "Reopen in the mode you used last",
            )
            SwitchRow(
                "Keep dictating", s.continuousDictation, { v -> update { it.copy(continuousDictation = v) } },
                subtitle = "Start a new recording after each insert",
            )
        }
    }
    item {
        SettingsGroup("While you speak") {
            SwitchRow(
                "Show words as you speak", s.liveTextInEditor, { v -> update { it.copy(liveTextInEditor = v) } },
                subtitle = "At each pause, what you said appears underlined until the final text replaces it",
            )
            ChoiceDialogRow(
                "Stop after silence", AutoStop.entries, s.autoStop, { it.label }, { v -> update { it.copy(autoStop = v) } },
                detail = { if (it == AutoStop.OFF) "You stop recording yourself" else "Stops by itself after this much silence" },
            )
        }
    }
    item { SettingsGroup("Language") { VoiceLanguage(s, models, update) } }
    item { ModelMemory(s, update) }
    item {
        SettingsGroup("Related") {
            LinkRow("Speech models", "Choose, download or delete models") { navigate(Destination.SPEECH_MODELS) }
            LinkRow("Dictionary", "Speech rules and the spelling of your words") { navigate(Destination.DICTIONARY) }
        }
    }
}

/** How long the speech model stays in memory after a dictation, and what is loaded now. */
@Composable
private fun ModelMemory(s: Settings, update: Update) {
    var details by rememberSaveable { mutableStateOf(false) }
    SettingsGroup("Model memory") {
        ChoiceDialogRow(
            "Keep model loaded", ModelRetention.entries, s.modelRetention, { it.label },
            { v -> update { it.copy(modelRetention = v) } },
            subtitle = "Longer starts faster but uses more memory",
        )
        ModelMemoryCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        DisclosureRow("How this works", details, { details = !details })
        if (details) {
            GroupText(
                "The time counts from the end of a dictation, even with the keys open. Android may release " +
                    "the model sooner when memory runs low, and closing the app always releases it.",
            )
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
                if (active == null) "Set by the speech model. Choose one under Voice › Speech models."
                else "${active.language}, set by the model. Multilingual models let you choose here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Saves the saved words, speech rules and settings to a file the user picks, and reads them back,
 * through Android's file picker (no storage permission).
 */
@Composable
private fun BackupSettings() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val export = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(com.rheerkens.androidkeyboard.backup.BackupFile.MIME),
    ) { uri -> if (uri != null) scope.launch { message = AppGraph.exportBackup(context, uri) } }
    val import = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) scope.launch { message = AppGraph.importBackup(context, uri) } }
    SettingsGroup("Backup") {
        ActionRow("Export words and settings") { export.launch(com.rheerkens.androidkeyboard.backup.BackupFile.NAME) }
        ActionRow("Import words and settings") { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        Text(
            message ?: "Your saved words, speech rules and settings, in one file you keep where you like. " +
                "Word lists and speech models download again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
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

internal fun onOff(on: Boolean): String = if (on) "on" else "off"

/** What Shaded number row does; with custom colors, where its color is set. */
internal fun numberRowShadeSummary(customTheme: Boolean): String =
    if (customTheme) "Sets the 1–0 row apart from the letters, in the Number row color of Advanced colors"
    else "Gives the 1–0 row the function-key color, to set it apart from the letters"

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
