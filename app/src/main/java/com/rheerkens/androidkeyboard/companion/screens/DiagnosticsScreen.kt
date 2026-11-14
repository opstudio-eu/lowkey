package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.settings.AudioInput
import com.rheerkens.androidkeyboard.ui.SlimIcons
import java.text.DateFormat
import java.util.Date

/**
 * Troubleshooting: a summary first, then the crash reports and voice sessions behind their own
 * rows. Everything shown is already kept on the phone; nothing is recorded or sent from here.
 */
@Composable
fun DiagnosticsScreen(modifier: Modifier) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val entries by AppGraph.sessionLog.entries.collectAsStateWithLifecycle()
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val clips = AppGraph.testClips
    val crashes by AppGraph.crashLog.entries.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var crashesOpen by rememberSaveable { mutableStateOf(false) }
    var sessionsOpen by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val list = rememberLazyListState()
    RevealSettingHighlight(list)
    LazyColumn(modifier.fillMaxWidth(), state = list) {
        item(key = "summary") {
            PageSummary(
                when {
                    crashes.isNotEmpty() -> "Lowkey recorded ${countLabel(crashes.size, "crash", "crashes")}. Copy the report to include it in a bug report."
                    else -> "No crashes recorded. Details stay on this phone until you copy them."
                },
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (crashes.isNotEmpty()) item(key = "copy") {
            MainAction(
                if (copied) "Report copied" else "Copy crash report",
                {
                    clipboard.setText(AnnotatedString(AppGraph.crashLog.report()))
                    copied = true
                },
                icon = if (copied) SlimIcons.Check else SlimIcons.Clipboard,
            )
        }
        item(key = "crashes") {
            SettingsGroup("Crash reports") {
                if (crashes.isEmpty()) {
                    GroupText("None recorded.")
                } else {
                    DisclosureRow(
                        "Show ${countLabel(crashes.size, "report", "reports")}", crashesOpen, { crashesOpen = !crashesOpen },
                        summary = "Latest: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(crashes.first().timeMs))}",
                    )
                    if (crashesOpen) {
                        crashes.forEach { c ->
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${c.kind} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(c.timeMs))}", fontWeight = FontWeight.SemiBold)
                                c.engineWork?.let {
                                    Text("Speech engine: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                if (c.details.isNotBlank()) {
                                    Text(
                                        c.details.lineSequence().take(8).joinToString("\n"),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = muted,
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { AppGraph.crashLog.clear(); copied = false; crashesOpen = false },
                        modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
                    ) { Text("Clear crash reports") }
                }
            }
        }
        item(key = "sessions") {
            SettingsGroup("Voice sessions") {
                if (entries.isEmpty()) {
                    GroupText("None yet. Dictate on the keyboard and each session appears here.")
                } else {
                    DisclosureRow(
                        "Show ${countLabel(entries.size, "session", "sessions")}", sessionsOpen, { sessionsOpen = !sessionsOpen },
                        summary = "Steps, word counts and timing",
                    )
                    if (sessionsOpen) {
                        entries.forEach { r ->
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Session #${r.sessionId}", fontWeight = FontWeight.SemiBold)
                                    Text(timeFormat.format(Date(r.startedAtMs)), style = MaterialTheme.typography.bodySmall, color = muted)
                                }
                                Text(r.transitions.joinToString(" → "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                val last = r.transitions.lastOrNull().orEmpty()
                                val outcomeWords = when {
                                    last.contains("left in field") -> "left in the field"
                                    last.startsWith("cancelled") -> "discarded"
                                    else -> "inserted"
                                }
                                Text(
                                    buildString {
                                        append("${r.words} word${if (r.words == 1) "" else "s"} $outcomeWords")
                                        r.finalizeLatencyMs?.let { append(" · finalize ${"%.1f".format(it / 1000.0)} s") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = muted,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { AppGraph.sessionLog.clear(); sessionsOpen = false },
                        modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
                    ) { Text("Clear session log") }
                }
            }
        }
        if (clips.isNotEmpty()) {
            item(key = "audio") {
                SettingsGroup("Developer") {
                    ChoiceDialogRow(
                        "Dictation audio", AudioInput.entries, s.audioInput, { it.label },
                        { v -> AppGraph.settings.update { it.copy(audioInput = v) } },
                        subtitle = if (s.audioInput == AudioInput.TEST_CLIPS) {
                            "Plays ${clips.size} bundled clips in turn instead of the microphone, for emulators and demos"
                        } else {
                            "Debug builds can replay bundled test clips instead of the microphone"
                        },
                    )
                }
            }
        }
        item(key = "end") { Spacer(Modifier.padding(12.dp)) }
    }
}
