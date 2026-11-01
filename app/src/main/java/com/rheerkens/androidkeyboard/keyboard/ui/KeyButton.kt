package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart

private class KeyTouch(var active: Boolean = true, var long: Boolean = false, var job: Job? = null)

/**
 * A touch target with press feedback. Supports tap, long-press, and auto-repeat while held.
 * Each finger owns its own press, including two thumbs overlapping on the same key.
 * [onDown] fires immediately on touch (used for haptics). [onRepeat] fires once on touch, then
 * repeats after a short delay until release (backspace). With [onLongPressMove], a finger that
 * has long-pressed keeps its press while it slides off the key and reports each position.
 */
@Composable
fun PressTarget(
    description: String,
    modifier: Modifier = Modifier,
    gestureKey: Any? = Unit,
    onDown: () -> Unit = {},
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    onLongPressRelease: (() -> Unit)? = null,
    onLongPressState: (Boolean) -> Unit = {},
    onLongPressMove: ((Offset) -> Unit)? = null,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val down by rememberUpdatedState(onDown)
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val repeat by rememberUpdatedState(onRepeat)
    val longRelease by rememberUpdatedState(onLongPressRelease)
    val longState by rememberUpdatedState(onLongPressState)
    val longMove by rememberUpdatedState(onLongPressMove)
    val hasTap = onTap != null
    val hasLong = onLongPress != null
    Box(
        modifier
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                onClick { (tap ?: repeat)?.invoke(); true }
                // A semantic long-click is a whole hold: start it, then release with its default choice.
                if (hasLong) onLongClick { longPress?.invoke(); longRelease?.invoke(); true }
            }
            .pointerInput(gestureKey, hasTap, hasLong) {
                coroutineScope {
                    val gestureScope = this
                    val touches = mutableMapOf<PointerId, KeyTouch>()
                    fun feedback() {
                        pressed = touches.values.any { it.active }
                        longState(touches.values.any { it.active && it.long })
                    }
                    fun inside(x: Float, y: Float, padding: Float = 0f) =
                        x >= -padding && x <= size.width + padding && y >= -padding && y <= size.height + padding
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    var touch = touches[change.id]
                                    if (touch == null && change.pressed && !change.previousPressed && !change.isConsumed &&
                                        inside(change.position.x, change.position.y)) {
                                        touch = KeyTouch()
                                        touches[change.id] = touch
                                        change.consume()
                                        down()
                                        feedback()
                                        val owned = touch
                                        val r = repeat
                                        if (r != null || hasLong) {
                                            touch.job = gestureScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                if (r != null) {
                                                    r()
                                                    delay(REPEAT_START_MS)
                                                    while (owned.active) { repeat?.invoke(); delay(REPEAT_INTERVAL_MS) }
                                                } else {
                                                    delay(viewConfiguration.longPressTimeoutMillis)
                                                    if (owned.active) {
                                                        owned.long = true
                                                        feedback()
                                                        longPress?.invoke()
                                                    }
                                                }
                                            }
                                        }
                                        continue
                                    }
                                    if (touch == null) continue
                                    val tracking = touch.long && touch.active && !change.isConsumed && longMove != null
                                    if (tracking) {
                                        longMove?.invoke(change.position)
                                    } else if (change.isConsumed || !inside(change.position.x, change.position.y, viewConfiguration.touchSlop)) {
                                        touch.active = false
                                        touch.job?.cancel()
                                    }
                                    if (!change.pressed) {
                                        touches.remove(change.id)
                                        touch.job?.cancel()
                                        if (touch.active) {
                                            if (touch.long) longRelease?.invoke() else if (repeat == null) tap?.invoke()
                                        }
                                        change.consume()
                                    } else if (touch.active) {
                                        change.consume()
                                    }
                                    feedback()
                                }
                            }
                        }
                    } finally {
                        touches.values.forEach { it.job?.cancel() }
                        touches.clear()
                        feedback()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { content(pressed) }
}

/** Visual key cap (concept 4: 7 dp radius, 3 × 2.5 dp gutter, subtle bottom shadow). */
@Composable
fun KeyCap(
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 2.5.dp, vertical = 3.dp)
            .shadow(1.dp, shape, clip = false)
            .background(background, shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private const val REPEAT_START_MS = 400L
private const val REPEAT_INTERVAL_MS = 55L
