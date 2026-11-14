package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.R
import com.rheerkens.androidkeyboard.companion.KeyboardSetupState
import com.rheerkens.androidkeyboard.companion.SystemImeStatus
import com.rheerkens.androidkeyboard.companion.theme.isDark

/** First-run introduction. Voice setup remains optional; the action follows real Android IME state. */
@Composable
fun WelcomeScreen(setup: KeyboardSetupState, onSkip: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.isDark
    val choosing = setup == KeyboardSetupState.NOT_SELECTED
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
        val heroHeight = (maxHeight - if (choosing) 338.dp else 294.dp).coerceAtLeast(410.dp)
        val compact = maxWidth < 360.dp
        val sidePadding = if (compact) 20.dp else 24.dp
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy((-28).dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    painterResource(R.drawable.welcome_sky), contentDescription = null,
                    modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(-0.4f, 1f),
                    colorFilter = if (dark) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                        .30f, 0f, 0f, 0f, 0f,
                        0f, .39f, 0f, 0f, 0f,
                        0f, 0f, .56f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ))) else null,
                )
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                    if (dark) listOf(Color(0xB306163A), Color.Transparent)
                    else if (choosing) listOf(Color(0x1A0A4682), Color(0xAD052746))
                    else listOf(Color(0x1A0A4682), Color.Transparent),
                )))
                Column(
                    Modifier.fillMaxWidth().heightIn(min = heroHeight).statusBarsPadding()
                        .padding(start = sidePadding, end = sidePadding, top = 24.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (choosing) {
                        WelcomeBrand()
                        ChooseKeyboardConfirmation(compact)
                    } else {
                        Column {
                            WelcomeBrand()
                            Spacer(Modifier.height(48.dp))
                            Text(
                                "Your voice.\nMore screen.",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = if (compact) 38.sp else 44.sp,
                                    lineHeight = if (compact) 40.sp else 46.sp,
                                    letterSpacing = (-.6).sp,
                                ),
                                color = Color.White, modifier = Modifier.semantics { heading() },
                            )
                        }
                        WelcomeVoicePreview(Modifier.padding(top = 32.dp))
                    }
                }
            }
            Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = colors.surface) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = sidePadding, vertical = 28.dp)) {
                    if (choosing) {
                        ChooseKeyboardInstruction(compact)
                    } else {
                        Text(buildAnnotatedString {
                            append("Dictate in ")
                            withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.SemiBold)) { append("one quiet row") }
                            append(". Download a speech model once and it runs on your phone, offline. Typing works without one.")
                        }, fontSize = 16.sp, lineHeight = 25.sp, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(28.dp))
                    }
                    Button(
                        onClick = {
                            if (setup == KeyboardSetupState.NOT_ENABLED) SystemImeStatus.openInputMethodSettings(context)
                            else SystemImeStatus.showPicker(context)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(28.dp), contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(if (setup == KeyboardSetupState.NOT_SELECTED) "Choose Lowkey" else "Set up Lowkey", fontSize = 17.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Set up later", fontSize = 16.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        // The white system icons must remain legible when large text scrolls the sheet underneath.
        if (scroll.value > 0) {
            Spacer(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(if (dark) Color(0xFF071B35) else Color(0xFF0876B0)))
        }
    }
}

@Composable
private fun WelcomeBrand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.opstudio_logo_blue), null, Modifier.size(44.dp, 35.dp), tint = Color.White)
        Column {
            Text("Lowkey", style = MaterialTheme.typography.titleLarge.copy(fontSize = 27.sp, lineHeight = 30.sp), color = Color.White)
            Text("by OpStudio", fontSize = 13.sp, color = Color.White.copy(alpha = .85f))
        }
    }
}
