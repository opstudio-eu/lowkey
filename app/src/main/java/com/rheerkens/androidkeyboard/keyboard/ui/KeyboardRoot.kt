package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rheerkens.androidkeyboard.keyboard.KeyboardLayout
import com.rheerkens.androidkeyboard.keyboard.KeyboardState
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.settings.InputMode
import com.rheerkens.androidkeyboard.ui.SlimIcons
import com.rheerkens.androidkeyboard.voice.VoiceState
import com.rheerkens.androidkeyboard.voice.ui.ModelPicker
import com.rheerkens.androidkeyboard.voice.ui.VOICE_ROW_DP
import com.rheerkens.androidkeyboard.voice.ui.VoiceRow

/** Height of the minimized keyboard, the slim bar shown with a physical keyboard. */
const val MINIMIZED_BAR_DP = 48

/** Everything the keyboard UI renders. Produced by the input method service. */
data class KeyboardUiState(
    val mode: InputMode,
    val keyboard: KeyboardState,
    val layout: KeyboardLayout,
    val strip: StripState,
    val voice: VoiceState,
    val models: ModelStoreState,
    val pickerOpen: Boolean,
    val enterKind: EnterKind,
    val rowHeightDp: Int,
    val dark: Boolean,
    /** Show only the slim bar (keys mode, minimized by the user). */
    val minimized: Boolean = false,
    /** Recent clips and screenshots, newest first: the slim bar, and the strip when no word is typed. */
    val clipHistory: List<ClipChip> = emptyList(),
    /** Changes each time the keyboard opens; the clip row then scrolls back to the newest item. */
    val inputSession: Int = 0,
    /** What the minimized bar's Backspace fan offers in this editor. */
    val fan: FanSpec = FanSpec(action = null, newLine = false),
    /** What a Backspace slide into the editor points at; null when not aiming. */
    val wordAim: WordAim? = null,
    val placement: com.rheerkens.androidkeyboard.keyboard.KeyboardPlacement = com.rheerkens.androidkeyboard.keyboard.KeyboardPlacement(rowHeight = rowHeightDp.toFloat()),
    val profile: com.rheerkens.androidkeyboard.keyboard.PlacementProfile = com.rheerkens.androidkeyboard.keyboard.PlacementProfile.COMPACT_PORTRAIT,
    val viewportWidthDp: Float = 360f,
    val viewportHeightDp: Float = 700f,
    val resizeOnOpen: Boolean = false,
    val toolsOpen: Boolean = false,
    val splitGapDp: Float = 0f,
    val contentWidthDp: Float = 360f,
    val numberRow: Boolean = false,
    val emojiOpen: Boolean = false,
    val emojiCatalog: com.rheerkens.androidkeyboard.keyboard.EmojiCatalog? = null,
    val recentEmoji: List<String> = emptyList(),
) {
    /** Keep panel height stable when switching to the existing four-row symbol layouts. */
    val keyRows: Int get() = if (numberRow) 5 else 4
}

/**
 * Concept 4 keyboard: a 44 dp top line (book, three suggestions, toggle) above four key rows,
 * or in voice mode one 56 dp row (model pill, status, Talk/Stop, toggle). The model picker
 * expands upward above the voice row, inside the keyboard, to about keys-mode height.
 * The mic/keyboard toggle stays in the same far-right 44 × 44 slot in both modes.
 * A chevron at the far left minimizes everything to one slim bar whose expand button sits in
 * the same spot.
 */
@Composable
internal fun KeyboardContent(state: KeyboardUiState, clock: () -> Long, actions: KeyboardActions) {
    val colors = if (state.dark) DarkKeyboardColors else LightKeyboardColors
    CompositionLocalProvider(LocalKeyboardColors provides colors, LocalWordAim provides state.wordAim) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.background),
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                if (state.minimized) {
                    MinimizedBar(state.clipHistory, state.inputSession, state.fan, actions)
                } else if (state.mode == InputMode.KEYS) {
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        StripIconButton(SlimIcons.ChevronDown, "Minimize keyboard") { actions.onSetMinimized(true) }
                        StripIconButton(SlimIcons.Tools, "Keyboard tools", onTap = actions::onOpenTools)
                        SuggestionStrip(state.strip, state.clipHistory, state.inputSession, actions)
                        ModeToggle(state.mode, actions)
                    }
                    if (state.toolsOpen) {
                        KeyboardTools(state, actions)
                    } else if (state.emojiOpen) {
                        EmojiPanel(state.emojiCatalog, state.recentEmoji,
                            (state.keyRows * state.rowHeightDp + 4).dp, actions)
                    } else {
                        KeysPanel(state.layout, state.keyboard, state.rowHeightDp, state.enterKind, actions, state.splitGapDp, state.keyRows)
                    }
                } else {
                    if (state.pickerOpen) {
                        val keysHeight = 44 + state.keyRows * state.rowHeightDp + 4 // strip + rows + bottom padding
                        ModelPicker(state.models, (keysHeight - VOICE_ROW_DP).dp, actions)
                    }
                    Row(Modifier.fillMaxWidth().height(VOICE_ROW_DP.dp), verticalAlignment = Alignment.CenterVertically) {
                        VoiceRow(state.voice, state.models, state.pickerOpen, clock, actions, (state.contentWidthDp - 160f).coerceIn(60f, 200f).dp)
                        ModeToggle(state.mode, actions)
                    }
                }
            }
        }
    }
}

/**
 * The whole keyboard while minimized: one slim row with an expand button at the far left, recent
 * clips in the middle (scrolls sideways, newest first), and Backspace at the far right, for
 * corrections while dictating with another app.
 */
@Composable
private fun MinimizedBar(clips: List<ClipChip>, session: Int, fan: FanSpec, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    Row(Modifier.fillMaxWidth().height(MINIMIZED_BAR_DP.dp), verticalAlignment = Alignment.CenterVertically) {
        PressTarget(
            "Show keyboard",
            Modifier.size(width = 58.dp, height = MINIMIZED_BAR_DP.dp),
            onDown = actions::onKeyDown,
            onTap = { actions.onSetMinimized(false) },
        ) { pressed ->
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .size(width = 48.dp, height = 34.dp)
                    .background(if (pressed) c.keyPressed else c.key, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(SlimIcons.Keyboard, null, tint = c.keyText, modifier = Modifier.size(22.dp)) }
        }
        ClipHistoryRow(clips, session, actions, Modifier.weight(1f).height(MINIMIZED_BAR_DP.dp), chipHeight = 34.dp)
        BackspaceKey(
            actions,
            // Inset from the screen corner, where the thumb reaches it more easily.
            Modifier.padding(end = 8.dp).size(width = 64.dp, height = MINIMIZED_BAR_DP.dp),
            sliderInPopup = true,
            fan = fan,
        )
    }
}

@Composable
private fun ModeToggle(mode: InputMode, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val voice = mode == InputMode.VOICE
    PressTarget(
        if (voice) "Switch to keyboard" else "Switch to voice typing",
        Modifier.size(44.dp),
        onDown = actions::onKeyDown,
        onTap = actions::onToggleMode,
    ) { pressed ->
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier
                .size(38.dp)
                .background(
                    when {
                        pressed -> c.keyPressed
                        voice -> c.toggleVoiceBg
                        else -> Color.Transparent
                    },
                    shape,
                )
                .then(if (voice) Modifier.border(1.dp, c.toggleVoiceRing, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (voice) SlimIcons.Keyboard else SlimIcons.Mic, null, tint = c.keyText, modifier = Modifier.size(22.dp))
        }
    }
}
