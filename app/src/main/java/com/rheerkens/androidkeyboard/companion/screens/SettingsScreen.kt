package com.rheerkens.androidkeyboard.companion.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.clipboard.RecentScreenshots
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.models.speechLanguageCode
import com.rheerkens.androidkeyboard.dictionary.LanguagePackState
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.settings.AutoStop
import com.rheerkens.androidkeyboard.settings.KeyArrangement
import com.rheerkens.androidkeyboard.settings.LoupeCorners
import com.rheerkens.androidkeyboard.settings.ModelRetention
import com.rheerkens.androidkeyboard.settings.Settings
import com.rheerkens.androidkeyboard.models.SpeechLanguage
import com.rheerkens.androidkeyboard.settings.ThemeMode
import com.rheerkens.androidkeyboard.ui.SlimIcons

private const val KEYBOARDS_INDEX = 8
private const val VOICE_SECTION_INDEX = 10

/** Where Settings opens: at the top, or scrolled to a group the keyboard links to. */
enum class SettingsFocus(val index: Int?) { TOP(null), KEYBOARDS(KEYBOARDS_INDEX), VOICE(VOICE_SECTION_INDEX) }

@Composable
fun SettingsScreen(modifier: Modifier, focus: SettingsFocus, navigate: (Destination) -> Unit, resizeKeyboard: () -> Unit = {}) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    // Recomposes the language summary when a download finishes or a word list is deleted.
    val packs by AppGraph.languagePacks.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    fun update(t: (Settings) -> Settings) = AppGraph.settings.update(t)
    val context = LocalContext.current
    var photosGranted by remember { mutableStateOf(RecentScreenshots(context).hasPermission()) }
    val photosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        photosGranted = granted
        update { it.copy(recentScreenshots = granted) }
    }

    LaunchedEffect(focus) {
        focus.index?.let { listState.scrollToItem(it) }
    }

    LazyColumn(modifier.fillMaxWidth(), state = listState) {
        // Keyboard (items 0–9; the layouts are item KEYBOARDS_INDEX)
        item { SectionTitle("Keyboard") }
        item {
            Column {
                LinkRow("Size and position", "Resize and move the keyboard directly", resizeKeyboard)
                LinkRow("Reset size and position", "Restore default sizes and positions; keep your keyboard modes") {
                    update { it.copy(placement = it.placement.resetGeometry()) }
                }
                SwitchRow("Automatically split on large screens", s.placement.automaticSplit,
                    { enabled -> update { it.copy(placement = it.placement.withAutomaticSplit(enabled)) } },
                    "Use split by default on large screens; remember manual choices")
                SwitchRow("Number row", s.numberRow, { enabled -> update { it.copy(numberRow = enabled) } },
                    "Show a dedicated 1–0 row above the letters")
            }
        }
        item { SwitchRow("Key-press vibration", s.vibrateOnKeyPress, { v -> update { it.copy(vibrateOnKeyPress = v) } }) }
        item {
            SwitchRow(
                "Auto-capitalize", s.autoCapitalize, { v -> update { it.copy(autoCapitalize = v) } },
                subtitle = "Capitalize the start of sentences and of dictated text",
            )
        }
        item {
            SwitchRow(
                "Autocorrect", s.autocorrect, { v -> update { it.copy(autocorrect = v) } },
                subtitle = "On space, restore saved terms' exact spelling and fix common typos",
            )
        }
        item { SwitchRow("Suggestion strip", s.suggestionStrip, { v -> update { it.copy(suggestionStrip = v) } }) }
        item {
            SwitchRow(
                "Show with physical keyboard", s.showWithPhysicalKeyboard, { v -> update { it.copy(showWithPhysicalKeyboard = v) } },
                subtitle = "Keep the keyboard on screen so voice apps like Wispr Flow can appear. It opens minimized; tap the keyboard button to expand it.",
            )
        }
        item {
            SwitchRow(
                "Recent screenshots", s.recentScreenshots && photosGranted,
                { v ->
                    if (v && !photosGranted) photosLauncher.launch(RecentScreenshots.PERMISSION)
                    else update { it.copy(recentScreenshots = v) }
                },
                subtitle = "Show screenshots from the last day next to copied clips in the minimized bar. Needs access to photos.",
            )
        }
        item { KeyboardsSetting(s, packs, ::update, navigate) }
        item { Spacer8() }
        // Voice (index VOICE_SECTION_INDEX)
        item { SectionTitle("Voice") }
        item {
            SwitchRow(
                "Keyboard-first dictation", s.keyboardFirstDictation, { v -> update { it.copy(keyboardFirstDictation = v) } },
                subtitle = "Open with keys, tap the mic to record in compact voice mode, then return to keys after transcription",
            )
        }
        item {
            DropdownRow(
                "Keep model loaded", ModelRetention.entries, s.modelRetention, { it.label },
                { v -> update { it.copy(modelRetention = v) } },
                subtitle = "Starts after dictation ends, even with keys open. Longer saves loading time but uses memory. " +
                    "Android may release it sooner under memory pressure. Closing the app means its process ends.",
            )
        }
        item { ModelMemoryCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        item {
            SwitchRow(
                "Auto-start on voice entry", s.autoStartVoice, { v -> update { it.copy(autoStartVoice = v) } },
                subtitle = if (s.keyboardFirstDictation) "Keyboard-first dictation starts recording when you tap the mic"
                    else "Start listening as soon as voice mode opens",
                enabled = !s.keyboardFirstDictation,
            )
        }
        item {
            SwitchRow(
                "Remember last mode", s.rememberLastMode, { v -> update { it.copy(rememberLastMode = v) } },
                subtitle = if (s.keyboardFirstDictation) "Keyboard-first dictation always opens with keys"
                    else "Reopen the keyboard in keys or voice mode, whichever was last",
                enabled = !s.keyboardFirstDictation,
            )
        }
        item {
            SwitchRow(
                "Live text in editor", s.liveTextInEditor, { v -> update { it.copy(liveTextInEditor = v) } },
                subtitle = "Show provisional words as underlined text while you speak",
            )
        }
        item {
            ChoiceRow("Auto-stop after silence", AutoStop.entries, s.autoStop, { it.label }, { v -> update { it.copy(autoStop = v) } })
        }
        item {
            SwitchRow(
                "Continuous dictation", s.continuousDictation && !s.keyboardFirstDictation, { v -> update { it.copy(continuousDictation = v) } },
                subtitle = if (s.keyboardFirstDictation) "Turn off Keyboard-first dictation to keep recording continuously"
                    else "Start a new capture after each insert",
                enabled = !s.keyboardFirstDictation,
            )
        }
        item {
            SwitchRow(
                "Apply phrase rules", s.applyPhraseRules, { v -> update { it.copy(applyPhraseRules = v) } },
                subtitle = "Replace spoken phrases once in the final text, e.g. view model → ViewModel",
            )
        }
        item {
            SwitchRow(
                "Match saved term spelling", s.matchTermCase, { v -> update { it.copy(matchTermCase = v) } },
                subtitle = "Restore the exact case of dictionary terms in dictated text, e.g. t3 code → T3 Code",
            )
        }
        item {
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
                        "${active?.language ?: "English"}, set by the model. Multilingual models let you choose here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            LinkRow("Speech models", models.activeModel?.let { "Active: ${it.name}" } ?: "No model installed") {
                navigate(Destination.MODELS)
            }
        }
        // Appearance
        item { SectionTitle("Appearance") }
        item { ChoiceRow("Theme", ThemeMode.entries, s.theme, { it.label }, { v -> update { it.copy(theme = v) } }) }
        item { SectionTitle("Image editor") }
        item {
            ChoiceRow(
                "Loupe corners", LoupeCorners.entries, s.loupeCorners, { it.label }, { v -> update { it.copy(loupeCorners = v) } },
                subtitle = "Where the magnifier sits while you draw: the top corners, or the left (for a right hand) or right (for a left hand) ones.",
            )
        }
        item { SectionTitle("Updates") }
        item { UpdatesSection() }
        // Links
        item { SectionTitle("More") }
        item { LinkRow("Try it", "Playground for typing and dictation") { navigate(Destination.TRY_IT) } }
        item { LinkRow("Diagnostics", "Voice session log and audio input") { navigate(Destination.DIAGNOSTICS) } }
        item { LinkRow("About and licenses") { navigate(Destination.ABOUT) } }
        item { OpStudioCredit(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
        item { Spacer8() }
    }
}

/**
 * The languages on the phone, each with a chip per letter layout: its own first, then (for
 * Latin-script languages) the alternatives. Every chip that's on is one stop of the globe key's
 * cycle (in the order shown) and a row of the space bar's long-press chooser. Chips sit in a fixed
 * grid, so a checkmark appearing never moves the others. More languages download on their own page.
 */
@Composable
private fun KeyboardsSetting(s: Settings, packs: LanguagePackState, update: ((Settings) -> Settings) -> Unit, navigate: (Destination) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Keyboard layouts", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Turn on the layouts you type with. Turning one on doesn't switch to it: tap the globe key to go " +
                "through them in the order shown, or long-press the space bar to pick one. " +
                "Long-press the globe key to switch to another keyboard app.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        val installed = remember(packs.installed) { AppGraph.installedKeyboards() }
        val usableIds = installed.map { it.id }
        val onlyOne = usableIds.count { it in s.enabledKeyboards } == 1
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 560.dp) KeyArrangement.entries.size else 3
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
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clickable(role = Role.Button) { navigate(Destination.LANGUAGES) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("More languages", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    moreLanguagesSummary(AppGraph.languages.languages.filter { !it.bundled && it.id !in packs.installed }.map { it.englishName }),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            Icon(SlimIcons.ChevronRight, null, tint = muted)
        }
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))

/** "Download Dutch, Polish, German and 15 more, or delete them", naming what isn't on the phone yet. */
internal fun moreLanguagesSummary(available: List<String>): String = when {
    available.isEmpty() -> "Every language is on the phone; delete the ones you don't use"
    available.size == 1 -> "Download ${available.single()}, or delete languages"
    available.size <= 4 -> "Download ${available.dropLast(1).joinToString(", ")} or ${available.last()}, or delete languages"
    else -> "Download ${available.take(3).joinToString(", ")} and ${available.size - 3} more, or delete languages"
}
