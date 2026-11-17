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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
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
import com.rheerkens.androidkeyboard.keyboard.CursorSlide
import com.rheerkens.androidkeyboard.keyboard.KeyRollover

private class KeyTouch(var active: Boolean = true, var long: Boolean = false, var job: Job? = null,
    var at: Offset = Offset.Zero, var committed: Boolean = false,
    /** The finger has left the spot it went down on, so holding it no longer long-presses. */
    var moved: Boolean = false, val down: Offset = at,
    /** On a key that slides ([KeySlide]): the finger's travel as cursor steps. */
    var slide: CursorSlide? = null, var edge: CursorSlide.Edge? = null, var edgeJob: Job? = null)

/**
 * Makes a key a trackpad for the cursor once the finger slides away from where it went down
 * ([CursorSlide]). Space works this way.
 */
class KeySlide(
    /** Move the cursor this many characters right and lines down (negative: left, up). */
    val onMove: (chars: Int, lines: Int) -> Unit,
    /** A slide began (true) or ended. */
    val onActive: (Boolean) -> Unit = {},
    /** How much keyboard lies above the key's top edge, in dp: higher up, the finger is over the app. */
    val aboveDp: Float = 0f,
)

/**
 * A touch target with press feedback. Supports tap, long-press, and auto-repeat while held.
 * Each finger owns its own press, including two thumbs overlapping on the same key.
 * [onDown] fires immediately on touch (used for haptics). [onRepeat] fires once on touch, then
 * repeats after a short delay until release (backspace). With [onLongPressMove], a finger that
 * has long-pressed keeps its press while it slides off the key and reports each position.
 * With [rollover], touching the next typing key accepts a pending tap before its hold timer;
 * hold until the popup opens first when combining an accent gesture with another finger. A
 * rollover key also keeps its tap when the finger slides off it before lifting; other keys
 * cancel then. With [slide], a finger that moves no longer long-presses, and one that slides on
 * moves the cursor instead of tapping.
 */
@Composable
fun PressTarget(
    description: String,
    modifier: Modifier = Modifier,
    gestureKey: Any? = Unit,
    rollover: KeyRollover? = null,
    onDown: () -> Unit = {},
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    /** What TalkBack announces for the hold action, such as "Open keyboard tools". */
    longPressLabel: String? = null,
    onRepeat: (() -> Unit)? = null,
    onLongPressRelease: (() -> Unit)? = null,
    onLongPressState: (Boolean) -> Unit = {},
    onLongPressMove: ((Offset) -> Unit)? = null,
    /** Called just before [onLongPress] with where the finger is at that moment (key coordinates). */
    onLongPressAt: (Offset) -> Unit = {},
    slide: KeySlide? = null,
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
    val longAt by rememberUpdatedState(onLongPressAt)
    val keySlide by rememberUpdatedState(slide)
    val slides = slide != null
    val screenWidth = LocalView.current.resources.displayMetrics.widthPixels
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hasTap = onTap != null
    val hasLong = onLongPress != null
    Box(
        modifier
            .then(if (slides) Modifier.onGloballyPositioned { placed[0] = it } else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                onClick { (tap ?: repeat)?.invoke(); true }
                // A semantic long-click is a whole hold: start it, then release with its default choice.
                if (hasLong) onLongClick(longPressLabel) { longPress?.invoke(); longRelease?.invoke(); true }
            }
            .pointerInput(gestureKey, hasTap, hasLong, rollover, slides, screenWidth) {
                coroutineScope {
                    val gestureScope = this
                    val touches = mutableMapOf<PointerId, KeyTouch>()
                    fun feedback() {
                        pressed = touches.values.any { it.active }
                        longState(touches.values.any { it.active && it.long })
                    }
                    /** Where a slide from this key runs out of room, in dp from the key's corner. */
                    fun slideZones(): CursorSlide.Zones {
                        val left = placed[0]?.takeIf { it.isAttached }?.localToScreen(Offset.Zero)?.x ?: 0f
                        return CursorSlide.Zones(
                            row = size.height.toDp().value,
                            left = -left.toDp().value + CursorSlide.SIDE_EDGE,
                            right = (screenWidth - left).toDp().value - CursorSlide.SIDE_EDGE,
                            top = -(keySlide?.aboveDp ?: 0f),
                            bottom = size.height.toDp().value + CursorSlide.BELOW_KEY,
                        )
                    }
                    /** The touch is a slide from here on: no tap, no long-press. */
                    fun beginSlide(touch: KeyTouch) {
                        rollover?.cancel(touch)
                        touch.job?.cancel()
                        keySlide?.onActive(true)
                    }
                    fun endSlide(touch: KeyTouch) {
                        touch.edgeJob?.cancel()
                        if (touch.slide?.sliding == true) keySlide?.onActive(false)
                        touch.slide = null
                    }
                    /** Resting in a zone at the edge keeps the cursor moving until the finger leaves it. */
                    fun watchEdge(touch: KeyTouch, sliding: CursorSlide) {
                        val edge = sliding.edge
                        if (edge == touch.edge) return
                        touch.edge = edge
                        touch.edgeJob?.cancel()
                        touch.edgeJob = if (edge == null) null else gestureScope.launch {
                            delay(CursorSlide.EDGE_DWELL_MS)
                            while (touch.active && !touch.long && !touch.committed && touch.slide === sliding) {
                                if (!sliding.sliding) {
                                    sliding.begin()
                                    beginSlide(touch)
                                }
                                keySlide?.onMove(edge.chars, edge.lines)
                                delay(edge.everyMs)
                            }
                        }
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
                                        touch = KeyTouch(at = change.position)
                                        if (slides) {
                                            touch.slide = CursorSlide(slideZones(), change.position.x.toDp().value,
                                                change.position.y.toDp().value, change.uptimeMillis)
                                        }
                                        touches[change.id] = touch
                                        change.consume()
                                        down()
                                        feedback()
                                        val owned = touch
                                        rollover?.press(owned) {
                                            owned.committed = true
                                            owned.job?.cancel()
                                            tap?.invoke()
                                        }
                                        val r = repeat
                                        if (r != null || hasLong) {
                                            touch.job = gestureScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                if (r != null) {
                                                    r()
                                                    delay(REPEAT_START_MS)
                                                    while (owned.active) { repeat?.invoke(); delay(REPEAT_INTERVAL_MS) }
                                                } else {
                                                    delay(viewConfiguration.longPressTimeoutMillis)
                                                    if (owned.active && !owned.committed) {
                                                        rollover?.cancel(owned)
                                                        owned.long = true
                                                        feedback()
                                                        longAt(owned.at)
                                                        longPress?.invoke()
                                                    }
                                                }
                                            }
                                        }
                                        continue
                                    }
                                    if (touch == null) continue
                                    touch.at = change.position
                                    val tracking = touch.long && touch.active && !change.isConsumed && longMove != null
                                    val sliding = touch.slide?.takeIf { touch.active && !touch.long && !change.isConsumed }
                                    if (sliding != null && (sliding.sliding || !touch.committed)) {
                                        if (!touch.moved && (change.position - touch.down).getDistance() > viewConfiguration.touchSlop) {
                                            // A finger on its way somewhere isn't holding the key: no long-press.
                                            touch.moved = true
                                            touch.job?.cancel()
                                        }
                                        val began = !sliding.sliding
                                        sliding.moveTo(change.position.x.toDp().value, change.position.y.toDp().value, change.uptimeMillis)
                                        if (sliding.sliding) {
                                            // A slide, not a tap: no character, no long-press.
                                            if (began) beginSlide(touch)
                                            if (sliding.chars != 0 || sliding.lines != 0) keySlide?.onMove(sliding.chars, sliding.lines)
                                        }
                                        watchEdge(touch, sliding)
                                    }
                                    if (sliding?.sliding == true) {
                                        // The cursor follows the finger wherever it goes, also off the key.
                                    } else if (tracking) {
                                        longMove?.invoke(change.position)
                                    } else if (change.isConsumed) {
                                        touch.active = false
                                        rollover?.cancel(touch)
                                        touch.job?.cancel()
                                    } else if (touch.active && !inside(change.position.x, change.position.y, viewConfiguration.touchSlop)) {
                                        if (rollover != null && !touch.long) {
                                            // A typing key keeps its tap when a fast thumb slides off it. Its
                                            // hold popup needs the finger on the key, so that no longer opens.
                                            touch.job?.cancel()
                                        } else {
                                            touch.active = false
                                            rollover?.cancel(touch)
                                            touch.job?.cancel()
                                        }
                                    }
                                    if (!change.pressed) {
                                        touches.remove(change.id)
                                        touch.job?.cancel()
                                        val slid = touch.slide?.sliding == true
                                        endSlide(touch)
                                        if (touch.active && !slid) {
                                            if (touch.long) longRelease?.invoke()
                                            else if (rollover != null) rollover.release(touch)
                                            else if (repeat == null) tap?.invoke()
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
                        touches.values.forEach { it.job?.cancel(); rollover?.cancel(it); endSlide(it) }
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
