package com.rheerkens.androidkeyboard.companion.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.updates.AvailableUpdate
import com.rheerkens.androidkeyboard.updates.UpdateState
import com.rheerkens.androidkeyboard.updates.UpdateStatus
import com.rheerkens.androidkeyboard.updates.Updates
import kotlinx.coroutines.launch

/** Home: shown only while a newer release is known. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val state by AppGraph.updates.state.collectAsStateWithLifecycle()
    if (state.update == null) return
    SlimCard(modifier) { UpdateActions(state) }
}

/** Settings → Updates: the automatic-check switch, the current state, and the update itself. */
@Composable
fun UpdatesSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val state by AppGraph.updates.state.collectAsStateWithLifecycle()
    var canNotify by remember { mutableStateOf(Updates.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Updates.canNotify(context)
        onPauseOrDispose { }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted && Updates.canNotify(context)
        if (!canNotify) {
            // Denied before: only the system settings can allow it now.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    Column {
        SwitchRow(
            "Check for updates automatically", settings.checkForUpdates,
            { v -> AppGraph.settings.update { it.copy(checkForUpdates = v) } },
            subtitle = "About once a day, Lowkey asks its GitHub releases page for the newest version number. " +
                "Nothing about you is sent. The update itself downloads only when you tap Update.",
        )
        if (settings.checkForUpdates && !canNotify && Build.VERSION.SDK_INT >= 33) {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Note("Notifications are off, so new versions show only on the Home page.", Modifier.weight(1f))
                TextButton(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Allow")
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    statusLine(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.status is UpdateStatus.Checking) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(
                    onClick = { scope.launch { AppGraph.updates.check(userStarted = true) } },
                    enabled = !state.busy && state.status !is UpdateStatus.Installing,
                ) { Text("Check now") }
            }
        }
        if (state.update != null) {
            SlimCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { UpdateActions(state) }
        } else if (state.status is UpdateStatus.Failed) {
            Note((state.status as UpdateStatus.Failed).message)
        }
    }
}

private fun statusLine(state: UpdateState): String {
    val checked = state.lastCheckedAt?.let {
        "checked " + DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    }
    val status = when (val s = state.status) {
        UpdateStatus.Checking -> "Checking…"
        UpdateStatus.UpToDate -> "Up to date"
        is UpdateStatus.Failed -> if (s.update == null) "Check failed" else "Lowkey ${s.update.versionName} is available"
        UpdateStatus.Idle -> if (state.lastCheckedAt == null) "Not checked yet" else "Up to date"
        else -> "Lowkey ${state.update?.versionName} is available"
    }
    return listOfNotNull(status, checked).joinToString(" · ")
}

/** Title, progress, and buttons for a known newer release. */
@Composable
private fun UpdateActions(state: UpdateState) {
    val update: AvailableUpdate = state.update ?: return
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var canInstall by remember { mutableStateOf(Updates.canInstall(context)) }
    LifecycleResumeEffect(Unit) {
        canInstall = Updates.canInstall(context)
        onPauseOrDispose { }
    }
    // Android asks once whether Lowkey may install apps; continue the update when it's allowed.
    val allowInstalls = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        canInstall = Updates.canInstall(context)
        if (canInstall) AppGraph.updates.startUpdate()
    }
    fun update() {
        if (Updates.canInstall(context)) {
            AppGraph.updates.startUpdate()
        } else {
            allowInstalls.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
        }
    }

    val status = state.status
    val title = when (status) {
        is UpdateStatus.Downloading -> "Downloading Lowkey ${update.versionName}"
        is UpdateStatus.Installing -> "Installing Lowkey ${update.versionName}"
        else -> "Lowkey ${update.versionName} is available"
    }
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    when (status) {
        is UpdateStatus.Downloading -> {
            LinearProgressIndicator(
                progress = { (status.bytes.toFloat() / update.sizeBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            Text(
                "${status.bytes / MB} of ${update.sizeBytes / MB} MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is UpdateStatus.Installing -> Text(
            "Confirm in Android's installer if it asks. Lowkey closes while it updates; the keyboard keeps " +
                "working, and your settings, dictionary and models stay.",
            style = MaterialTheme.typography.bodySmall,
        )
        is UpdateStatus.Failed -> Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        else -> Text(
            "${update.sizeBytes / MB} MB download. Your settings, dictionary and models stay." +
                if (canInstall) "" else " Android asks once to allow Lowkey to install updates.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            is UpdateStatus.Downloading -> OutlinedButton(onClick = { AppGraph.updates.cancelDownload() }) { Text("Cancel") }
            is UpdateStatus.Installing -> OutlinedButton(onClick = ::update) { Text("Install again") }
            is UpdateStatus.Failed -> Button(onClick = ::update) { Text("Try again") }
            else -> Button(onClick = ::update) { Text("Update") }
        }
        TextButton(onClick = { uri.openUri(update.releasePage) }) { Text("What's new") }
    }
}

private const val MB = 1024 * 1024
