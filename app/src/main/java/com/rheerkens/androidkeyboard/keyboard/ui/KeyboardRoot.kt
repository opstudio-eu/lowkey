package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.KeyboardLayout
import com.rheerkens.androidkeyboard.keyboard.KeyboardState
import com.rheerkens.androidkeyboard.keyboard.KEYBOARD_GRIP_SPACE_DP
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.settings.InputMode
import com.rheerkens.androidkeyboard.voice.VoiceState
import com.rheerkens.androidkeyboard.voice.ui.ModelPicker
import com.rheerkens.androidkeyboard.voice.ui.VOICE_ROW_DP
import com.rheerkens.androidkeyboard.voice.ui.VoiceRow

/** Height of the minimized keyboard, the slim bar shown with a physical keyboard. */
const val MINIMIZED_BAR_DP = 48 + KEYBOARD_GRIP_SPACE_DP

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
    /** The palette to draw with: the standard one for the app theme, or the custom one. */
    val colors: KeyboardColors,
    /** The custom theme's background image, decoded; null for none or when it could not be loaded. */
    val background: KeyboardBackgroundImage? = null,
    /** Show only the slim bar (keys mode, minimized by the user). */
    val minimized: Boolean = false,
    val showExpandHint: Boolean = false,
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
    /** The size to return to when quick settings close. */
    val toolsReturnToMinimized: Boolean = false,
    val splitGapDp: Float = 0f,
    val contentWidthDp: Float = 360f,
    val numberRow: Boolean = false,
    val emojiOpen: Boolean = false,
    val emojiCatalog: com.rheerkens.androidkeyboard.keyboard.EmojiCatalog? = null,
    val recentEmoji: List<String> = emptyList(),
    /** The enabled, installed keyboards in globe order, offered by holding the emoji key. */
    val keyboards: List<com.rheerkens.androidkeyboard.keyboard.TypingKeyboard> = emptyList(),
) {
    /** Keep panel height stable when switching to the existing four-row symbol layouts. */
    val keyRows: Int get() = if (numberRow) 5 else 4
}

/**
 * The microphone stays on the left in both keyboard forms. During dictation its slot becomes
 * Cancel, with Stop and insert at the right. The model picker expands above that voice row.
 */
@Composable
internal fun KeyboardContent(
    state: KeyboardUiState,
    clock: () -> Long,
    actions: KeyboardActions,
    /** False when the placement frame already paints the background (and any image) behind it. */
    drawBackground: Boolean = true,
) {
    val colors = state.colors
    val languageMenu = remember { LanguageMenuState() }
    // A new editor, another panel or a changed keyboard closes the emoji key's language menu.
    LaunchedEffect(state.inputSession, state.mode, state.minimized, state.toolsOpen, state.emojiOpen,
        state.keyboard.keyboard, state.keyboard.layer, state.keyboards, state.pickerOpen) {
        languageMenu.close()
    }
    val more = { actions.onDismissExpandHint(); languageMenu.close(); actions.onOpenTools() }
    // Holding the microphone toggles the tools panel, so TalkBack names the direction.
    val moreLabel = if (state.toolsOpen) "Close keyboard tools" else "Open keyboard tools"
    val talk = {
        actions.onDismissExpandHint()
        if (state.voice == VoiceState.NoPermission) actions.onOpenSetup() else actions.onToggleMode()
    }
    CompositionLocalProvider(LocalKeyboardColors provides colors, LocalWordAim provides state.wordAim) {
        Box(Modifier.onGloballyPositioned { languageMenu.host = it }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (drawBackground) Modifier.keyboardBackground(colors, state.background) else Modifier)
                    // While the language menu waits for a tap, only the menu is reachable with TalkBack.
                    .then(if (languageMenu.open?.sticky == true) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                if (state.minimized) {
                    MinimizedBar(state, actions, talk, more, moreLabel, showHint = state.showExpandHint)
                } else if (state.mode == InputMode.KEYS) {
                    Box(Modifier.fillMaxWidth().height((44 + KEYBOARD_GRIP_SPACE_DP).dp)
                        .keyboardBarSwipe(minimized = false) { actions.onSetMinimized(true) }
                        .semantics {
                            customActions = listOf(CustomAccessibilityAction("Minimize keyboard") {
                                actions.onSetMinimized(true); true
                            })
                        }) {
                        Row(Modifier.fillMaxSize().padding(top = KEYBOARD_GRIP_SPACE_DP.dp), verticalAlignment = Alignment.CenterVertically) {
                            VoiceEntryButton(state.voice, actions, talk, more, moreLabel)
                            SuggestionStrip(state.strip, state.clipHistory, state.inputSession, actions)
                        }
                        KeyboardBarGrip()
                    }
                    if (state.toolsOpen) {
                        KeyboardTools(state, actions)
                    } else if (state.emojiOpen) {
                        EmojiPanel(state.emojiCatalog, state.recentEmoji,
                            (state.keyRows * state.rowHeightDp + 4).dp, actions)
                    } else {
                        KeysPanel(state.layout, state.keyboard, state.rowHeightDp, state.enterKind, actions, state.splitGapDp, state.keyRows,
                            state.keyboards, languageMenu)
                    }
                } else {
                    if (state.pickerOpen) {
                        val keysHeight = 44 + KEYBOARD_GRIP_SPACE_DP + state.keyRows * state.rowHeightDp + 4 // strip + rows + bottom padding
                        ModelPicker(state.models, (keysHeight - VOICE_ROW_DP).dp, actions)
                    }
                    Row(Modifier.fillMaxWidth().height(VOICE_ROW_DP.dp), verticalAlignment = Alignment.CenterVertically) {
                        VoiceRow(state.voice, state.models, state.pickerOpen, clock, actions,
                            widthDp = state.contentWidthDp, fan = state.fan, onMore = more,
                            moreLabel = moreLabel)
                    }
                }
            }
            LanguageMenuOverlay(languageMenu, actions)
        }
    }
}

/**
 * Two fixed controls: microphone and Backspace. Swipe up or use quick settings to expand.
 */
@Composable
private fun MinimizedBar(state: KeyboardUiState, actions: KeyboardActions, talk: () -> Unit,
    more: () -> Unit, moreLabel: String, showHint: Boolean) {
    val c = LocalKeyboardColors.current
    Box(Modifier.fillMaxWidth().height(MINIMIZED_BAR_DP.dp)
        .keyboardBarSwipe(minimized = true) { actions.onSetMinimized(false) }
        .semantics {
            customActions = listOf(CustomAccessibilityAction("Show keyboard") {
                actions.onSetMinimized(false); true
            })
        }) {
        Row(Modifier.fillMaxSize().padding(top = KEYBOARD_GRIP_SPACE_DP.dp), verticalAlignment = Alignment.CenterVertically) {
            VoiceEntryButton(state.voice, actions, talk, more, moreLabel)
            val note = state.strip.note
            if (note != null) {
                Text(note.text, color = if (note.warn) c.amber else c.muted,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
            } else {
                ClipHistoryRow(state.clipHistory, state.inputSession, actions,
                    Modifier.weight(1f).height(48.dp), chipHeight = 36.dp)
            }
            BackspaceKey(actions, Modifier.padding(end = 6.dp).size(width = 56.dp, height = 48.dp),
                sliderInPopup = true, fan = state.fan)
        }
        KeyboardBarGrip()
        if (showHint) {
            AbovePopup {
                PressTarget("Swipe up to show keyboard", onTap = { actions.onSetMinimized(false) }) { pressed ->
                    Text("Swipe up to show keyboard", color = c.keyText, fontSize = 13.sp,
                        modifier = Modifier.background(if (pressed) c.keyPressed else c.popup, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.KeyboardBarGrip() {
    Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(32.dp, 3.dp)
        .background(LocalKeyboardColors.current.toggleVoiceRing, RoundedCornerShape(2.dp)))
}
