package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.BackspaceScrub
import com.rheerkens.androidkeyboard.keyboard.CommandFan
import com.rheerkens.androidkeyboard.keyboard.FanCommand
import com.rheerkens.androidkeyboard.keyboard.SlideScale
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The slider's span in the key's coordinates (px): its top, the touch point, the travel to the
 * top, and its bottom (the key's bottom, or just above the key when drawn in its own window).
 */
private class SlideRail(val top: Float, val start: Float, val travel: Float, val bottom: Float)

private val RAIL_WIDTH = 52.dp
/** Room left of the capsule for its chip, wide enough for "All before cursor". */
private val CHIP_AREA = 140.dp
/** A press longer than this is a hold: it shows the slider instead of deleting a character. */
private const val HOLD_MS = 300L
/** Room around the slider's own window for its shadows. */
private val POPUP_PAD = 8.dp
/** How far down from the full keyboard's Backspace (within 30 degrees of vertical) minimizes it. */
private const val MINIMIZE_DP = 36f
/** The longest travel inside the keyboard ([sliderInPopup] off), about 4 cm. */
private val MAX_TRAVEL = SlideScale.MAX_TRAVEL.dp
/** Travel when the key's place on screen isn't known yet. */
private val FALLBACK_TRAVEL = 179.dp
/** Room the slider's window keeps below the top of the screen, for the status bar. */
private val SCREEN_TOP_MARGIN = 48.dp

/**
 * Backspace: a tap deletes one character. Holding raises a vertical slider instead of repeating:
 * sliding up erases word by word, the top erases everything before the cursor (all text when the
 * cursor is at the end; text after it always stays), and sliding back down brings it back.
 * [sliderInPopup] draws the slider in its own window, rising from the key over the keyboard and the
 * app (both keyboards use it). It ends at the same height on screen for both: about 4 cm above the
 * minimized bar's key, and less from the full keyboard's key, which sits higher; otherwise it rises over the keys, up to the
 * keyboard's top edge. Words count from the touch point and are spaced by [SlideScale] on one
 * smooth curve: about 3 mm for the first word, each next one a little less toward the top.
 *
 * With [fan] (the minimized bar), holding or dragging opens the command fan instead: up still
 * erases words, up-left types a space, left runs the editor's action, and further out are clearing
 * the clipboard, a new line and the keyboard for this field only. Sliding the word eraser back down
 * past where it started brings the fan back. With [onSwipeDown] (the full keyboard), sliding down
 * from the key calls it instead of deleting.
 *
 * Sliding on from the slider into the editor (left off a [sliderInPopup] slider above the keyboard,
 * or past the keyboard's top edge otherwise) points at where to cut instead (Android 14+): everything from the word
 * under the finger up to the cursor is deleted, and moving elsewhere brings words back or deletes
 * more, against the text as it was when the slide began. Releasing keeps it; sliding back over the
 * keyboard puts everything back, and releasing there cancels.
 */
@Composable
fun BackspaceKey(
    actions: KeyboardActions,
    modifier: Modifier,
    sliderInPopup: Boolean = false,
    fan: FanSpec? = null,
    onSwipeDown: (() -> Unit)? = null,
) {
    val latest by rememberUpdatedState(actions)
    val latestFan by rememberUpdatedState(fan)
    val latestSwipeDown by rememberUpdatedState(onSwipeDown)
    val c = LocalKeyboardColors.current
    var pressed by remember { mutableStateOf(false) }
    var keyTop by remember { mutableFloatStateOf(0f) }
    // How high the key's top sits above the keyboard's bottom edge (px), for the slider's length.
    var keyLift by remember { mutableStateOf<Float?>(null) }
    val bottomInset = WindowInsets.navigationBars.union(WindowInsets.captionBar).getBottom(LocalDensity.current)
    var rail by remember { mutableStateOf<SlideRail?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var scrub by remember { mutableStateOf<BackspaceScrub?>(null) }
    var scale by remember { mutableStateOf<SlideScale?>(null) }
    var level by remember { mutableIntStateOf(-1) }
    var fanOrigin by remember { mutableStateOf<Offset?>(null) }
    var fanHot by remember { mutableStateOf<FanCommand?>(null) }
    var minimizeArmed by remember { mutableStateOf(false) }
    var aiming by remember { mutableStateOf(false) }
    var hasAimed by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val hasFan = fan != null
    val hasSwipeDown = onSwipeDown != null
    Box(
        modifier.onGloballyPositioned {
            keyTop = it.positionInRoot().y
            keyLift = it.findRootCoordinates().size.height - bottomInset - keyTop
            coords = it
        }.semantics {
            contentDescription = when {
                fan != null -> "Backspace. Tap to delete a character. Hold or drag for more: up erases words, " +
                    "then left into the text deletes from the word under your finger, " +
                    "up-left types a space, left is ${fan.actionLabel}"
                onSwipeDown != null -> "Backspace. Tap to delete a character. Hold and slide up to erase word by word, " +
                    "then left into the text field to delete from the word under your finger, or slide down to minimize the keyboard"
                else -> "Backspace. Tap to delete a character. Hold and slide up to erase word by word, " +
                    "or to the top to erase everything before the cursor"
            }
            role = Role.Button
            onClick { latest.onBackspace(); true }
            val clearAll = CustomAccessibilityAction("Clear all text") {
                latest.onBackspaceHoldStarted()
                try { latest.onClearAllText() } finally { latest.onBackspaceHoldEnded() }
                true
            }
            val beforeCursor = CustomAccessibilityAction("Delete text before cursor") {
                latest.onBackspaceHoldStarted()
                try { latest.onDeleteBeforeCursor() } finally { latest.onBackspaceHoldEnded() }
                true
            }
            customActions = buildList {
                add(beforeCursor)
                add(clearAll)
                fan?.sectors?.map { it.command }?.filter { it != FanCommand.WORDS && fan.enabled(it) }?.forEach { command ->
                    add(CustomAccessibilityAction(fan.description(command)) { latest.onFanCommand(command); true })
                }
                onSwipeDown?.let { add(CustomAccessibilityAction("Minimize keyboard") { latestSwipeDown?.invoke(); true }) }
            }
        }.pointerInput(sliderInPopup, hasFan, hasSwipeDown) {
            coroutineScope {
                val gestureScope = this
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    // The fan opens around the round button, not around the finger.
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val deadZone = CommandFan.deadZoneFor((down.position - center).getDistance() / density)
                    // In its own window the slider starts just above the key and ends at the same
                    // height on screen from either keyboard, short of the status bar. Inside the
                    // keyboard, at most up to its top edge.
                    val reach = down.position.y - (MAX_TRAVEL + 10.dp).toPx()
                    val popupBottom = -4.dp.toPx()
                    val geometry = if (sliderInPopup) {
                        // Never past the room on screen; without a position yet, the old 3 cm.
                        val keyOnScreen = coords?.takeIf { it.isAttached }?.localToScreen(Offset.Zero)?.y
                        val room = keyOnScreen?.let { it + popupBottom - (SCREEN_TOP_MARGIN + POPUP_PAD + 10.dp).toPx() }
                            ?: FALLBACK_TRAVEL.toPx()
                        val lift = (keyLift ?: 0f) / density
                        val top = popupBottom - SlideScale.travelFor(lift, room / density) * density - 10.dp.toPx()
                        // Words count from the touch point, so the first goes after a short slide however
                        // low on the key it began; the capsule reaches down over the key to meet the finger.
                        SlideRail(top, down.position.y, (down.position.y - top - 10.dp.toPx()).coerceAtLeast(1f), size.height.toFloat())
                    } else {
                        val top = maxOf(6.dp.toPx() - keyTop, reach)
                        SlideRail(top, down.position.y, (down.position.y - top - 10.dp.toPx()).coerceAtLeast(1f), size.height.toFloat())
                    }
                    pressed = true
                    fingerY = down.position.y
                    latest.onKeyDown()
                    latest.onBackspaceHoldStarted()
                    // A tap deletes one character on release. Holding shows the slider (or the fan)
                    // instead and deletes nothing until it slides; sliding up starts it right away.
                    var held = false
                    val reveal = gestureScope.launch {
                        delay(HOLD_MS)
                        held = true
                        if (latestFan != null) fanOrigin = center else rail = geometry
                    }
                    var sliding: BackspaceScrub? = null
                    // Where the finger was when the slider started: sliding back below it reopens the fan.
                    var slideFrom = 0f
                    var unsupported = false
                    var movedDown = false
                    var command: FanCommand? = null
                    // Above the keyboard's top edge the finger is over the editor. A slider in its own
                    // window covers the editor there, so sliding left off it starts aiming.
                    val editorEdge = -keyTop - 6.dp.toPx()
                    val offRail = center.x - (RAIL_WIDTH / 2 + 24.dp).toPx()
                    fun overEditor(p: Offset) = p.y < editorEdge && (hasAimed || !sliderInPopup || p.x < offRail)
                    hasAimed = false
                    // Lifted normally, rather than interrupted by a second finger or a cancel.
                    var released = false
                    // Erasing past the snapshot's start can't be undone, so it waits for the release.
                    var pendingClear = false
                    // Where each word begins on the slider, for the text the slide started with.
                    var steps: SlideScale? = null
                    fun levelFor(r: SlideRail, y: Float) = steps?.levelAt((r.start - y) / density, level) ?: 0
                    fun startSliding(from: SlideRail, y: Float): Boolean {
                        val s = latest.onBackspaceScrubStart()
                        if (s == null) {
                            unsupported = true
                            return false
                        }
                        reveal.cancel()
                        sliding = s
                        slideFrom = y
                        rail = from
                        scrub = s
                        steps = SlideScale(s.top, from.travel / density)
                        scale = steps
                        level = -1
                        return true
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val finger = event.changes.firstOrNull { it.id == down.id }
                            if (finger == null || finger.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                            val position = finger.position
                            finger.consume()
                            val dx = (position.x - down.position.x) / density
                            val dy = (position.y - down.position.y) / density
                            // From the button's centre, for the fan's wedges.
                            val fx = (position.x - center.x) / density
                            val fy = (position.y - center.y) / density
                            if (!finger.pressed) {
                                released = true
                                reveal.cancel()
                                val spec = latestFan
                                when {
                                    // Lifting over the editor aims there, even with no move in between.
                                    hasAimed || (sliding != null && overEditor(position)) -> {
                                        // Decide on where the finger lifted, not where it last moved.
                                        val layout = coords
                                        if (overEditor(position) && layout != null && layout.isAttached) {
                                            val screen = layout.localToScreen(position)
                                            latest.onWordAim(screen.x, screen.y)
                                        } else if (aiming) {
                                            latest.onWordAimLeft()
                                        }
                                        aiming = false
                                    }
                                    sliding != null -> sliding?.let { slid ->
                                        // Settle on where the finger lifted: it may not have moved there first.
                                        val r = rail ?: geometry
                                        val target = levelFor(r, position.y)
                                        if (target != level || pendingClear) latest.onBackspaceScrubTo(target)
                                    }
                                    fanOrigin != null -> command = fanHot?.takeIf { it != FanCommand.WORDS }
                                    minimizeArmed -> latestSwipeDown?.invoke()
                                    spec != null -> if (!held && !unsupported) latest.onBackspace()
                                    !held && !unsupported && !movedDown -> latest.onBackspace()
                                }
                                break
                            }
                            fingerY = position.y
                            if (sliding != null && (hasAimed || overEditor(position))) {
                                val layout = coords
                                if (overEditor(position) && layout != null && layout.isAttached) {
                                    if (!hasAimed) {
                                        // Into the editor: from here the finger picks where the deleting starts.
                                        hasAimed = true
                                        pendingClear = false
                                        rail = null
                                        latest.onKeyDown()
                                    }
                                    aiming = true
                                    val screen = layout.localToScreen(position)
                                    latest.onWordAim(screen.x, screen.y)
                                } else if (aiming) {
                                    // Back over the keyboard: everything returns, and releasing here cancels.
                                    aiming = false
                                    latest.onWordAimLeft()
                                }
                                continue
                            }
                            val s = sliding
                            if (s != null) {
                                if (latestFan != null && position.y > slideFrom + 6.dp.toPx()) {
                                    // Slid back below where the slider started: restore everything and show the fan again.
                                    if (level > 0) latest.onBackspaceScrubTo(0)
                                    pendingClear = false
                                    latest.onBackspaceScrubEnd()
                                    sliding = null
                                    rail = null
                                    scrub = null
                                    scale = null
                                    steps = null
                                    level = -1
                                    fanHot = null
                                    fanOrigin = center
                                    continue
                                }
                                val r = rail ?: geometry
                                val target = levelFor(r, position.y)
                                if (target != level && s.topIsFinal && target == s.top) {
                                    // Show the top, but only apply it on release so it stays reversible.
                                    pendingClear = true
                                    level = target
                                    latest.onKeyDown()
                                } else if (target != level) {
                                    pendingClear = false
                                    val shown = latest.onBackspaceScrubTo(target)
                                    if (shown != null) {
                                        level = shown
                                    } else {
                                        // The editor went away or the cursor moved: stop and keep what it has.
                                        latest.onBackspaceScrubEnd()
                                        sliding = null
                                        unsupported = true
                                        rail = null
                                        scrub = null
                                        scale = null
                                    }
                                }
                                continue
                            }
                            val spec = latestFan
                            if (spec != null) {
                                if (fanOrigin == null) {
                                    if (!unsupported && CommandFan.isSliderStart(dx, dy, 8f)) {
                                        startSliding(geometry, position.y)
                                        continue
                                    }
                                    if (hypot(dx, dy) > 12f) {
                                        reveal.cancel()
                                        fanOrigin = center
                                    } else {
                                        continue
                                    }
                                }
                                // A dimmed wedge (New line in a single-line field) behaves like the cancel zone.
                                val hit = CommandFan.hit(spec.sectors, fx, fy, deadZone)?.takeIf { spec.enabled(it.command) }
                                if (hit?.command == FanCommand.WORDS && hypot(fx, fy) > CommandFan.WORDS_SLIDER && !unsupported) {
                                    // Up from the fan: the word slider, starting from the finger.
                                    // Reaching the Words wedge already took a deliberate slide, so most of the
                                    // dead zone is spent: the first word goes a few dp after the slider appears.
                                    val start = minOf(position.y + (SlideScale.DEAD - 2f).dp.toPx(), geometry.bottom)
                                    val from = SlideRail(geometry.top, start,
                                        (start - geometry.top - 10.dp.toPx()).coerceAtLeast(1f), geometry.bottom)
                                    fanOrigin = null
                                    fanHot = null
                                    if (!startSliding(from, position.y)) fanOrigin = center
                                    continue
                                }
                                if (hit?.command != fanHot) {
                                    fanHot = hit?.command
                                    if (hit != null) latest.onKeyDown()
                                }
                                continue
                            }
                            if (latestSwipeDown != null && (movedDown || dy > 8f)) {
                                // Sliding down from the full keyboard's Backspace minimizes it.
                                if (!movedDown) {
                                    movedDown = true
                                    reveal.cancel()
                                    rail = null
                                }
                                val armed = dy >= MINIMIZE_DP && abs(dx) <= dy * 0.577f
                                if (armed != minimizeArmed) {
                                    minimizeArmed = armed
                                    if (armed) latest.onKeyDown()
                                }
                                continue
                            }
                            if (!unsupported && down.position.y - position.y > 8.dp.toPx()) {
                                startSliding(geometry, position.y)
                            }
                        }
                    } finally {
                        reveal.cancel()
                        // Only a release keeps what aiming deleted; an interrupted gesture puts it back.
                        if (sliding != null && hasAimed && !released) latest.onWordAimLeft()
                        if (sliding != null) latest.onBackspaceScrubEnd()
                        aiming = false
                        hasAimed = false
                        latest.onBackspaceHoldEnded()
                        pressed = false
                        rail = null
                        scrub = null
                        scale = null
                        level = -1
                        fanOrigin = null
                        fanHot = null
                        minimizeArmed = false
                    }
                    // After cleanup: the keyboard for this field replaces this bar.
                    command?.let { latest.onFanCommand(it) }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (fan != null) {
            // Keep the fan's existing geometry and touch area, with only the glyph visible.
            Box(
                Modifier.size((CommandFan.BUTTON * 2).dp),
                contentAlignment = Alignment.Center,
            ) { Icon(SlimIcons.Backspace, null, tint = if (pressed) c.enter else c.keyText, modifier = Modifier.size(22.dp)) }
        } else {
            if (minimizeArmed) Icon(SlimIcons.ChevronDown, "Release to minimize", tint = c.enter, modifier = Modifier.size(22.dp))
            else Icon(SlimIcons.Backspace, null, tint = if (pressed) c.enter else c.keyText, modifier = Modifier.size(22.dp))
        }
        if (hasAimed) {
            val aim = if (aiming) LocalWordAim.current ?: WordAim.Searching else null
            if (sliderInPopup) {
                // The minimized bar has no room above the key: the chip gets its own window.
                val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
                Popup(
                    popupPositionProvider = remember(gap) { AboveKey(gap) },
                    properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
                ) { AimChip(aim, Modifier) }
            } else {
                AimChip(aim, Modifier.align(Alignment.TopEnd).offset(y = (-44).dp).wrapContentSize(Alignment.TopEnd, unbounded = true))
            }
        }
        val origin = fanOrigin
        if (fan != null && origin != null) CommandFanPopup(fan, fanHot, origin)
        val r = rail ?: return@Box
        if (sliderInPopup) {
            val density = LocalDensity.current
            val provider = remember(r, density) {
                with(density) { GaugeAnchor(r, RAIL_WIDTH.roundToPx(), POPUP_PAD.roundToPx()) }
            }
            Popup(
                popupPositionProvider = provider,
                properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
            ) { SlideGauge(r, fingerY, scrub, scale, level, POPUP_PAD, Modifier) }
        } else {
            // Centre the capsule on the key; the label area hangs off its left.
            SlideGauge(
                r, fingerY, scrub, scale, level, 0.dp,
                Modifier.align(Alignment.BottomCenter).offset(x = -CHIP_AREA / 2)
                    .wrapContentSize(Alignment.BottomCenter, unbounded = true),
            )
        }
    }
}

/**
 * Places the slider's own window so the capsule (the content's right end, inside [pad]) is centred
 * on the key and spans [rail], past the top of the keyboard. The gesture stays with the keyboard
 * window until release, so this window only draws.
 */
private class GaugeAnchor(private val rail: SlideRail, private val railWidth: Int, private val pad: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.center.x + railWidth / 2 + pad - popupContentSize.width
        return IntOffset(x.coerceAtLeast(0), anchorBounds.top + rail.top.toInt() - pad)
    }
}

/**
 * A capsule rising from Backspace that fills like a level gauge up to the finger, with a notch per
 * word and a cap at the top that erases everything before the cursor. The key's icon rides on the fill. The label hangs off the
 * capsule's left, and [pad] surrounds both.
 */
@Composable
private fun SlideGauge(
    rail: SlideRail,
    fingerY: Float,
    scrub: BackspaceScrub?,
    scale: SlideScale?,
    level: Int,
    pad: Dp,
    modifier: Modifier,
) {
    val c = LocalKeyboardColors.current
    val density = LocalDensity.current
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
    val all = scrub != null && scrub.top > 0 && level == scrub.top
    val fill by animateColorAsState(if (all) c.danger else if (scrub != null) c.enter else c.keyPressed, label = "fill")
    val ink = if (all) c.dangerInk else if (scrub != null) c.enterInk else c.keyText
    val heightPx = rail.bottom - rail.top
    val inset = with(density) { 10.dp.toPx() }
    val fillTop = if (all) 0f else (fingerY - rail.top).coerceIn(inset, heightPx)
    // y in the capsule of a point [dp] of travel above the touch point.
    fun yOf(dp: Float) = rail.start - rail.top - with(density) { dp.dp.toPx() }
    val allLine = yOf(scale?.allStart ?: with(density) { rail.travel.toDp().value - SlideScale.ALL })
    Box(
        modifier
            .requiredSize(RAIL_WIDTH + CHIP_AREA + pad * 2, with(density) { heightPx.toDp() } + pad * 2)
            .padding(pad)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleY = 0.5f + 0.5f * shown.value
                alpha = shown.value
            },
    ) {
        val shape = RoundedCornerShape(RAIL_WIDTH / 2)
        Box(Modifier.align(Alignment.TopEnd).requiredWidth(RAIL_WIDTH).fillMaxHeight().shadow(8.dp, shape)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val pad = 4.dp.toPx()
                drawRoundRect(c.popup, cornerRadius = CornerRadius(w / 2))
                drawRoundRect(c.popupBorder, cornerRadius = CornerRadius(w / 2), style = Stroke(1.dp.toPx()))
                val inner = w - pad * 2
                drawRoundRect(
                    Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0.55f)), startY = fillTop, endY = size.height),
                    topLeft = Offset(pad, (fillTop - pad).coerceAtLeast(pad)),
                    size = Size(inner, size.height - pad - (fillTop - pad).coerceAtLeast(pad)),
                    cornerRadius = CornerRadius(inner / 2),
                )
                if (scrub == null || scale == null) return@Canvas
                // A notch on each side where every word starts; crowded ones are skipped.
                var last = Float.MAX_VALUE
                val tick = 6.dp.toPx()
                for (l in 1 until scrub.top) {
                    val y = yOf(scale.startOf(l))
                    if (last - y < 3.dp.toPx()) continue
                    last = y
                    val color = if (y >= fillTop) ink.copy(alpha = 0.5f) else c.keyText.copy(alpha = 0.3f)
                    drawLine(color, Offset(pad + 4.dp.toPx(), y), Offset(pad + 4.dp.toPx() + tick, y), 1.5.dp.toPx())
                    drawLine(color, Offset(w - pad - 4.dp.toPx() - tick, y), Offset(w - pad - 4.dp.toPx(), y), 1.5.dp.toPx())
                }
                if (scrub.top == 0) return@Canvas
                drawLine(
                    if (all) ink.copy(alpha = 0.6f) else c.keyText.copy(alpha = 0.35f),
                    Offset(pad + 6.dp.toPx(), allLine), Offset(w - pad - 6.dp.toPx(), allLine), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
            val trash = if (all) 20.dp else 16.dp
            // Nothing before the cursor: no cap to reach.
            if (scrub?.top != 0) Icon(
                SlimIcons.Trash, null, tint = if (all) ink else c.muted,
                modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, ((allLine - trash.toPx()) / 2).roundToInt()) }.size(trash),
            )
            // At the top the trash icon takes over from the riding Backspace icon.
            if (!all) {
                Icon(
                    SlimIcons.Backspace, null, tint = ink,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset { IntOffset(0, (fillTop + 4.dp.toPx()).coerceAtMost(heightPx - 26.dp.toPx()).roundToInt()) }
                        .size(20.dp),
                )
            }
        }
        val label = when {
            scrub != null && scrub.top == 0 -> "Nothing before cursor"
            scrub == null || level <= 0 -> "Slide up"
            all -> if (scrub?.topClearsAll == true) "Clear all" else "All before cursor"
            level == 1 -> "1 word"
            else -> "$level words"
        }
        val chipShape = RoundedCornerShape(10.dp)
        Text(
            label,
            color = if (all) c.dangerInk else c.keyText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(-(RAIL_WIDTH + 8.dp).roundToPx(), (fillTop - 15.dp.toPx()).coerceAtLeast(0f).roundToInt()) }
                .shadow(3.dp, chipShape)
                .background(if (all) c.danger else c.popup, chipShape)
                .border(1.dp, c.popupBorder, chipShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Right-aligned with the key, [gap] above it. */
private class AboveKey(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        (anchorBounds.right - popupContentSize.width).coerceAtLeast(0),
        anchorBounds.top - gap - popupContentSize.height,
    )
}

/** Above Backspace while sliding through the editor: what releasing does. Null: back over the keyboard. */
@Composable
private fun AimChip(aim: WordAim?, modifier: Modifier) {
    val c = LocalKeyboardColors.current
    val label = when (aim) {
        null -> "Release to cancel"
        WordAim.Searching -> "Finding words…"
        is WordAim.Cut -> when {
            aim.words == 0 -> "Nothing to delete"
            aim.words == 1 -> "Delete 1 word"
            else -> "Delete ${aim.words} words"
        }
        WordAim.Unsupported -> "Not supported here"
    }
    val hot = aim is WordAim.Cut && aim.words > 0
    val shape = RoundedCornerShape(10.dp)
    Text(
        label,
        color = if (hot) c.dangerInk else c.keyText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 220.dp)
            .shadow(3.dp, shape)
            .background(if (hot) c.danger else c.popup, shape)
            .border(1.dp, c.popupBorder, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
