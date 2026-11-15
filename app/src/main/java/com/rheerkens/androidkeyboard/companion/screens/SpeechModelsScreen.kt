package com.rheerkens.androidkeyboard.companion.screens

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.models.DeleteResult
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.ModelInfo
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.ui.SlimIcons

/**
 * Every speech model in one list, grouped by language support. Each row says whether the model is
 * active, downloaded or not yet on the phone, and offers the matching action: use, download (it
 * becomes active when it finishes), cancel, retry or delete. Tapping a name shows its details.
 */
@Composable
fun SpeechModelsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val state by AppGraph.models.state.collectAsStateWithLifecycle()
    // Refreshed as models are added or removed, and every 5% of a download's progress.
    val downloadSteps = state.downloads.mapValues { (it.value * 20).toInt() }
    val freeBytes = remember(state.installed, state.usedMb, downloadSteps, state.errors) {
        StatFs(context.filesDir.absolutePath).availableBytes
    }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageSummary("${state.usedMb} MB used on this phone · ${Formatter.formatShortFileSize(context, freeBytes)} free. " +
                "A new download becomes active when it finishes.")
        }
        message?.let { item { Note(it, Modifier.padding(horizontal = 4.dp)) } }
        listOf(true to "Multilingual", false to "English only").forEach { (multilingual, title) ->
            item(key = "group-$title") { GroupTitle(title) }
            val group = ModelCatalog.models.filter { it.languages.isNotEmpty() == multilingual }
                .sortedByDescending { it.recommended }
            items(group, key = { it.id }) { model ->
                ModelRow(model, state, onDelete = { deleting = model.id; message = null })
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item {
            Note("Models download from Hugging Face, pinned to exact versions and checked with SHA-256 checksums. " +
                "They are stored privately in the app and work offline.", Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
        }
    }
    deleting?.let { id ->
        val model = ModelCatalog.byId(id)
        if (model == null) { deleting = null; return@let }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${model.name}?") },
            text = { Text("This frees ${model.sizeShort}. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    message = when (AppGraph.models.delete(id)) {
                        DeleteResult.Deleted -> "${model.name} deleted."
                        DeleteResult.ActiveModelProtected -> "Choose another model before deleting ${model.name}."
                        DeleteResult.NotInstalled -> null
                    }
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ModelRow(model: ModelInfo, state: ModelStoreState, onDelete: () -> Unit) {
    val store = AppGraph.models
    val colors = MaterialTheme.colorScheme
    val installed = model.id in state.installed
    val active = installed && model.id == state.activeId
    val progress = state.downloads[model.id]
    val error = state.errors[model.id]
    var details by rememberSaveable(model.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Only the name toggles details, so each action button keeps its own accessible label.
            Column(Modifier.weight(1f).heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClickLabel = if (details) "Hide details" else "Show details") { details = !details }
                .semantics { stateDescription = if (details) "Details shown" else "Details hidden" }
                .padding(start = 20.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.Center) {
                Text(model.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                Text(when {
                    active -> "Active · ${model.sizeShort}"
                    installed -> "On this phone · ${model.sizeShort}"
                    progress != null -> "Downloading · ${(progress * 100).toInt()}% of ${model.sizeShort}"
                    else -> "Not downloaded · ${model.sizeShort}"
                } + if (model.recommended) " · Recommended" else "",
                    color = if (active) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.Medium else null,
                    style = MaterialTheme.typography.bodySmall)
            }
            when {
                active -> Icon(SlimIcons.Check, "Active", Modifier.padding(horizontal = 8.dp).size(22.dp), tint = colors.primary)
                progress != null -> TextButton(onClick = { store.cancelDownload(model.id) }) {
                    Text("Cancel", Modifier.clearAndSetSemantics { contentDescription = "Cancel download of ${model.name}" })
                }
                installed -> {
                    FilledTonalButton(onClick = { store.setActive(model.id) }) {
                        Text("Use", Modifier.clearAndSetSemantics { contentDescription = "Use ${model.name}" })
                    }
                    IconButton(onClick = onDelete, Modifier.offset(x = 8.dp)) {
                        Icon(SlimIcons.Trash, "Delete ${model.name}", tint = colors.onSurfaceVariant)
                    }
                }
                else -> OutlinedButton(onClick = { store.download(model.id) },
                    contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Icon(SlimIcons.Download, null, Modifier.size(18.dp).padding(end = 4.dp))
                    val action = if (error != null) "Retry" else "Download"
                    Text(action, Modifier.clearAndSetSemantics { contentDescription = "$action ${model.name}, ${model.sizeShort}" })
                }
            }
        }
        if (progress != null) LinearProgressIndicator(progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
        if (error != null && progress == null) Text("Download failed: $error", color = colors.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp))
        if (details) Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.description, style = MaterialTheme.typography.bodyMedium)
            Text("${model.language}. ${model.source}. License: ${model.license}.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (active) Text("This is the model you dictate with. Choose another one before deleting it.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
