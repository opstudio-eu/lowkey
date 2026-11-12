package com.rheerkens.androidkeyboard.voice.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardActions
import com.rheerkens.androidkeyboard.keyboard.ui.LocalKeyboardColors
import com.rheerkens.androidkeyboard.keyboard.ui.PressTarget
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.models.SpeechLanguage
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.ui.SlimIcons
import com.rheerkens.androidkeyboard.voice.VoiceNotes
import com.rheerkens.androidkeyboard.voice.VoiceOutcome
import com.rheerkens.androidkeyboard.voice.VoiceState
import kotlinx.coroutines.delay

/** Height of the one-row voice panel. */
const val VOICE_ROW_DP = 56

/**
 * One-row voice panel, without the mode toggle (the caller adds it in the far-right slot):
 * model pill, status area (meter and time, spinner, or a fading note), and Talk/Stop.
 */
@Composable
fun RowScope.VoiceRow(
    state: VoiceState,
    models: ModelStoreState,
    pickerOpen: Boolean,
    clock: () -> Long,
    actions: KeyboardActions,
    modelWidth: Dp = 200.dp,
) {
    val c = LocalKeyboardColors.current
    Spacer(Modifier.width(8.dp))
    ModelPill(models, pickerOpen, actions, modelWidth)
    // Status area. While the picker is open it is "outside the list": a tap closes the picker.
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(
                if (pickerOpen) {
                    Modifier.clickable(interactionSource = null, indication = null, onClickLabel = "Close model picker") {
                        actions.onClosePicker()
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is VoiceState.Listening -> {
                    LevelMeter(state.level, c.green)
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = c.muted, trackColor = c.busy, strokeWidth = 2.dp)
                        Text("Loading model", color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        ElapsedTime(state.startedAtMs, state.sessionId, clock)
                        if (state.backlogSeconds >= 2f) {
                            Text("Catching up", color = c.muted, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
                is VoiceState.Finalizing -> {
                    CircularProgressIndicator(Modifier.size(16.dp), color = c.green, trackColor = c.busy, strokeWidth = 2.dp)
                    if (state.backlogSeconds >= 1f) {
                        Text("${state.backlogSeconds.toInt()} s of audio to go", color = c.muted, fontSize = 13.sp, maxLines = 1)
                    }
                }
                is VoiceState.Idle -> state.last?.let { FadingNote(it) }
                VoiceState.NoModel, VoiceState.NoPermission -> Unit
            }
        }
    }
    when (state) {
        is VoiceState.Listening ->
            TalkButton("Stop", "Stop and insert text", SlimIcons.Stop, c.stop, c.stopInk, c.stopPressed, onTap = actions::onStop)
        is VoiceState.Finalizing ->
            TalkButton("Stop", "Finalizing, Stop already pressed", SlimIcons.Stop, c.busy, c.muted, c.busy, onTap = null)
        is VoiceState.Idle, VoiceState.NoModel ->
            TalkButton("Talk", "Talk", SlimIcons.Mic, c.key, c.keyText, c.keyPressed, onTap = actions::onTapToTalk)
        VoiceState.NoPermission ->
            TalkButton("Allow mic", "Allow microphone in Lowkey Keyboard setup", SlimIcons.Mic, c.enter, c.enterInk, c.enterPressed, onTap = actions::onOpenSetup)
    }
    Spacer(Modifier.width(4.dp))
}

/** `● Whisper base ⌄`: a compact pill like T3 Code's composer model selector. */
@Composable
private fun ModelPill(models: ModelStoreState, open: Boolean, actions: KeyboardActions, maxWidth: Dp) {
    val c = LocalKeyboardColors.current
    val model = models.activeModel
    val settings by AppGraph.settings.settings.collectAsState()
    val language = model?.resolveLanguage(AppGraph.speechLanguage(settings))
    val label = when {
        model == null -> "Choose model"
        language == null -> model.name
        language == SpeechLanguage.AUTO -> "${model.name} · Auto"
        else -> "${model.name} · ${language.code.uppercase()}"
    }
    PressTarget(
        if (open) "Close model picker" else "Voice model: $label. Change model",
        Modifier.fillMaxHeight().widthIn(max = maxWidth),
        onTap = actions::onTogglePicker,
    ) { pressed ->
        Row(
            Modifier
                .height(34.dp)
                .background(if (pressed || open) c.keyPressed else c.pill, RoundedCornerShape(17.dp))
                .padding(start = 11.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(if (model != null) c.green else c.amber, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                color = c.keyText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                if (open) SlimIcons.ChevronUp else SlimIcons.ChevronDown, null,
                tint = c.muted, modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Commit/cancel note that fades out after about 2 s (errors later); a notice stays until Talk. */
@Composable
private fun FadingNote(outcome: VoiceOutcome) {
    val c = LocalKeyboardColors.current
    var visible by remember(outcome) { mutableStateOf(true) }
    val isError = outcome is VoiceOutcome.Cancelled && outcome.error != null
    LaunchedEffect(outcome) {
        if (outcome is VoiceOutcome.Notice) return@LaunchedEffect
        delay(if (isError) NOTE_VISIBLE_MS * 3 else NOTE_VISIBLE_MS)
        visible = false
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "note")
    val warn = outcome is VoiceOutcome.Cancelled || outcome is VoiceOutcome.Notice
    Row(Modifier.alpha(alpha), verticalAlignment = Alignment.CenterVertically) {
        if (outcome is VoiceOutcome.Inserted) {
            Icon(SlimIcons.Check, null, tint = c.green, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            VoiceNotes.outcome(outcome),
            color = if (warn) c.amber else c.muted, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ElapsedTime(startedAtMs: Long, sessionId: Long, clock: () -> Long) {
    var now by remember(sessionId) { mutableLongStateOf(clock()) }
    LaunchedEffect(sessionId) {
        while (true) {
            now = clock()
            delay(250)
        }
    }
    Text(VoiceNotes.elapsed(now - startedAtMs), color = LocalKeyboardColors.current.chipText, fontSize = 14.sp, maxLines = 1)
}

@Composable
private fun LevelMeter(level: Float, color: Color) {
    val factors = remember { floatArrayOf(0.55f, 0.9f, 1f, 0.75f, 0.5f) }
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        factors.forEach { f ->
            val h by animateDpAsState((4 + 16 * level * f).dp, tween(180), label = "bar")
            Box(Modifier.width(3.dp).height(h).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun TalkButton(
    label: String,
    description: String,
    icon: ImageVector,
    background: Color,
    ink: Color,
    /** Background while pressed, chosen so [ink] stays readable on it. */
    pressedBackground: Color,
    onTap: (() -> Unit)?,
) {
    PressTarget(description, Modifier.fillMaxHeight().defaultMinSize(minWidth = 84.dp), onTap = onTap) { pressed ->
        Row(
            Modifier
                .height(40.dp)
                .defaultMinSize(minWidth = 84.dp)
                .background(if (pressed && onTap != null) pressedBackground else background, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private const val NOTE_VISIBLE_MS = 2_000L
