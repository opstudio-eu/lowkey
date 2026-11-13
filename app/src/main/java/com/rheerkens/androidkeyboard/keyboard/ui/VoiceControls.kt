package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.rheerkens.androidkeyboard.keyboard.BarSwipe
import com.rheerkens.androidkeyboard.keyboard.barSwipe
import com.rheerkens.androidkeyboard.ui.SlimIcons
import com.rheerkens.androidkeyboard.voice.VoiceState

/** Shared, icon-only entry point in the compact bar and the expanded keyboard's top row. */
@Composable
fun VoiceEntryButton(state: VoiceState, actions: KeyboardActions, onTap: () -> Unit, onMore: () -> Unit) {
    val c = LocalKeyboardColors.current
    val permission = state == VoiceState.NoPermission
    PressTarget("Switch to voice typing", Modifier.width(58.dp).fillMaxHeight().semantics {
        stateDescription = when (state) {
            VoiceState.NoPermission -> "Microphone permission needed"
            VoiceState.NoModel -> "No model selected"
            else -> "Ready"
        }
    }, onDown = actions::onKeyDown,
        onTap = onTap, onLongPress = onMore) { pressed ->
        Box(Modifier.padding(start = 4.dp).size(48.dp, 36.dp), contentAlignment = Alignment.Center) {
            Icon(SlimIcons.Mic, null, tint = when {
                pressed -> c.enter
                permission -> c.muted
                state == VoiceState.NoModel -> c.amber
                else -> c.keyText
            }, modifier = Modifier.size(22.dp))
        }
    }
}
/** Vertical size gestures yield immediately to horizontal scrolling, and never steal Backspace. */
@Composable
internal fun Modifier.keyboardBarSwipe(minimized: Boolean, onToggle: () -> Unit): Modifier {
    val toggle by rememberUpdatedState(onToggle)
    return pointerInput(minimized) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (minimized && down.position.x >= size.width - 72.dp.toPx()) return@awaitEachGesture
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    ?: break
                val delta = change.position - down.position
                val held = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                when (barSwipe(delta.x, delta.y, minimized, 24.dp.toPx(), held,
                    horizontalSlop = viewConfiguration.touchSlop)) {
                    BarSwipe.TOGGLE -> {
                        change.consume()
                        toggle()
                        break
                    }
                    BarSwipe.IGNORE -> break
                    BarSwipe.PENDING -> if (!change.pressed) break
                }
            }
        }
    }
}
