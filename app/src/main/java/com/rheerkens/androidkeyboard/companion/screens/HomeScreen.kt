package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.clipboard.PhotoAccess
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.companion.KeyboardSetupState
import com.rheerkens.androidkeyboard.companion.SystemImeStatus
import com.rheerkens.androidkeyboard.companion.keyboardSetupState
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    modifier: Modifier,
    navigate: (Destination) -> Unit,
    openTest: () -> Unit,
    skipped: Boolean,
    onSkipSetup: () -> Unit,
    onSetupVisibilityChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var ime by androidx.compose.runtime.remember { mutableStateOf(SystemImeStatus.read(context)) }
    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val photos = rememberPhotoAccess()
    val setup = keyboardSetupState(ime.enabled, ime.selected)
    val onboarding = setup != KeyboardSetupState.READY && !skipped
    LifecycleResumeEffect(Unit) { ime = SystemImeStatus.read(context); onPauseOrDispose { } }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        ime = SystemImeStatus.read(context)
        delay(600)
        ime = SystemImeStatus.read(context)
    }
    LaunchedEffect(onboarding) { onSetupVisibilityChanged(onboarding) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        UpdateBanner(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (onboarding) "WELCOME TO LOWKEY" else "YOUR KEYBOARD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    onboarding -> "Your words.\nYour keyboard."
                    setup == KeyboardSetupState.READY -> "Ready to type"
                    setup == KeyboardSetupState.NOT_ENABLED -> "Set up Lowkey"
                    else -> "Choose Lowkey"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                if (onboarding) "Start with typing. Add voice whenever you want."
                else if (setup == KeyboardSetupState.READY) "Type your way. Dictate on your device."
                else "Your keyboard settings are here when you are ready.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!onboarding) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (setup == KeyboardSetupState.READY) SlimIcons.Check else SlimIcons.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        when (setup) {
                            KeyboardSetupState.READY -> "Lowkey is your current keyboard"
                            KeyboardSetupState.NOT_ENABLED -> "Not enabled yet"
                            KeyboardSetupState.NOT_SELECTED -> "Enabled, but not the current keyboard"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = {
                        when (setup) {
                            KeyboardSetupState.READY -> openTest()
                            KeyboardSetupState.NOT_ENABLED -> SystemImeStatus.openInputMethodSettings(context)
                            KeyboardSetupState.NOT_SELECTED -> SystemImeStatus.showPicker(context)
                        }
                    }, modifier = Modifier.fillMaxWidth(),
                ) { Text(if (setup == KeyboardSetupState.READY) "Try your keyboard" else if (ime.enabled) "Choose keyboard" else "Enable Lowkey") }
            }
        }
        if (onboarding) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SetupStep("1", "Enable Lowkey", "Add Lowkey in Android's keyboard settings.", ime.enabled, "Open settings") {
                    SystemImeStatus.openInputMethodSettings(context)
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SetupStep("2", "Choose Lowkey", "Select it as your current keyboard.", ime.selected, "Choose keyboard", ime.enabled) {
                    SystemImeStatus.showPicker(context)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onSkipSetup) { Text("Set up later") }
                Text("Android shows a warning for all keyboard apps. Lowkey processes typing on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            SectionTitle("Make it yours")
            LinkRow("Languages & layouts", "${AppGraph.currentKeyboard(settings).fullLabel} · opens in Settings") { navigate(Destination.LANGUAGES) }
            LinkRow("Voice", models.activeModel?.name ?: "Not set up · optional") { navigate(Destination.MODELS) }
            Note("Typing and speech are processed on your device. Models, keyboard languages and updates use an internet connection.", Modifier.padding(top = 20.dp))
        }
        if (settings.recentScreenshots && photos.access != PhotoAccess.ALL) {
            PhotoAccessCard(photos, { AppGraph.settings.update { it.copy(recentScreenshots = false) } },
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        }
    }
}

@Composable
private fun SetupStep(number: String, title: String, text: String, done: Boolean, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (done) "$title · Done" else "$number. $title", style = MaterialTheme.typography.titleMedium)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!done) FilledTonalButton(onClick = onClick, enabled = enabled) { Text(action) }
    }
}
