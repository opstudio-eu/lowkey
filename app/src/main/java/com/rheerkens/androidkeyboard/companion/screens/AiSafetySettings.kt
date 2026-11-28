package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.ai.AiDisclosure
import com.rheerkens.androidkeyboard.ai.ContentReport
import com.rheerkens.androidkeyboard.ai.ContentReports
import com.rheerkens.androidkeyboard.ai.ReportCopy
import com.rheerkens.androidkeyboard.ai.ReportStatus
import com.rheerkens.androidkeyboard.ai.ContentSafetyPolicy
import com.rheerkens.androidkeyboard.ai.serverHostOf
import com.rheerkens.androidkeyboard.settings.Settings
import java.text.DateFormat
import java.io.IOException
import java.util.Date

/** Settings › Experimental: the safety checks and the content reports. */
@Composable
internal fun AiSafetySettings(s: Settings) {
    val reports by AppGraph.reports.reports.collectAsStateWithLifecycle()
    val update: ((Settings) -> Settings) -> Unit = { AppGraph.settings.update(it) }
    val accepted = AiDisclosure.accepted(s.aiSendNotice, s.aiService, s.aiServerUrl)
    // Opening the page also retries reports that are waiting.
    LaunchedEffect(Unit) { AppGraph.reportUploads.schedule(AppGraph.appScope) }
    SettingsGroup("Safety") {
        if (ContentSafetyPolicy.allowsRelaxing()) {
            SwitchRow(
                "Stricter safety checks",
                s.aiSafetyStrict,
                { value -> update { it.copy(aiSafetyStrict = value) } },
                subtitle = "Also blocks other content the provider's safety check flags, such as graphic violence, hate and adult sexual content. " +
                    "Sexual content involving a child is always blocked.",
            )
        } else {
            GroupText(
                "Safety checks stay on. This Play Store build doesn't offer a way to turn them down. " +
                    "Sexual content involving a child is always blocked.",
            )
        }
        SendNoticeRow(s, accepted)
        Text(
            "Content reports",
            style = MaterialTheme.typography.titleSmall,
            modifier = settingHighlight("Content reports").fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        GroupText(
            "Flag a result from its card, or from the flag beside Undo on an improved prompt. Lowkey removes it and, " +
                "once you confirm, sends the report to Op Studio from inside the app. Without a connection, a report waits " +
                "here and is sent automatically later. The content goes with it only if you include it, and is deleted " +
                "from this phone once the report is sent.",
        )
        if (reports.isEmpty()) {
            GroupText("No reports.")
        } else {
            reports.forEach { report -> ReportRow(report) }
        }
    }
}

@Composable
private fun SendNoticeRow(s: Settings, accepted: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    ValueRow("What a request sends", if (accepted) "Accepted" else "Asks first", warn = !accepted) { open = true }
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("What a request sends") },
        text = { Text(AiDisclosure.review(s.aiService, serverHostOf(s), accepted)) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
    )
}

@Composable
private fun ReportRow(report: ContentReport) {
    var editing by rememberSaveable(report.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(report.id) { mutableStateOf(false) }
    val whenText = remember(report.createdAtMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.createdAtMs))
    }
    val queued = report.status == ReportStatus.QUEUED
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(report.reason.label, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${report.kind.label} · $whenText · ${ReportCopy.statusLabel(report)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (report.note.isNotBlank()) {
            Text(report.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        Row {
            if (queued) {
                TextButton(onClick = { AppGraph.reportUploads.schedule(AppGraph.appScope, onlyId = report.id) }) {
                    Text("Send now")
                }
                TextButton(onClick = { editing = true }) { Text(if (report.note.isBlank()) "Add a note" else "Edit note") }
            }
            TextButton(onClick = { deleting = true }) { Text(if (queued) "Delete" else "Remove") }
        }
    }
    if (editing) NoteDialog(report, onClose = { editing = false })
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(if (queued) "Delete this report?" else "Remove from the list?") },
            text = {
                Text(
                    if (queued) "It hasn't been sent yet. Deleting it here means it won't be sent."
                    else "Op Studio already has it. This only clears it from this phone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try { AppGraph.reports.delete(report.id) } catch (_: IOException) { }
                    deleting = false
                }) { Text(if (queued) "Delete" else "Remove") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoteDialog(report: ContentReport, onClose: () -> Unit) {
    var text by rememberSaveable(report.id) { mutableStateOf(report.note) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ContentReports.MAX_NOTE) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                try { AppGraph.reports.updateNote(report.id, text) } catch (_: IOException) { }
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
