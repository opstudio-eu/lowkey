package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.companion.KeyboardSetupState
import com.rheerkens.androidkeyboard.companion.SystemImeStatus
import com.rheerkens.androidkeyboard.companion.keyboardSetupState
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.ui.SlimIcons

@Composable
fun HomeScreen(
    modifier: Modifier,
    navigate: (Destination) -> Unit,
    openTest: () -> Unit,
    ime: SystemImeStatus.Status,
) {
    val context = LocalContext.current
    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    var hasMic by remember(context) { mutableStateOf(AppGraph.hasMicPermission(context)) }
    LifecycleResumeEffect(context) {
        hasMic = AppGraph.hasMicPermission(context)
        onPauseOrDispose { }
    }
    val setup = keyboardSetupState(ime.enabled, ime.selected)
    val model = models.activeModel?.takeIf { it.id in models.installed }
    val voiceReady = model != null && hasMic
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    BoxWithConstraints(modifier.background(colors.surface)) {
        val contentHeight = (maxHeight - 132.dp).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy((-24).dp)) {
            HomeHeader(setup)
            Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = colors.surface) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = contentHeight).padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        UpdateBanner()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(12.dp).border(2.dp, colors.primary, CircleShape))
                            Text(if (voiceReady) "VOICE" else "OPTIONAL", color = colors.primary,
                                fontSize = 12.sp, letterSpacing = .5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (voiceReady) "Voice typing is ready" else "Add voice typing",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 34.sp),
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(when {
                            voiceReady -> "Using ${model.name}. Dictation works offline on your phone."
                            model != null -> "Allow microphone access to dictate with ${model.name}."
                            hasMic -> "Download a speech model to dictate offline on your phone."
                            else -> "Needs microphone access and a speech model download. After that, dictation works offline."
                        }, fontSize = 16.sp, lineHeight = 24.sp, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { navigate(Destination.MODELS) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(28.dp)) {
                            Icon(SlimIcons.Mic, null, Modifier.size(22.dp))
                            Text(if (voiceReady) "Voice settings" else "Set up voice",
                                Modifier.padding(start = 10.dp), fontSize = 17.sp)
                        }
                    }
                    Column(Modifier.padding(top = 28.dp)) {
                        Text("KEYBOARD", color = colors.onSurfaceVariant, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                        HomeShortcut(
                            SlimIcons.Keyboard,
                            when (setup) {
                                KeyboardSetupState.READY -> "Try your keyboard"
                                KeyboardSetupState.NOT_ENABLED -> "Enable Lowkey"
                                KeyboardSetupState.NOT_SELECTED -> "Choose Lowkey"
                            },
                            if (setup == KeyboardSetupState.READY) "Test field" else "Finish keyboard setup",
                        ) {
                            when (setup) {
                                KeyboardSetupState.READY -> openTest()
                                KeyboardSetupState.NOT_ENABLED -> SystemImeStatus.openInputMethodSettings(context)
                                KeyboardSetupState.NOT_SELECTED -> SystemImeStatus.showPicker(context)
                            }
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                        HomeShortcut(SlimIcons.Globe, "Languages & layouts", AppGraph.currentKeyboard(settings).fullLabel) {
                            navigate(Destination.LANGUAGES)
                        }
                    }
                }
            }
        }
        if (scroll.value > 0) {
            Spacer(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(if (colors.isDark) Color(0xFF071B35) else Color(0xFF17659C)))
        }
    }
}
