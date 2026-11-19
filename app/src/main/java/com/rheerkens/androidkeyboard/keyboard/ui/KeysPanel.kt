package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Constraints
import com.rheerkens.androidkeyboard.keyboard.AlternatesPopup
import com.rheerkens.androidkeyboard.keyboard.CursorSlide
import com.rheerkens.androidkeyboard.keyboard.LanguageMenu
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.LayoutCoordinates
import com.rheerkens.androidkeyboard.keyboard.shiftCase
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import com.rheerkens.androidkeyboard.keyboard.splitRows
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import com.rheerkens.androidkeyboard.keyboard.KEYBOARD_GRIP_SPACE_DP
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rheerkens.androidkeyboard.keyboard.Key
import com.rheerkens.androidkeyboard.keyboard.KeyRollover
import com.rheerkens.androidkeyboard.keyboard.KeyboardLayout
import com.rheerkens.androidkeyboard.keyboard.KeyboardState
import com.rheerkens.androidkeyboard.keyboard.Layer
import com.rheerkens.androidkeyboard.keyboard.ShiftState
import com.rheerkens.androidkeyboard.keyboard.TouchKey
import com.rheerkens.androidkeyboard.keyboard.touchRow
import com.rheerkens.androidkeyboard.keyboard.stickKey
import com.rheerkens.androidkeyboard.ui.SlimIcons

/** Four key rows (concept 4 geometry: 48 dp rows by default, 3 dp side and 4 dp bottom padding). */
@Composable
fun KeysPanel(
    layout: KeyboardLayout,
    state: KeyboardState,
    rowHeightDp: Int,
    enterKind: EnterKind,
    actions: KeyboardActions,
    splitGapDp: Float = 0f,
    keyRows: Int = layout.rows.size,
    keyboards: List<TypingKeyboard> = emptyList(),
    languageMenu: LanguageMenuState? = null,
    pointingStick: Boolean = true,
) {
    val rollover = remember(state.keyboard, state.layer) { KeyRollover() }
    val rowHeight = (rowHeightDp * keyRows.toFloat() / layout.rows.size).dp
    // While a slide from the pointing stick moves the cursor, the key labels fade: the keys are a trackpad then.
    var trackpad by remember { mutableStateOf(false) }
    val colors = LocalKeyboardColors.current
    val shown = remember(colors, trackpad) { if (trackpad) colors.fadedLabels() else colors }
    // Only the letters have one, when it is on: the symbols and number pads keep every key for typing.
    val stick = remember(layout, state.layer, pointingStick) {
        if (pointingStick && state.layer == Layer.LETTERS) stickKey(layout) else null
    }
    // A slide and a screen reader's cursor step, or two fingers, can overlap: the keys stay a trackpad until the
    // last slide ends.
    val sliding = remember { intArrayOf(0) }
    val latestActions by rememberUpdatedState(actions)
    // Outside the menu, so a popup held open while the menu is rebuilt keeps its place on top.
    val popups = remember { mutableStateListOf<Key.Char>() }
    // Where each copy of the stick key has its bottom-right corner, in root coordinates: its nub sits there.
    val corners = remember { mutableStateMapOf<Any, NubSpot>() }
    // How the nubs look: pressed while a finger is on one, leaning the way the slide moves the cursor.
    val look = remember { NubLook() }
    val menu = remember(keyboards, languageMenu, stick, rowHeight, layout.rows.size) {
        val slide = stick?.let {
            val (above, below) = CursorSlide.nubBounds(rowHeight.value, layout.rows.size, it.row, STRIP_DP.toFloat())
            KeySlide(
                onMove = { chars, lines -> look.lean = Lean.of(chars, lines); latestActions.onMoveCursor(chars, lines) },
                onActive = { active ->
                    if (!active) look.lean = null
                    val was = sliding[0] > 0
                    sliding[0] += if (active) 1 else -1
                    val now = sliding[0] > 0
                    if (now != was) {
                        trackpad = now
                        latestActions.onCursorSlide(now)
                    }
                },
                aboveDp = above,
                rowDp = rowHeight.value,
                belowDp = below,
                onTouch = { down -> look.touches += if (down) 1 else -1 },
            )
        }
        KeyMenus(keyboards, languageMenu, popups, stick?.key, slide, corners)
    }
    CompositionLocalProvider(LocalKeyboardColors provides shown) {
        Box {
            // The nub draws over the keys around it; an open accent popup over the nub.
            Box(Modifier.zIndex(if (popups.isEmpty()) 0f else 2f)) {
                KeyRows(layout, state, rowHeight, enterKind, actions, splitGapDp, menu, rollover)
            }
            menu.stickSlide?.let { slide ->
                StickNubs(corners, look, slide, rollover, Modifier.matchParentSize().zIndex(1f))
            }
        }
    }
}

/** Where a copy of the stick key ends, in root coordinates, and how big it is, in pixels: its nub sits on the corner. */
private data class NubSpot(val corner: Offset, val keyWidth: Float, val keyHeight: Float) {
    /** How far from the corner a touch is on the nub, in pixels: a fingertip, but never as far as a key's middle. */
    fun reach(density: Density): Float = minOf(with(density) { NUB_REACH_DP.dp.toPx() }, minOf(keyWidth, keyHeight) * NUB_SHARE)
}

/** How the nubs look: pressed while a finger is on one, leaning the way a slide last moved the cursor. */
private class NubLook {
    var touches by mutableIntStateOf(0)
    var lean by mutableStateOf<Lean?>(null)
}

/**
 * The pointing stick: a red nub drawn on each of [corners], the stick key's bottom-right corner (G's, where G,
 * H, V and B meet, on QWERTY). It takes no touches: the keys under it do, and a touch on one of them that
 * starts on the nub slides the cursor ([nubSlideFor]). For screen readers it is a node with the slide as actions.
 */
@Composable
private fun StickNubs(corners: Map<Any, NubSpot>, look: NubLook, slide: KeySlide, rollover: KeyRollover, modifier: Modifier) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        // Corners are known before this box is placed: draw nothing until it is, rather than in the wrong place.
        val at = origin ?: return@Box
        corners.forEach { (id, spot) ->
            val reach = spot.reach(density)
            key(id) {
                Box(
                    Modifier
                        .absoluteOffset { (spot.corner - at - Offset(reach, reach)).round() }
                        .size(with(density) { (reach * 2).toDp() })
                        .stickNub(look.touches > 0, look.lean)
                        .semantics {
                            contentDescription = "Pointing stick. Slide to move the cursor"
                            // The slide as actions, for screen readers, which can't slide. Each is a one-step
                            // slide, so Shift tapped before it selects, as with a finger.
                            customActions = Lean.entries.map { step ->
                                CustomAccessibilityAction("Move cursor ${step.name.lowercase()}") {
                                    // A letter still held is typed where the cursor is now, before it moves.
                                    rollover.flush()
                                    // An editor can throw: the slide still ends, or the keys would stay a trackpad.
                                    try {
                                        slide.onActive(true)
                                        slide.onMove(step.x, step.y)
                                    } finally {
                                        slide.onActive(false)
                                    }
                                    true
                                }
                            }
                        },
                )
            }
        }
    }
}

/**
 * [slide] for a key whose touch area is [bounds] (root coordinates) when one of [nubs] reaches into it: only a
 * touch that starts on the nub slides, and the slide's room is measured from the key's middle. Null when no nub
 * reaches the key.
 */
private fun nubSlideFor(slide: KeySlide, bounds: Rect, nubs: Collection<NubSpot>, density: Density): KeySlide? {
    val nub = nubs.firstOrNull { spot ->
        val nearest = Offset(spot.corner.x.coerceIn(bounds.left, bounds.right), spot.corner.y.coerceIn(bounds.top, bounds.bottom))
        (spot.corner - nearest).getDistance() < spot.reach(density)
    } ?: return null
    val reach = nub.reach(density)
    val lower = (bounds.center.y - nub.corner.y) / density.density
    return slide.copy(
        aboveDp = slide.aboveDp + lower,
        belowDp = slide.belowDp - lower,
        startsAt = { at -> (bounds.topLeft + at - nub.corner).getDistance() <= reach },
    )
}

/** How far a touch on the nub reaches from its middle, in dp: about a fingertip. */
private const val NUB_REACH_DP = 10f

/** How far the nub reaches into the keys around it, at most: a share of their size, short of their middle. */
private const val NUB_SHARE = 0.4f

/** The suggestion strip above the keys, with its grip (`KeyboardContent`). */
private const val STRIP_DP = 44 + KEYBOARD_GRIP_SPACE_DP

/** The palette with the text on the keys faded, for while the keys work as a trackpad. */
private fun KeyboardColors.fadedLabels(): KeyboardColors = copy(
    keyText = keyText.copy(alpha = keyText.alpha * TRACKPAD_LABEL_ALPHA),
    spaceText = spaceText.copy(alpha = spaceText.alpha * TRACKPAD_LABEL_ALPHA),
    muted = muted.copy(alpha = muted.alpha * TRACKPAD_LABEL_ALPHA),
    enterInk = enterInk.copy(alpha = enterInk.alpha * TRACKPAD_LABEL_ALPHA),
)

private const val TRACKPAD_LABEL_ALPHA = 0.25f

@Composable
private fun KeyRows(
    layout: KeyboardLayout,
    state: KeyboardState,
    rowHeight: Dp,
    enterKind: EnterKind,
    actions: KeyboardActions,
    splitGapDp: Float,
    menu: KeyMenus,
    rollover: KeyRollover,
) {
    // A number pad stays whole: its digits are never far apart.
    if (splitGapDp > 0f && state.layer != Layer.NUMBERS) {
        val rows = remember(layout, state.keyboard, state.layer) { splitRows(layout, state.keyboard, state.layer) }
        val units = rows.filterNot { it.fullWidth }.maxOf { maxOf(it.left.sumOf { key -> key.weight.toDouble() }, it.right.sumOf { key -> key.weight.toDouble() }) }.toFloat()
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, bottom = 4.dp)) {
            val unit = ((maxWidth - splitGapDp.dp) / (2 * units)).coerceAtLeast(1.dp)
            Column {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().height(rowHeight).zIndex(menu.layerOf(row.left + row.right))) {
                        if (row.fullWidth) {
                            row.left.forEach { key ->
                                val width = if (key is Key.Space) Modifier.weight(1f) else Modifier.width(unit * key.weight)
                                KeyView(key, state, enterKind, actions, menu, width.fillMaxHeight(), rollover)
                            }
                        } else {
                            // The shared middle key's left copy ends at the gap, where no keys meet its corner: the
                            // right copy carries the nub. A key at the end of the left half that isn't shared keeps it.
                            row.left.forEachIndexed { i, key ->
                                KeyView(key, state, enterKind, actions, menu, Modifier.width(unit * key.weight).fillMaxHeight(), rollover,
                                    nubHere = i < row.left.lastIndex || key !in row.right)
                            }
                            Spacer(Modifier.weight(1f))
                            row.right.forEach { key -> KeyView(key, state, enterKind, actions, menu, Modifier.width(unit * key.weight).fillMaxHeight(), rollover) }
                        }
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, bottom = 4.dp)) {
        layout.rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(rowHeight).zIndex(menu.layerOf(row))) {
                // Gaps that centre a row widen the touch area of the letter beside them (A and L on QWERTY).
                touchRow(row).forEach { touch ->
                    KeyView(touch.key, state, enterKind, actions, menu, Modifier.weight(touch.weight).fillMaxHeight(), rollover, touch)
                }
            }
        }
    }
}

@Composable
private fun KeyView(
    key: Key,
    state: KeyboardState,
    enterKind: EnterKind,
    actions: KeyboardActions,
    menu: KeyMenus,
    modifier: Modifier,
    rollover: KeyRollover,
    touch: TouchKey? = null,
    /** Whether the nub may sit on this key's corner when it is the stick key. */
    nubHere: Boolean = true,
) {
    val c = LocalKeyboardColors.current
    when (key) {
        is Key.Gap -> Spacer(modifier)
        is Key.Char -> CharKey(key, state, actions, modifier, rollover, touch?.before ?: 0f, touch?.after ?: 0f,
            popups = menu.popups, nubSlide = menu.stickSlide, corners = menu.corners, isStick = nubHere && key == menu.stick)
        is Key.Shift -> {
            val on = state.shift != ShiftState.OFF
            val desc = when (state.shift) {
                ShiftState.OFF -> "Shift"
                ShiftState.ONCE -> "Shift on"
                ShiftState.LOCKED -> "Caps lock on"
            }
            PressTarget(desc, modifier, gestureKey = key, rollover = rollover, onDown = actions::onKeyDown, onTap = { actions.onKey(key) }) { pressed ->
                KeyCap(if (pressed) c.keyPressed else if (on) c.keyOn else c.keyMod) {
                    val icon = when (state.shift) {
                        ShiftState.OFF -> SlimIcons.Shift
                        ShiftState.ONCE -> SlimIcons.ShiftFilled
                        ShiftState.LOCKED -> SlimIcons.CapsLock
                    }
                    KeyIcon(icon)
                }
            }
        }
        // The slider rises above the keyboard in its own window, for a long travel.
        // Backspace acts on touch-down: type a letter still held before it deletes.
        is Key.Backspace -> BackspaceKey(actions, modifier, sliderInPopup = true, onSwipeDown = { actions.onSetMinimized(true) },
            onPress = rollover::flush)
        is Key.LayerSwitch -> {
            val desc = when (key.target) {
                Layer.LETTERS -> "Letters"
                Layer.SYMBOLS -> "Numbers and symbols"
                Layer.SYMBOLS_2 -> "More symbols"
                Layer.NUMBERS -> "Number pad"
            }
            PressTarget(desc, modifier, gestureKey = key, rollover = rollover, onDown = actions::onKeyDown, onTap = { actions.onKey(key) }) { pressed ->
                KeyCap(if (pressed) c.keyPressed else c.keyMod) {
                    Text(key.label, color = c.keyText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        is Key.Emoji -> EmojiKey(key, state, actions, menu, modifier, rollover)
        is Key.Globe -> PressTarget(
            "Next keyboard. Long-press to switch to another keyboard app",
            // Announce the keyboard a tap switched to, whichever key has focus.
            modifier.semantics {
                stateDescription = state.keyboard.fullLabel
                liveRegion = LiveRegionMode.Polite
            },
            gestureKey = key,
            onDown = { rollover.flush(); actions.onKeyDown() }, onTap = { actions.onKey(key) }, onLongPress = actions::onGlobeLongPress,
        ) { pressed ->
            KeyCap(if (pressed) c.keyPressed else c.keyMod) { KeyIcon(SlimIcons.Globe) }
        }
        is Key.Space -> PressTarget(
            "Space, ${state.keyboard.fullLabel}. Long-press to switch keyboard app", modifier, gestureKey = key,
            rollover = rollover,
            onDown = actions::onKeyDown, onTap = { actions.onKey(key) }, onLongPress = actions::onSpaceLongPress,
        ) { pressed ->
            KeyCap(if (pressed) c.keyPressed else c.key) {
                if (key.showsLanguage) {
                    Text(state.keyboard.spaceLabel, color = c.spaceText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    KeyIcon(SlimIcons.Space, tint = c.spaceText)
                }
            }
        }
        is Key.Enter -> PressTarget(
            enterKind.label ?: "Enter, new line", modifier, gestureKey = key, onDown = actions::onKeyDown, onTap = { actions.onKey(key) },
            rollover = rollover,
        ) { pressed ->
            KeyCap(if (pressed && !enterKind.dimmed) c.enterPressed else c.enter,
                Modifier.alpha(if (enterKind.dimmed) DIMMED_ALPHA else 1f)) {
                val label = enterKind.label
                if (enterKind.opensSetup) {
                    // "Add key" and the like are spelled out on the prompt window's button; a glyph fits any key width.
                    KeyIcon(if (enterKind.setupIsServer) SlimIcons.Server else SlimIcons.Key, tint = c.enterInk)
                } else if (label == null) {
                    KeyIcon(SlimIcons.Enter, tint = c.enterInk)
                } else {
                    Text(label, color = c.enterInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/**
 * What the keys need from the panel: what the emoji key's hold menu offers and where it draws, and
 * which key is the pointing [stick] ([stickKey]; on a split keyboard that repeats it, its nub shows once) and what
 * a slide from its nub does ([stickSlide]).
 */
private class KeyMenus(
    val keyboards: List<TypingKeyboard>,
    val languages: LanguageMenuState?,
    /** The keys showing an accent popup now. */
    val popups: MutableList<Key.Char>,
    val stick: Key.Char? = null,
    val stickSlide: KeySlide? = null,
    /** Where each copy of the [stick] key has its bottom-right corner, in root coordinates. */
    val corners: MutableMap<Any, NubSpot> = mutableMapOf(),
) {
    /** A row with an open accent popup draws over the rows after it: a tall popup reaches into the next row. */
    fun layerOf(keys: List<Key>): Float = if (popups.any { it in keys }) 1f else 0f
}

/**
 * The emoji key: tap for emoji. With two or more keyboards, hold for a popup of keyboard languages
 * (like accents: slide to one and let go). Letting go without sliding keeps it open to tap one.
 */
@Composable
private fun EmojiKey(key: Key.Emoji, state: KeyboardState, actions: KeyboardActions, menus: KeyMenus, modifier: Modifier,
    rollover: KeyRollover) {
    val c = LocalKeyboardColors.current
    val density = LocalDensity.current
    val menu = menus.languages
    val choices = remember(menus.keyboards, state.keyboard) { LanguageMenu.choices(menus.keyboards, state.keyboard) }
    val switches = menu != null && choices.size > 1
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val holder = remember(touchSlop) { EmojiKeyHolder(LanguageMenu.Hold(touchSlop)) }
    // Read at the moment of the long-press: the gesture callbacks below can outlive this composition.
    val latestChoices = rememberUpdatedState(choices)
    fun open() {
        val menu = menu ?: return
        val choices = latestChoices.value
        val host = menu.host ?: return
        val coords = holder.coords?.takeIf { it.isAttached && host.isAttached } ?: return
        val at = host.localPositionOf(coords, Offset.Zero)
        val windowWidth = host.size.width.toFloat()
        val (cellWidth, minCell, fullCell) = with(density) {
            Triple(maxOf(coords.size.width.toFloat(), 64.dp.toPx()).coerceAtMost(windowWidth), 44.dp.toPx(), 52.dp.toPx())
        }
        // Rows above the key shrink to fit inside the keyboard, but not below a readable height:
        // keyboards beyond that are reached through a last "More" cell.
        val (shown, more) = LanguageMenu.fit(choices, LanguageMenu.capacity(windowWidth, cellWidth, at.y, minCell))
        val count = shown.size + if (more) 1 else 0
        val rows = LanguageMenu.rows(count, windowWidth, cellWidth)
        val cellHeight = if (rows > 1) minOf(fullCell, at.y / (rows - 1)).coerceAtLeast(1f) else fullCell
        val popup = AlternatesPopup(
            count = count, keyLeft = at.x, keyWidth = coords.size.width.toFloat(),
            keyHeight = coords.size.height.toFloat(), windowWidth = windowWidth,
            cellWidth = cellWidth, cellHeight = cellHeight, slop = with(density) { 12.dp.toPx() },
        )
        holder.hold.start(holder.startAt?.x, holder.startAt?.y)
        holder.startAt = null
        menu.open = OpenLanguageMenu(shown, popup, at.x, at.y, selected = 0, sticky = false, more = more)
    }
    fun release() {
        val menu = menu ?: return
        val open = menu.open?.takeIf { !it.sticky } ?: return
        when (LanguageMenu.release(open.selected, holder.hold.moved)) {
            LanguageMenu.Release.KEEP_OPEN -> menu.open = open.copy(sticky = true)
            LanguageMenu.Release.CLOSE -> menu.close()
            LanguageMenu.Release.CHOOSE -> {
                menu.close()
                val keyboard = open.choices.getOrNull(open.selected ?: 0)
                if (keyboard != null) actions.onSelectKeyboard(keyboard.id) else actions.onOpenKeyboardSettings()
            }
        }
    }
    PressTarget(
        if (switches) "Emoji. Long-press for keyboard languages" else "Emoji",
        modifier.onGloballyPositioned { holder.coords = it }
            .then(if (switches) Modifier.semantics { stateDescription = state.keyboard.fullLabel } else Modifier),
        gestureKey = key to switches,
        // The emoji panel replaces the keys: type a letter still held first.
        onDown = { rollover.flush(); actions.onKeyDown() },
        onTap = { actions.onKey(key) },
        onLongPressAt = { holder.startAt = it },
        onLongPress = if (switches) ({ open() }) else null,
        onLongPressRelease = if (switches) ({ release() }) else null,
        // A held menu whose press was cancelled (not released) closes; a released one stays.
        onLongPressState = { held -> if (!held) menu?.let { if (it.open?.sticky == false) it.close() } },
        onLongPressMove = if (switches && menu != null) { at: Offset ->
            menu.open?.takeIf { !it.sticky }?.let { open ->
                // Until the finger slides from where it rested when the menu opened, the current
                // keyboard stays selected, even if that spot lies over a neighbouring cell.
                if (holder.hold.update(at.x, at.y)) {
                    val selected = open.popup.select(at.x, at.y)
                    if (selected != open.selected) menu.open = open.copy(selected = selected)
                }
            }
        } else null,
    ) { pressed ->
        KeyCap(if (pressed) c.keyPressed else c.key) {
            KeyIcon(SlimIcons.Emoji)
            if (switches) LongPressMark()
        }
    }
}

/** Shared corner mark for keys with hidden choices. */
@Composable
private fun BoxScope.LongPressMark() {
    val mark = LocalKeyboardColors.current.muted
    Box(
        Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp).size(5.dp).drawBehind {
            drawPath(Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, mark)
        },
    )
}

/** The emoji key's layout and gesture memory; not state, so updating it doesn't recompose. */
private class EmojiKeyHolder(val hold: LanguageMenu.Hold) {
    var coords: LayoutCoordinates? = null
    /** Where the finger was when the long-press fired, until the menu opens with it. */
    var startAt: Offset? = null
}

/**
 * Drawn key size and left edge in the window, in pixels. [capLeft] is where the drawn key starts
 * in its touch area, which can also cover a gap beside it.
 */
private data class KeyPlacement(val left: Float, val width: Float, val height: Float, val window: Float, val capLeft: Float = 0f)

/** Draws a key over its own [weight] when its touch area also covers [before] and [after] it. */
private fun Modifier.drawnKey(before: Float, after: Float, weight: Float): Modifier =
    if (before == 0f && after == 0f) this else layout { measurable, constraints ->
        val total = weight + before + after
        val width = constraints.maxWidth
        val placeable = measurable.measure(Constraints.fixed((width * weight / total).roundToInt(), constraints.maxHeight))
        layout(width, constraints.maxHeight) { placeable.place((width * before / total).roundToInt(), 0) }
    }

/**
 * A letter or symbol key. Long-press opens a popup with its number and accents; slide to choose,
 * release to type. Sliding away from the popup and key cancels. Its touch area also covers the
 * empty space [before] and [after] it, in key widths. While its popup is open, the key is in [popups].
 * The pointing-stick key ([isStick]) puts its bottom-right corner in [corners], for its nub ([StickNubs]);
 * a key the nub reaches into slides the cursor ([nubSlide]) for a touch that starts on the nub.
 */
@Composable
private fun CharKey(key: Key.Char, state: KeyboardState, actions: KeyboardActions, modifier: Modifier, rollover: KeyRollover,
    before: Float = 0f, after: Float = 0f, popups: MutableList<Key.Char>? = null, nubSlide: KeySlide? = null,
    corners: MutableMap<Any, NubSpot>? = null, isStick: Boolean = false) {
    val c = LocalKeyboardColors.current
    val density = LocalDensity.current
    val shifted = state.layer == Layer.LETTERS && state.shift != ShiftState.OFF
    fun cased(text: String) = if (shifted) shiftCase(text) else text
    val label = cased(key.label)
    val choices = key.alternates
    var open by remember(key) { mutableStateOf(false) }
    var selected by remember(key) { mutableStateOf<Int?>(null) }
    var placement by remember(key) { mutableStateOf<KeyPlacement?>(null) }
    // Removes exactly what it added, also when the popup's key or row goes away while it is open.
    if (open && popups != null) DisposableEffect(popups, key) { popups += key; onDispose { popups -= key } }
    val slot = remember { Any() }
    if (isStick && corners != null) DisposableEffect(corners) { onDispose { corners.remove(slot) } }
    // Where the key's touch area is, in root coordinates: whether a nub reaches into it.
    var bounds by remember(key) { mutableStateOf<Rect?>(null) }
    val slide = if (nubSlide == null || corners == null) null else bounds?.let { nubSlideFor(nubSlide, it, corners.values, density) }
    val popup = remember(placement, choices.size, density) {
        val p = placement
        if (p == null || choices.isEmpty()) null else with(density) {
            AlternatesPopup(
                count = choices.size, keyLeft = p.left, keyWidth = p.width, keyHeight = p.height, windowWidth = p.window,
                cellWidth = maxOf(p.width, 40.dp.toPx()), cellHeight = 44.dp.toPx(), slop = 12.dp.toPx(),
            )
        }
    }
    fun choiceDescription(i: Int?): String = when {
        i == null -> "Slide back to choose"
        i == 0 && key.longPressOutput != null -> "Number preview: ${choices[0]}"
        else -> "Accent: ${cased(choices[i])}"
    }
    val description = when {
        key.opensEmojiOnHold -> "Comma. Hold for emoji"
        choices.isEmpty() -> label
        open -> "$label. ${choiceDescription(selected)}. Release to type"
        key.longPressOutput == null -> "$label. Long-press for accents"
        key.accents.isEmpty() -> "$label. Long-press for ${key.longPressOutput}"
        else -> "$label. Long-press for ${key.longPressOutput} or accents"
    }
    PressTarget(
        description,
        modifier
            .zIndex(if (open) 1f else 0f)
            .onGloballyPositioned { coords ->
                val root = coords.findRootCoordinates()
                val left = root.localPositionOf(coords, Offset.Zero).x
                val total = key.weight + before + after
                val capLeft = coords.size.width * before / total
                placement = KeyPlacement(left + capLeft, coords.size.width * key.weight / total,
                    coords.size.height.toFloat(), root.size.width.toFloat(), capLeft)
                val inRoot = coords.positionInRoot()
                val area = Rect(inRoot, coords.size.toSize())
                if (bounds != area) bounds = area
                if (isStick && corners != null) {
                    val width = coords.size.width * key.weight / total
                    val height = coords.size.height.toFloat()
                    val spot = NubSpot(inRoot + Offset(capLeft + width, height), width, height)
                    if (corners[slot] != spot) corners[slot] = spot
                }
            }
            .semantics {
                if (key.accents.isNotEmpty()) {
                    customActions = key.accents.map { accent ->
                        CustomAccessibilityAction("Type ${cased(accent)}") { actions.onKey(Key.Char(accent)); true }
                    }
                }
            },
        gestureKey = key, rollover = rollover, onDown = actions::onKeyDown,
        onTap = { actions.onKey(key) },
        onLongPress = when {
            key.opensEmojiOnHold -> ({ actions.onKey(Key.Emoji()) })
            choices.isEmpty() -> null
            else -> ({ selected = 0 })
        },
        onLongPressRelease = if (choices.isEmpty()) null else { { selected?.let { actions.onKey(Key.Char(choices[it])) } } },
        onLongPressState = { open = it },
        onLongPressMove = popup?.let { p -> { at: Offset -> selected = p.select(at.x - (placement?.capLeft ?: 0f), at.y) } },
        slide = slide,
    ) { pressed ->
        KeyCap(if (pressed) c.keyPressed else if (key.numberRow) c.numberKey else c.key, Modifier.drawnKey(before, after, key.weight)) {
            val hint = key.hint
            Text(
                label, color = c.keyText, fontSize = 21.sp, maxLines = 1,
                modifier = if (hint == null) Modifier else Modifier.offset(y = 3.dp),
            )
            if (hint != null) {
                Text(
                    hint, color = c.muted, fontSize = 10.sp, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 3.dp),
                )
            }
            if (key.accents.isNotEmpty()) LongPressMark()
        }
        if (pressed && open && popup != null) {
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    // Popup geometry is relative to the key's top-left corner, not its centre.
                    .align(Alignment.TopStart)
                    .layout { measurable, _ ->
                        val box = measurable.measure(Constraints.fixed(popup.width.roundToInt(), popup.height.roundToInt()))
                        val capLeft = (placement?.capLeft ?: 0f).roundToInt()
                        layout(0, 0) { box.place(popup.left.roundToInt() + capLeft, popup.top.roundToInt()) }
                    }
                    .shadow(3.dp, shape).background(c.popup, shape).border(1.dp, c.popupBorder, shape)
                    .semantics { contentDescription = choiceDescription(selected) },
            ) {
                for (row in 0 until popup.rows) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (column in 0 until popup.columns) {
                            val choice = popup.choiceAt(column, row)
                            val on = choice != null && choice == selected
                            Box(
                                Modifier.weight(1f).fillMaxHeight().padding(3.dp)
                                    .background(if (on) c.enter else Color.Transparent, RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (choice != null) Text(
                                    cased(choices[choice]), color = if (on) c.enterInk else c.keyText, maxLines = 1,
                                    fontSize = if (popup.count == 1) 28.sp else 22.sp, fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = LocalKeyboardColors.current.keyText) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
}
