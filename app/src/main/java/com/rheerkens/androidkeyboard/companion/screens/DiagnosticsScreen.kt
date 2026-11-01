package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.settings.AudioInput
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreen(modifier: Modifier) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val entries by AppGraph.sessionLog.entries.collectAsStateWithLifecycle()
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val clips = AppGraph.testClips
    val crashes by AppGraph.crashLog.entries.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Crashes (${crashes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(AppGraph.crashLog.report()))
                        copied = true
                    },
                    enabled = crashes.isNotEmpty(),
                ) { Text(if (copied) "Copied" else "Copy report") }
                OutlinedButton(onClick = { AppGraph.crashLog.clear(); copied = false }, enabled = crashes.isNotEmpty()) { Text("Clear") }
            }
        }
        if (crashes.isEmpty()) {
            item { Note("No crashes recorded.", Modifier.padding(top = 8.dp)) }
        }
        items(crashes, key = { "crash-${it.timeMs}-${it.kind}" }) { c ->
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        if (clips.isNotEmpty()) {
            item { SectionTitle("Audio input") }
            item {
                ChoiceRow(
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
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Voice sessions (${entries.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { AppGraph.sessionLog.clear() }, enabled = entries.isNotEmpty()) { Text("Clear log") }
            }
        }
        if (entries.isEmpty()) {
            item { Note("No voice sessions yet. Open voice mode on the keyboard to start one.", Modifier.padding(top = 8.dp)) }
        }
        items(entries, key = { "${it.sessionId}-${it.startedAtMs}" }) { r ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Session #${r.sessionId}", fontWeight = FontWeight.SemiBold)
                    Text(
                        timeFormat.format(Date(r.startedAtMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(r.transitions.joinToString(" → "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}
