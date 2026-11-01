package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.ui.SlimIcons

@Composable
fun ModelMemoryCard(modifier: Modifier = Modifier) {
    val memory by AppGraph.speechEngine.memory.collectAsStateWithLifecycle()
    val model = ModelCatalog.byId(memory.loadedKey?.substringBefore('|'))
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                SlimIcons.Models, null,
                tint = if (model == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        memory.inUse -> "${model?.name ?: "Model"} in use"
                        model != null -> "${model.name} loaded in memory"
                        else -> "No model loaded in memory"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    if (model == null) "Loads when you dictate. Downloads stay installed."
                    else if (memory.inUse) "Recording and transcription stay protected."
                    else "Ready for your next dictation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
