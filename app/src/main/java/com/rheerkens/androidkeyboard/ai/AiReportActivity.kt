package com.rheerkens.androidkeyboard.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.theme.SlimTheme
import java.io.IOException

/**
 * The keyboard's side of a report opened from a result. Both run in this process. [reporting] is
 * true while this screen is in front, so the keyboard keeps the result until the report is filed
 * or cancelled. [discardImage] returns whether a Pictures/Lowkey copy may still be there.
 */
object AiReportLink {
    @Volatile var reporting: Boolean = false
    var discardImage: ((clipKey: String) -> Boolean)? = null
    var discardImproved: ((session: Int) -> Unit)? = null
}

/**
 * Report a generated image or an improved prompt without leaving Lowkey. Confirming removes it.
 * Email is a separate tap that opens the user's mail app; nothing is sent from here.
 */
class AiReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiReportLink.reporting = true
        AppGraph.init(this)
        val launch = launchOf(intent) ?: run { finish(); return }
        setContent {
            val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
            SlimTheme(settings.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReportForm(launch = launch, address = settings.aiReportEmail, onClose = { finish() })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AiReportLink.reporting = true
    }

    override fun onStop() {
        AiReportLink.reporting = false
        super.onStop()
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_CLIP = "clip"
        private const val EXTRA_SESSION = "session"

        /** In the note field's `privateImeOptions`, so the keyboard doesn't treat it as another app's field. */
        const val FIELD = "lowkey.aiReport"

        fun imageIntent(context: Context, clipKey: String, path: String, mime: String, prompt: String): Intent =
            Intent(context, AiReportActivity::class.java)
                .putExtra(EXTRA_KIND, ReportKind.IMAGE.name)
                .putExtra(EXTRA_CLIP, clipKey)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_MIME, mime)
                .putExtra(EXTRA_TEXT, prompt)

        fun promptIntent(context: Context, session: Int, improved: String): Intent =
            Intent(context, AiReportActivity::class.java)
                .putExtra(EXTRA_KIND, ReportKind.IMPROVED_PROMPT.name)
                .putExtra(EXTRA_SESSION, session)
                .putExtra(EXTRA_TEXT, improved)

        private fun launchOf(intent: Intent): ReportLaunch? {
            val kind = enumValues<ReportKind>().firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: return null
            return ReportLaunch(
                kind = kind,
                text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                path = intent.getStringExtra(EXTRA_PATH),
                mime = intent.getStringExtra(EXTRA_MIME),
                clipKey = intent.getStringExtra(EXTRA_CLIP),
                session = intent.getIntExtra(EXTRA_SESSION, -1),
            )
        }
    }
}

private data class ReportLaunch(
    val kind: ReportKind,
    val text: String,
    val path: String?,
    val mime: String?,
    val clipKey: String?,
    val session: Int,
)

@Composable
private fun ReportForm(launch: ReportLaunch, address: String, onClose: () -> Unit) {
    var reasonName by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var include by rememberSaveable { mutableStateOf(false) }
    var reportId by rememberSaveable { mutableStateOf<String?>(null) }
    var omitted by rememberSaveable { mutableStateOf(false) }
    var leftover by rememberSaveable { mutableStateOf(false) }
    var missing by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var mailError by rememberSaveable { mutableStateOf<String?>(null) }
    val reason = reasonName?.let { name -> ReportReason.entries.firstOrNull { it.name == name } }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (reportId != null) {
            Text("Removed from Lowkey", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Nothing has been sent. The report stays on this phone until you delete it.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (leftover) {
                Text(
                    "A copy in Pictures/Lowkey may still be there.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (missing) {
                Text(
                    "Lowkey couldn't remove it from the keyboard. Clear the clipboard history if it is still there.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (omitted) {
                Text(
                    "The image was too large to keep with the report, so it won't be attached.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                if (ContentReports.isPlaceholder(address)) {
                    "Send opens your email app, addressed to ${ContentReports.addressOrPlaceholder(address)}, " +
                        "a placeholder until Op Studio publishes a real address. You can change it before sending."
                } else {
                    "Send opens your email app. You send the message yourself."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            mailError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = {
                    val report = reportId?.let { AppGraph.reports.find(it) } ?: return@Button
                    val sent = ContentReportMail.send(context, report, AppGraph.reports.imageFile(report), address)
                    mailError = if (sent) null else "No email app is installed. The report stays on this phone."
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send by email") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
            return@Column
        }
        Text(
            if (launch.kind == ReportKind.IMAGE) "Report this image" else "Report this improved prompt",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "This removes it from Lowkey and keeps a report on this phone. Nothing is emailed unless you tap Send afterwards.",
            style = MaterialTheme.typography.bodyLarge,
        )
        ReportReason.entries.forEach { choice ->
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = reason == choice,
                    role = Role.RadioButton,
                    onClick = { reasonName = choice.name },
                ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = reason == choice, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        choice.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(ContentReports.MAX_NOTE) },
            label = { Text("Note, optional") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                platformImeOptions = PlatformImeOptions(AiReportActivity.FIELD),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = include, onCheckedChange = { include = it })
            Text(
                "Include the content in the email, if I send one. Until then it stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    val chosen = reason ?: return@Button
                    val bytes = if (include && launch.kind == ReportKind.IMAGE) {
                        ContentReports.readCacheImage(context.cacheDir, launch.path)
                    } else {
                        null
                    }
                    if (include && launch.kind == ReportKind.IMAGE && bytes == null) {
                        error = "Couldn't read the image, so it wasn't removed. You can report it without including the content."
                        return@Button
                    }
                    val saved = try {
                        AppGraph.reports.add(ReportSubmission(
                            kind = launch.kind,
                            reason = chosen,
                            note = note,
                            includeContent = include,
                            text = launch.text,
                            imageBytes = bytes,
                            imageMime = launch.mime,
                        ))
                    } catch (_: IOException) {
                        error = "Couldn't save the report, so nothing was removed."
                        return@Button
                    }
                    when (launch.kind) {
                        ReportKind.IMAGE -> {
                            val key = launch.clipKey
                            val discard = key?.let { AiReportLink.discardImage }
                            if (key == null || discard == null) missing = true
                            else leftover = discard.invoke(key)
                        }
                        ReportKind.IMPROVED_PROMPT -> AiReportLink.discardImproved?.invoke(launch.session)
                    }
                    omitted = saved.imageOmitted
                    reportId = saved.id
                    error = null
                },
                enabled = reason != null,
                modifier = Modifier.weight(1f),
            ) { Text("Report") }
        }
    }
}
