package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.clipboard.RecentScreenshots
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.settings.AutoStop
import com.rheerkens.androidkeyboard.settings.KeyboardHeight
import com.rheerkens.androidkeyboard.settings.LoupeCorners
import com.rheerkens.androidkeyboard.settings.ModelRetention
import com.rheerkens.androidkeyboard.settings.Settings
import com.rheerkens.androidkeyboard.settings.ThemeMode
import com.rheerkens.androidkeyboard.settings.TypingLayout

private const val VOICE_SECTION_INDEX = 10

@Composable
fun SettingsScreen(modifier: Modifier, focusVoice: Boolean, navigate: (Destination) -> Unit) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    fun update(t: (Settings) -> Settings) = AppGraph.settings.update(t)
    val context = LocalContext.current
    var photosGranted by remember { mutableStateOf(RecentScreenshots(context).hasPermission()) }
    val photosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        photosGranted = granted
        update { it.copy(recentScreenshots = granted) }
    }

    LaunchedEffect(focusVoice) {
        if (focusVoice) listState.scrollToItem(VOICE_SECTION_INDEX)
    }

    LazyColumn(modifier.fillMaxWidth(), state = listState) {
        // Keyboard (items 0–9)
        item { SectionTitle("Keyboard") }
        item {
            ChoiceRow("Keyboard height", KeyboardHeight.entries, s.keyboardHeight, { it.label }, { v -> update { it.copy(keyboardHeight = v) } })
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
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Typing layouts", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "The globe key cycles enabled layouts. Long-press it to switch keyboards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TypingLayout.entries.forEach { layout ->
                    val checked = layout in s.enabledLayouts
                    val onlyOne = checked && s.enabledLayouts.size == 1
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !onlyOne, role = Role.Checkbox) {
                            update { toggleLayout(it, layout) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = !onlyOne)
                        Text(layout.label, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                    }
                }
            }
        }
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
            val chosen = active?.resolveLanguage(s.speechLanguage)
            if (active != null && chosen != null) {
                DropdownRow(
                    "Voice language", active.languages, chosen, { it.label },
                    { v -> update { it.copy(speechLanguage = v.code) } },
                    subtitle = "For ${active.name}. Typing layouts don't change the speech language.",
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
        item { Spacer8() }
    }
}

private fun toggleLayout(s: Settings, layout: TypingLayout): Settings {
    val next = if (layout in s.enabledLayouts) s.enabledLayouts - layout else s.enabledLayouts + layout
    if (next.isEmpty()) return s
    val current = if (s.currentLayout in next) s.currentLayout else next.first()
    return s.copy(enabledLayouts = next, currentLayout = current)
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
