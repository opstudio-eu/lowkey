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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardActions
import com.rheerkens.androidkeyboard.keyboard.ui.LocalKeyboardColors
import com.rheerkens.androidkeyboard.keyboard.ui.PressTarget
import com.rheerkens.androidkeyboard.keyboard.ui.VoiceEntryButton
import com.rheerkens.androidkeyboard.keyboard.ui.BackspaceKey
import com.rheerkens.androidkeyboard.keyboard.ui.FanSpec
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.ui.SlimIcons
import com.rheerkens.androidkeyboard.voice.VoiceNotes
import com.rheerkens.androidkeyboard.voice.VoiceOutcome
import com.rheerkens.androidkeyboard.voice.VoiceState
import kotlinx.coroutines.delay

/** Height of the one-row voice panel. */
const val VOICE_ROW_DP = 48

/**
 * The same left-hand microphone as both keyboard forms, replaced by Cancel while recording.
 * Status doubles as the model picker entry; Stop and insert stays at the far right.
 */
@Composable
fun RowScope.VoiceRow(
    state: VoiceState,
    models: ModelStoreState,
    pickerOpen: Boolean,
    clock: () -> Long,
    actions: KeyboardActions,
    widthDp: Float = 360f,
    fan: FanSpec = FanSpec(action = null, newLine = false),
    onMore: () -> Unit = actions::onOpenVoicePicker,
) {
    val c = LocalKeyboardColors.current
    val recording = state is VoiceState.Listening || state is VoiceState.Finalizing
    if (recording) {
        PressTarget("Cancel dictation and return to keyboard", Modifier.width(58.dp).fillMaxHeight(),
            onDown = actions::onKeyDown, onTap = actions::onToggleMode) { pressed ->
            Box(Modifier.size(48.dp, 36.dp),
                contentAlignment = Alignment.Center) {
                Icon(SlimIcons.Close, null, tint = if (pressed) c.enter else c.keyText, modifier = Modifier.size(22.dp))
            }
        }
    } else {
        VoiceEntryButton(state, actions,
            onTap = if (state == VoiceState.NoPermission) actions::onOpenSetup else actions::onTapToTalk,
            onMore = onMore)
    }
    val modelName = models.activeModel?.name ?: "Choose model"
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { contentDescription = "Voice model: $modelName. Change model" }
            .clickable(interactionSource = null, indication = null,
                onClickLabel = if (pickerOpen) "Close model picker" else "Change voice model",
                onClick = actions::onTogglePicker),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is VoiceState.Listening -> {
                    LevelMeter(state.level, c.green)
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = c.muted, trackColor = c.busy, strokeWidth = 2.dp)
                        Text("Loading model", color = c.muted, fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    } else {
                        ElapsedTime(state.startedAtMs, state.sessionId, clock)
                        if (widthDp >= 412f) {
                            Text(if (state.backlogSeconds >= 2f) "Catching up" else modelName,
                                color = c.muted, fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is VoiceState.Finalizing -> {
                    CircularProgressIndicator(Modifier.size(16.dp), color = c.green, trackColor = c.busy, strokeWidth = 2.dp)
                    if (state.backlogSeconds >= 1f) {
                        Text("${state.backlogSeconds.toInt()} s of audio to go", color = c.muted, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                is VoiceState.Idle -> if (state.last != null) FadingNote(state.last) else
                    Text(modelName, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                VoiceState.NoModel -> Text("Choose model", color = c.amber, fontSize = 13.sp)
                VoiceState.NoPermission -> Text("Microphone access needed", color = c.amber,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    when (state) {
        is VoiceState.Listening ->
            AcceptButton("Stop and insert text", onTap = actions::onStop)
        is VoiceState.Finalizing ->
            AcceptButton("Finalizing, Stop already pressed", onTap = null)
        else -> BackspaceKey(actions, Modifier.width(58.dp).fillMaxHeight(), sliderInPopup = true, fan = fan)
    }
    Spacer(Modifier.width(4.dp))
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
private fun AcceptButton(
    description: String,
    onTap: (() -> Unit)?,
) {
    val c = LocalKeyboardColors.current
    PressTarget(description, Modifier.fillMaxHeight().width(58.dp)
        .then(if (onTap == null) Modifier.semantics { disabled() } else Modifier), onTap = onTap) { pressed ->
        Icon(SlimIcons.Check, null, tint = when {
            onTap == null -> c.muted
            pressed -> c.enter
            else -> c.keyText
        }, modifier = Modifier.size(22.dp))
    }
}

private const val NOTE_VISIBLE_MS = 2_000L
