package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.models.DeleteResult
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.ModelStoreState

/** A choice alone never starts a download or changes the active model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeechModelPicker(selectedId: String, state: ModelStoreState,
    onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var choice by rememberSaveable { mutableStateOf(selectedId) }
    var details by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val selected = ModelCatalog.byId(choice) ?: ModelCatalog.models.first { it.recommended }
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .84f).dp)) {
            Text("Speech models", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.weight(1f, fill = false).selectableGroup(),
                contentPadding = PaddingValues(horizontal = 20.dp)) {
                listOf(true to "Multilingual", false to "English only").forEach { (multilingual, title) ->
                    item { Text(title, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                    items(ModelCatalog.models.filter { it.languages.isNotEmpty() == multilingual }
                        .sortedByDescending { it.recommended }, key = { it.id }) { model ->
                        Column {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(selected = choice == model.id, role = Role.RadioButton,
                                    onClick = { choice = model.id; details = false; message = null })
                                .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = choice == model.id, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(model.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                                    if (model.recommended) Text("Recommended", color = colors.primary, style = MaterialTheme.typography.bodySmall)
                                    if (model.id in state.installed) Text(if (model.id == state.activeId) "Active" else "Downloaded",
                                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    state.downloads[model.id]?.let {
                                        Text("Downloading \u00b7 ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(model.sizeShort, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            if (model.id in state.downloads) {
                                TextButton(onClick = { AppGraph.models.cancelDownload(model.id) },
                                    modifier = Modifier.align(Alignment.End)) { Text("Cancel download") }
                            } else if (model.id in state.errors) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Download failed", modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall, color = colors.error)
                                    TextButton(onClick = { AppGraph.models.download(model.id) }) { Text("Retry") }
                                }
                            }
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
                item {
                    TextButton(onClick = { details = !details }) { Text(if (details) "Hide model details" else "Model details") }
                    if (details) {
                        Text(selected.description, style = MaterialTheme.typography.bodyMedium)
                        Text("${selected.source}. License: ${selected.license}. Downloads use verified SHA-256 checksums.",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp))
                        if (choice in state.installed) {
                            if (choice == state.activeId) Text("Choose another model before deleting this one.",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                            else TextButton(onClick = {
                                message = when (AppGraph.models.delete(choice)) {
                                    DeleteResult.Deleted -> "Model deleted."
                                    DeleteResult.ActiveModelProtected -> "Choose another model before deleting this one."
                                    DeleteResult.NotInstalled -> null
                                }
                            }) { Text("Delete model", color = colors.error) }
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Button(onClick = { onSelect(choice) }, Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 48.dp)) { Text("Done") }
        }
    }
}
