package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rheerkens.androidkeyboard.clipboard.PhotoAccess

/** Optional setup, shared by Home and the clipboard setting. */
@Composable
internal fun PhotoAccessCard(photos: PhotoAccessState, onTurnOff: () -> Unit, modifier: Modifier = Modifier) {
    SlimCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add recent screenshots", style = MaterialTheme.typography.titleMedium)
            Text(
                if (photos.access == PhotoAccess.LIMITED)
                    "Only chosen photos are available. New screenshots need access to all photos."
                else "Allow access to all photos to show recent screenshots beside your clipboard clips. Typing works without it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = photos::request) {
                    Text(if (photos.deniedForever) "Open app settings" else "Allow all photos")
                }
                TextButton(onClick = onTurnOff) { Text("Not now") }
            }
        }
    }
}
