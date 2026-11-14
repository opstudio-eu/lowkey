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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.updates.AvailableUpdate
import com.rheerkens.androidkeyboard.updates.UpdateState
import com.rheerkens.androidkeyboard.updates.UpdateStatus
import com.rheerkens.androidkeyboard.updates.Updates
import com.rheerkens.androidkeyboard.updates.acceptsReleaseUpdates
import kotlinx.coroutines.launch

/** Home: shown only while a newer release is known. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    if (!acceptsReleaseUpdates(BuildConfig.APPLICATION_ID)) return
    val state by AppGraph.updates.state.collectAsStateWithLifecycle()
    if (state.update == null) return
    SlimCard(modifier) { UpdateActions(state) }
}

/** Settings → Updates: the automatic-check switch, the current state, and the update itself. */
@Composable
fun UpdatesSection() {
    if (!acceptsReleaseUpdates(BuildConfig.APPLICATION_ID)) {
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Version ${BuildConfig.VERSION_NAME} · UI Preview", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Install preview updates by hand. Automatic updates belong to the regular Lowkey app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
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
    val checking = state.status is UpdateStatus.Checking
    val canCheck = !state.busy && state.status !is UpdateStatus.Installing &&
        state.status !is UpdateStatus.AwaitingConfirmation
    fun check() { scope.launch { AppGraph.updates.check(userStarted = true) } }

    Column {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text(statusLine(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.update != null) {
            // The update itself is the main action; checking again stays quiet.
            SlimCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { UpdateActions(state) }
            TextButton(
                onClick = ::check, enabled = canCheck && !checking,
                modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
            ) { Text(if (checking) "Checking…" else "Check again") }
        } else {
            MainAction(if (checking) "Checking…" else "Check for updates", ::check, enabled = canCheck && !checking)
            if (state.status is UpdateStatus.Failed) {
                Note((state.status as UpdateStatus.Failed).message, Modifier.padding(horizontal = 4.dp))
            }
        }
        SettingsGroup("Automatic checks") {
            SwitchRow(
                "Check for updates automatically", settings.checkForUpdates,
                { v -> AppGraph.settings.update { it.copy(checkForUpdates = v) } },
                subtitle = "About once a day, Lowkey asks its GitHub releases page for the newest version number. " +
                    "Nothing about you is sent. An update downloads only when you tap Update.",
            )
            if (settings.checkForUpdates && !canNotify && Build.VERSION.SDK_INT >= 33) {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Note("Notifications are off, so new versions show only on Home.", Modifier.weight(1f))
                    TextButton(
                        onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Allow") }
                }
            }
        }
    }
}

private fun statusLine(state: UpdateState): String {
    val checked = state.lastCheckedAt?.let {
        val now = System.currentTimeMillis()
        if (now - it in 0 until DateUtils.MINUTE_IN_MILLIS) "checked just now"
        else "checked " + DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS)
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
    val working by AppGraph.updates.working.collectAsStateWithLifecycle()
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
    fun confirm() {
        // Android's confirmation screen for the waiting install; start over if it's gone.
        if (!Updates.openConfirmation(context)) update()
    }

    val status = state.status
    val title = when (status) {
        is UpdateStatus.Downloading -> "Downloading Lowkey ${update.versionName}"
        is UpdateStatus.Installing -> "Installing Lowkey ${update.versionName}"
        is UpdateStatus.AwaitingConfirmation -> "Lowkey ${update.versionName} is ready to install"
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
        is UpdateStatus.AwaitingConfirmation -> Text(
            "Tap Install, then confirm in Android's installer. Lowkey closes while it updates; your " +
                "settings, dictionary and models stay.",
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
            // Disabled while the APK is still being handed to the installer, when a retry is ignored.
            is UpdateStatus.Installing -> OutlinedButton(onClick = ::update, enabled = !working) { Text("Install again") }
            is UpdateStatus.AwaitingConfirmation -> Button(onClick = ::confirm) { Text("Install") }
            is UpdateStatus.Failed -> Button(onClick = ::update) { Text("Try again") }
            else -> Button(onClick = ::update) { Text("Update") }
        }
        TextButton(onClick = { uri.openUri(update.releasePage) }) { Text("What's new") }
    }
}

/** Decimal megabytes, as Android's storage settings and GitHub show sizes. */
private const val MB = 1_000_000L
