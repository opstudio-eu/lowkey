package com.rheerkens.androidkeyboard.companion.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.ui.SlimIcons

@Composable
fun ModelsScreen(modifier: Modifier, navigate: (Destination) -> Unit, tryKeyboard: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val store = AppGraph.models
    val state by store.state.collectAsStateWithLifecycle()
    var hasMic by remember { mutableStateOf(AppGraph.hasMicPermission(context)) }
    var deniedForever by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable {
        mutableStateOf(state.downloads.keys.firstOrNull { ModelCatalog.byId(it) != null }
            ?: state.activeModel?.takeIf { it.id in state.installed }?.id
            ?: ModelCatalog.models.first { it.recommended }.id)
    }
    LifecycleResumeEffect(Unit) {
        hasMic = AppGraph.hasMicPermission(context)
        if (hasMic) { denied = false; deniedForever = false }
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        denied = !granted
        deniedForever = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    }
    val model = ModelCatalog.byId(selectedId) ?: ModelCatalog.models.first { it.recommended }
    val installed = model.id in state.installed
    val setup = VoiceSetupProgress(hasMic, state, model)
    val modelReady = setup.modelReady
    val step = setup.step
    val showModelManagement = state.installed.isNotEmpty() || state.downloads.isNotEmpty() || state.errors.isNotEmpty()
    val progress = state.downloads[model.id]
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(if (step == 3) "Voice is ready" else "Set up voice",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                modifier = Modifier.padding(bottom = 6.dp).semantics { heading() })
        }
        item {
            VoiceSetupStep(1, if (hasMic) "Microphone" else "Allow microphone", hasMic, step == 1,
                if (hasMic) "Allowed" else null) {
                Text(when {
                    deniedForever -> "Microphone is off. Turn it on in Android settings."
                    denied -> "Microphone is off. You can try again."
                    else -> "Used only while you dictate."
                }, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    if (deniedForever) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)))
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(when { deniedForever -> "Open settings"; denied -> "Try again"; else -> "Allow microphone" })
                }
            }
        }
        item {
            VoiceSetupStep(2, if (modelReady) "Speech model" else "Download speech model", modelReady,
                step == 2 || (hasMic && setup.changingModel) || progress != null,
                setup.activeModel?.let { "${it.name} active" }) {
                Text(model.name + if (installed) "" else " \u00b7 ${model.sizeShort}", fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(model.installedBytes * progress / 1_000_000).toInt()} MB of ${model.sizeShort}",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { store.cancelDownload(model.id) }) { Text("Cancel") }
                } else {
                    Text(if (installed) "Downloaded on this phone." else "Works offline after download.",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    state.errors[model.id]?.let {
                        Text("Download failed: $it", color = colors.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = {
                        if (installed) store.setActive(model.id) else store.download(model.id)
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(when {
                            installed -> "Use this model"
                            model.id in state.errors -> "Retry \u00b7 ${model.sizeShort}"
                            else -> "Download \u00b7 ${model.sizeShort}"
                        })
                    }
                    if (!showModelManagement) TextButton(onClick = { pickerOpen = true }) { Text("Other models") }
                }
            }
        }
        item {
            VoiceSetupStep(3, "Ready to dictate", step == 3, step == 3) {
                Text("Tap the mic on the keyboard.", color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = tryKeyboard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Try the keyboard")
                }
            }
        }
        if (showModelManagement) {
            item { TextButton(onClick = { pickerOpen = true }) { Text(if (modelReady) "Change speech model" else "Other models") } }
            item { LinkRow("Dictation settings", "Language, starting and stopping") { navigate(Destination.SETTINGS_VOICE) } }
            item { LinkRow("Storage & performance", "${state.usedMb} MB on this device") { navigate(Destination.SETTINGS_STORAGE) } }
        } else item {
            Text("Typing works without voice.", color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))
        }
    }
    if (pickerOpen) SpeechModelPicker(model.id, state,
        onSelect = { selectedId = it; pickerOpen = false }, onDismiss = { pickerOpen = false })
}

@Composable
private fun VoiceSetupStep(number: Int, title: String, complete: Boolean, current: Boolean,
    status: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val success = if (colors.isDark) Color(0xFF5CD193) else Color(0xFF1E7A4C)
    Row(Modifier.fillMaxWidth().background(if (current) colors.surfaceContainerLow else Color.Transparent,
        RoundedCornerShape(20.dp)).padding(16.dp).semantics {
            stateDescription = when { complete -> "Done"; current -> "Current step"; else -> "Not started" }
        }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(28.dp).background(when { complete -> success; current -> colors.primary; else -> colors.surfaceContainer }, CircleShape),
            contentAlignment = Alignment.Center) {
            if (complete) Icon(SlimIcons.Check, null, Modifier.size(16.dp), tint = if (colors.isDark) colors.surface else Color.White)
            else Text(number.toString(), color = if (current) colors.onPrimary else colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = if (current || complete) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current || complete) colors.onSurface else colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium)
            if (status != null) Text(status, color = success, style = MaterialTheme.typography.bodyMedium)
            if (current) content()
        }
    }
}
