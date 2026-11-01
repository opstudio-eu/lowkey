package com.rheerkens.androidkeyboard.companion.screens

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.models.DeleteResult
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.ModelInfo
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.ui.SlimIcons

@Composable
fun ModelsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val store = AppGraph.models
    val state by store.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    // Recomputed whenever models are installed, deleted, or downloads change.
    val freeBytes = remember(state.installed, state.downloads.keys, state.errors) {
        StatFs(context.filesDir.absolutePath).availableBytes
    }

    LazyColumn(modifier.fillMaxWidth()) {
        item { ModelMemoryCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
        item {
            SlimCard(Modifier.padding(16.dp)) {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Models: ${state.usedMb} MB used · ${state.installed.size} installed", style = MaterialTheme.typography.bodyMedium)
                Text("Free space on device: ${Formatter.formatShortFileSize(context, freeBytes)}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Models download from Hugging Face, pinned to exact versions and SHA-256 checksums, and are " +
                        "stored in app-private storage outside the APK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        message?.let { msg ->
            item {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        ModelCatalog.models.groupBy { it.family }.forEach { (family, models) ->
            item(key = "family-$family") { SectionTitle(family) }
            items(models, key = { it.id }) { model ->
                ModelRow(model, state, onMessage = { message = it })
            }
        }
        item {
            Note(
                "Whisper writes the text after you tap Stop. Base is a good start; small and the 1 GB models are " +
                    "more accurate but slower. Download on Wi-Fi. All models are MIT licensed.",
                Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ModelRow(model: ModelInfo, state: ModelStoreState, onMessage: (String?) -> Unit) {
    val store = AppGraph.models
    val installed = model.id in state.installed
    val active = model.id == state.activeId
    val progress = state.downloads[model.id]
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (model.recommended) {
                        Text(
                            "Recommended",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Text(
                    "${model.sizeLabel} · ${model.language} · ${model.license}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                progress != null -> TextButton(
                    onClick = { store.cancelDownload(model.id); onMessage("Download of ${model.name} cancelled") },
                    modifier = Modifier.semantics { contentDescription = "Cancel download of ${model.name}" },
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.tertiary)
                }
                !installed -> Button(
                    onClick = { store.download(model.id); onMessage(null) },
                    modifier = Modifier.semantics { contentDescription = "Download ${model.name}" },
                ) {
                    Icon(SlimIcons.Download, null, Modifier.padding(end = 6.dp))
                    Text("Download")
                }
                active -> AssistChip(onClick = {}, label = { Text("Active") }, leadingIcon = { Icon(SlimIcons.Check, null) })
                else -> OutlinedButton(
                    onClick = { store.setActive(model.id); onMessage("${model.name} is now active") },
                    modifier = Modifier.semantics { contentDescription = "Use ${model.name}" },
                ) { Text("Use") }
            }
            if (installed) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(SlimIcons.More, "More actions for ${model.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            enabled = !active,
                            leadingIcon = { Icon(SlimIcons.Trash, null) },
                            onClick = {
                                menu = false
                                when (store.delete(model.id)) {
                                    DeleteResult.Deleted -> onMessage("${model.name} deleted")
                                    DeleteResult.ActiveModelProtected -> onMessage("The active model can't be deleted.")
                                    DeleteResult.NotInstalled -> Unit
                                }
                            },
                        )
                        if (active) {
                            Text(
                                "The active model can't be deleted. Choose another model first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(220.dp),
                            )
                        }
                    }
                }
            }
        }
        Text(model.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        state.errors[model.id]?.let { error ->
            Text(
                "Download failed: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        if (active) {
            Text(
                "In use for voice typing. Can't be deleted while active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
