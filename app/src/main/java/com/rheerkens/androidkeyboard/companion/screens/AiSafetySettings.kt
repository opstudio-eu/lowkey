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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.ai.ContentReport
import com.rheerkens.androidkeyboard.ai.ContentReportMail
import com.rheerkens.androidkeyboard.ai.ContentReports
import com.rheerkens.androidkeyboard.ai.ContentSafetyPolicy
import com.rheerkens.androidkeyboard.settings.Settings
import java.text.DateFormat
import java.util.Date

/** Settings › Experimental: the safety checks and the reports kept on this phone. */
@Composable
internal fun AiSafetySettings(s: Settings) {
    val reports by AppGraph.reports.reports.collectAsStateWithLifecycle()
    val update: ((Settings) -> Settings) -> Unit = { AppGraph.settings.update(it) }
    var mailError by rememberSaveable { mutableStateOf<String?>(null) }
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
        ReportEmailRow(s.aiReportEmail) { value -> update { it.copy(aiReportEmail = value) } }
        GroupText(
            "Flag a result from its card, or from the flag beside Undo on an improved prompt. " +
                "Lowkey removes it and keeps the report here. Send opens your email app; nothing is sent until you send that email." +
                (mailError?.let { " $it" } ?: ""),
        )
        if (reports.isEmpty()) {
            GroupText("No reports stored. Up to ${ContentReports.MAX_REPORTS} are kept on this phone.")
        } else {
            reports.forEach { report -> ReportRow(report, s.aiReportEmail, onMailError = { mailError = it }) }
        }
    }
}

@Composable
private fun ReportEmailRow(address: String, onSave: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ValueRow("Report email", address, warn = ContentReports.isPlaceholder(address)) { open = true }
    if (!open) return
    var text by rememberSaveable { mutableStateOf(address) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Report email") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(120); error = null },
                label = { Text("Address") },
                placeholder = { Text(ContentReports.PLACEHOLDER_EMAIL) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                supportingText = {
                    Text(error ?: "A placeholder until Op Studio publishes a real address. Send opens your email app.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val next = ContentReports.normalizeEmail(text)
                if (next == null) error = "That isn't an email address"
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
private fun ReportRow(report: ContentReport, address: String, onMailError: (String?) -> Unit) {
    val context = LocalContext.current
    var editing by rememberSaveable(report.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(report.id) { mutableStateOf(false) }
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
            TextButton(onClick = {
                val sent = ContentReportMail.send(context, report, AppGraph.reports.imageFile(report), address)
                onMailError(if (sent) null else "No email app is installed. The report stays on this phone.")
            }) { Text("Send") }
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
