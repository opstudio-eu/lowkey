package com.rheerkens.androidkeyboard.companion.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.companion.SystemImeStatus
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(modifier: Modifier, navigate: (Destination) -> Unit) {
    val context = LocalContext.current
    var ime by remember { mutableStateOf(SystemImeStatus.read(context)) }
    var mic by remember { mutableStateOf(AppGraph.hasMicPermission(context)) }
    var micDeniedForever by remember { mutableStateOf(false) }
    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    val dict by AppGraph.dictionary.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()

    fun refresh() {
        ime = SystemImeStatus.read(context)
        mic = AppGraph.hasMicPermission(context)
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    // The input-method picker is a dialog over this activity (no onResume); refresh when
    // window focus returns, and once shortly after in case the system applies it late.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        refresh()
        delay(600)
        refresh()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        mic = granted
        val activity = context as? Activity
        micDeniedForever = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    }

    val steps = listOf(ime.enabled, ime.selected, mic, models.activeModel != null)
    val done = steps.count { it }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Text(
            "A slim keyboard with local voice input. Speech runs on this device; nothing you type or say leaves it.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTitle("Setup · $done of 4 done")
        SlimCard(Modifier.padding(horizontal = 16.dp)) {
            StepRow(
                number = 1, title = "Enable Lowkey Keyboard in system settings",
                status = if (ime.enabled) "Enabled" else "Not enabled yet",
                done = ime.enabled, action = "Open settings",
            ) { SystemImeStatus.openInputMethodSettings(context) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            StepRow(
                number = 2, title = "Choose it as the current keyboard",
                status = if (ime.selected) "Current keyboard" else "Current: ${ime.currentLabel ?: "unknown"}",
                done = ime.selected, action = "Choose keyboard", actionEnabled = ime.enabled,
            ) { SystemImeStatus.showPicker(context) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            StepRow(
                number = 3, title = "Allow the microphone",
                status = when {
                    mic -> "Allowed"
                    micDeniedForever -> "Denied. Allow it in app settings."
                    else -> "Needed for voice typing"
                },
                done = mic, action = if (micDeniedForever) "App settings" else "Allow",
            ) {
                if (micDeniedForever) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            StepRow(
                number = 4, title = "Download a speech model",
                status = models.activeModel?.let { "${it.name} active" } ?: "No model installed",
                done = models.activeModel != null, action = "Get a model",
            ) { navigate(Destination.MODELS) }
        }

        SectionTitle("Overview")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SlimCard {
                Text("Try it", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Type and dictate in a playground with multiline, search, and send fields.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { navigate(Destination.TRY_IT) }) { Text("Open Try it") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCard(
                    "Current model",
                    models.activeModel?.name ?: "None",
                    Modifier.weight(1f),
                ) { navigate(Destination.MODELS) }
                InfoCard(
                    "Dictionary",
                    "${dict.terms.size} terms · ${dict.rules.size} rules",
                    Modifier.weight(1f),
                ) { navigate(Destination.DICTIONARY) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCard(
                    "Last mode",
                    settings.lastMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    Modifier.weight(1f),
                ) { navigate(Destination.SETTINGS) }
                InfoCard(
                    "Voice sessions",
                    "${AppGraph.sessionLog.entries.collectAsStateWithLifecycle().value.size} logged",
                    Modifier.weight(1f),
                ) { navigate(Destination.DIAGNOSTICS) }
            }
        }
        Spacer(Modifier.height(8.dp))
        LinkRow("Diagnostics", "Voice session log and audio input") { navigate(Destination.DIAGNOSTICS) }
        LinkRow("About and licenses", "Privacy, references, and licenses") { navigate(Destination.ABOUT) }
    }
}

@Composable
private fun StepRow(
    number: Int,
    title: String,
    status: String,
    done: Boolean,
    action: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = false) {
            contentDescription = "Step $number, $title, ${if (done) "done" else "not done"}"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(
                    if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(SlimIcons.Check, "Done", tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done) {
            FilledTonalButton(onClick = onAction, enabled = actionEnabled) { Text(action) }
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}
