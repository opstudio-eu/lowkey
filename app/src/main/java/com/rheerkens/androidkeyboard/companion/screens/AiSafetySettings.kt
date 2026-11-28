package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.ai.AiDisclosure
import com.rheerkens.androidkeyboard.ai.ContentReport
import com.rheerkens.androidkeyboard.ai.ContentReportSender
import com.rheerkens.androidkeyboard.ai.ContentReports
import com.rheerkens.androidkeyboard.ai.ContentSafetyPolicy
import com.rheerkens.androidkeyboard.ai.ReportDelivery
import com.rheerkens.androidkeyboard.ai.serverHostOf
import com.rheerkens.androidkeyboard.settings.Settings
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings › Experimental: the safety checks and the reports kept on this phone. */
@Composable
internal fun AiSafetySettings(s: Settings) {
    val reports by AppGraph.reports.reports.collectAsStateWithLifecycle()
    val update: ((Settings) -> Settings) -> Unit = { AppGraph.settings.update(it) }
    var sendStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val accepted = AiDisclosure.accepted(s.aiSendNotice, s.aiService, s.aiServerUrl)
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
        ReportEndpointRow(s.aiReportEndpoint) { value -> update { it.copy(aiReportEndpoint = value) } }
        SendNoticeRow(s, accepted)
        GroupText(
            "Flag a result from its card, or from the flag beside Undo on an improved prompt. " +
                "Lowkey removes it and sends the report from this app. If that doesn't go through, the report stays here." +
                (sendStatus?.let { " $it" } ?: ""),
        )
        if (reports.isEmpty()) {
            GroupText("No reports waiting. Up to ${ContentReports.MAX_REPORTS} unsent reports are kept on this phone.")
        } else {
            reports.forEach { report -> ReportRow(report, s.aiReportEndpoint, onStatus = { sendStatus = it }) }
        }
    }
}

@Composable
private fun ReportEndpointRow(endpoint: String, onSave: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ValueRow("Report address", endpoint, warn = ContentReports.isPlaceholder(endpoint)) { open = true }
    if (!open) return
    var text by rememberSaveable { mutableStateOf(endpoint) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Report address") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300); error = null },
                label = { Text("Https address") },
                placeholder = { Text(ContentReports.PLACEHOLDER_ENDPOINT) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                supportingText = {
                    Text(error ?: "A placeholder until a real address is published. Lowkey posts the report itself. No key is sent.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val next = ContentReports.normalizeEndpoint(text)
                if (next == null) error = "Use an https address, with no password or ?options"
                else {
                    onSave(next)
                    open = false
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    )
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
private fun ReportRow(report: ContentReport, endpoint: String, onStatus: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(report.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(report.id) { mutableStateOf(false) }
    var sending by rememberSaveable(report.id) { mutableStateOf(false) }
    val whenText = remember(report.createdAtMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.createdAtMs))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(report.reason.label, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${report.kind.label} · $whenText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (report.note.isNotBlank()) {
            Text(report.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        Row {
            TextButton(
                enabled = !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) { AppGraph.reports.imageFile(report)?.takeIf { it.isFile }?.readBytes() }
                        val result = withContext(Dispatchers.IO) {
                            ContentReportSender().send(
                                ContentReports.endpointOrPlaceholder(endpoint),
                                ContentReports.jsonBody(report, bytes),
                            )
                        }
                        sending = false
                        when (result) {
                            ReportDelivery.Sent -> {
                                AppGraph.reports.delete(report.id)
                                onStatus(null)
                            }
                            is ReportDelivery.Kept -> onStatus(result.message)
                        }
                    }
                },
            ) { Text(if (sending) "Sending…" else "Send again") }
            TextButton(onClick = { editing = true }) { Text(if (report.note.isBlank()) "Add a note" else "Edit note") }
            TextButton(onClick = { deleting = true }) { Text("Delete") }
        }
    }
    if (editing) NoteDialog(report, onClose = { editing = false })
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete this report?") },
            text = { Text("The copy on this phone is removed. This doesn't send anything.") },
            confirmButton = {
                TextButton(onClick = { AppGraph.reports.delete(report.id); deleting = false }) { Text("Delete") }
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
            TextButton(onClick = { AppGraph.reports.updateNote(report.id, text); onClose() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
