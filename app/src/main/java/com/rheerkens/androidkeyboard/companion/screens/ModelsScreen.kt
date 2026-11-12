package com.rheerkens.androidkeyboard.companion.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.models.DeleteResult
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.ModelInfo
import com.rheerkens.androidkeyboard.models.ModelStoreState

@Composable
fun ModelsScreen(modifier: Modifier, navigate: (Destination) -> Unit) {
    val context = LocalContext.current
    val store = AppGraph.models
    val state by store.state.collectAsStateWithLifecycle()
    var hasMic by remember { mutableStateOf(AppGraph.hasMicPermission(context)) }
    var deniedForever by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) { hasMic = AppGraph.hasMicPermission(context); onPauseOrDispose { } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        deniedForever = !granted && context is Activity &&
            !ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.RECORD_AUDIO)
    }
    LazyColumn(modifier.fillMaxWidth()) {
        item { Note("Dictate on your device. Typing works without voice setup.", Modifier.padding(top = 12.dp)) }
        if (!hasMic) item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Allow the microphone", style = MaterialTheme.typography.titleMedium)
                Text("Needed only for voice typing. Audio is processed on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    if (deniedForever) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(if (deniedForever) "Open app settings" else "Allow microphone") }
            }
        }
        message?.let { msg -> item { Note(msg) } }
        val active = state.activeModel
        item { SectionTitle(if (active == null) "Choose a speech model" else "Active speech model") }
        if (active != null) item(key = "active-${active.id}") { ModelRow(active, state) { message = it } }
        else item { Note("Download a model to start. It becomes active when the download finishes. English-only models cannot transcribe other languages.") }
        item { LinkRow("Dictation settings", "Language, starting and stopping") { navigate(Destination.SETTINGS_VOICE) } }
        val alternatives = ModelCatalog.models.filter { it.id != active?.id }
        val multilingual = alternatives.filter { it.languages.isNotEmpty() }
        val english = alternatives.filter { it.languages.isEmpty() }
        if (multilingual.isNotEmpty()) {
            item { SectionTitle("Multilingual models") }
            items(multilingual, key = { it.id }) { model -> ModelRow(model, state) { message = it } }
        }
        if (english.isNotEmpty()) {
            item { SectionTitle("English-only models") }
            items(english, key = { it.id }) { model -> ModelRow(model, state) { message = it } }
        }
        item { LinkRow("Storage & performance", "${state.usedMb} MB on this device · model memory") { navigate(Destination.SETTINGS_STORAGE) } }
        item { Note("Download on Wi-Fi. Models are stored privately on this device. Larger models can be slower; speed depends on your device.", Modifier.padding(bottom = 20.dp)) }
    }
}

@Composable
private fun ModelRow(model: ModelInfo, state: ModelStoreState, onMessage: (String?) -> Unit) {
    val store = AppGraph.models
    val installed = model.id in state.installed
    val active = model.id == state.activeId
    val progress = state.downloads[model.id]
    var details by rememberSaveable(model.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (model.recommended) Text("Recommended starting point", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(model.name, style = MaterialTheme.typography.titleMedium)
        Text("${model.sizeLabel} · ${model.language}" + if (installed) " · Downloaded" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (active) Text("Active for voice typing", color = MaterialTheme.colorScheme.primary)
        state.errors[model.id]?.let { error ->
            Text("Download failed: $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("${(progress * 100).toInt()}% downloaded", style = MaterialTheme.typography.labelMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                progress != null -> TextButton(onClick = { store.cancelDownload(model.id); onMessage("Download cancelled.") }) { Text("Cancel download") }
                !installed -> Button(
                    onClick = { store.download(model.id); onMessage(null) },
                    modifier = Modifier.semantics { contentDescription = "Download ${model.name}" },
                ) { Text(if (state.errors.containsKey(model.id)) "Retry download & use" else "Download & use") }
                !active -> Button(onClick = { store.setActive(model.id); onMessage("${model.name} is now active.") }) { Text("Use") }
            }
            TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Details") }
        }
        if (details) {
            Text(model.description, style = MaterialTheme.typography.bodyMedium)
            Text("${model.source}. License: ${model.license}. Downloads use pinned versions and verified SHA-256 checksums.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (installed) {
                if (active) Text("Choose another model before deleting this one.", style = MaterialTheme.typography.bodySmall)
                else TextButton(onClick = {
                    when (store.delete(model.id)) {
                        DeleteResult.Deleted -> onMessage("${model.name} deleted.")
                        DeleteResult.ActiveModelProtected -> onMessage("The active model cannot be deleted.")
                        DeleteResult.NotInstalled -> Unit
                    }
                }) { Text("Delete model", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
