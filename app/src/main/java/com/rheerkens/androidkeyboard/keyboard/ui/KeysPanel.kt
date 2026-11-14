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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Constraints
import com.rheerkens.androidkeyboard.keyboard.AlternatesPopup
import com.rheerkens.androidkeyboard.keyboard.LanguageMenu
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import com.rheerkens.androidkeyboard.keyboard.splitRows
import com.rheerkens.androidkeyboard.keyboard.SplitRow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    val menu = KeyMenus(keyboards, languageMenu)
    val rollover = remember(state.keyboard, state.layer) { KeyRollover() }
    val rowHeight = (rowHeightDp * keyRows.toFloat() / layout.rows.size).dp
    if (splitGapDp > 0f) {
        val rows = remember(layout, state.keyboard, state.layer) { splitRows(layout, state.keyboard, state.layer) }
        val units = splitKeyUnits(rows)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, bottom = 4.dp)) {
            val unit = ((maxWidth - splitGapDp.dp) / (2 * units)).coerceAtLeast(1.dp)
            Column {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().height(rowHeight)) {
                        if (row.fullWidth) {
                            row.left.forEach { key ->
                                val width = if (key is Key.Space) Modifier.weight(1f) else Modifier.width(unit * key.weight)
                                KeyView(key, state, enterKind, actions, menu, width.fillMaxHeight(), rollover)
                            }
                        } else {
                            row.left.forEach { key -> KeyView(key, state, enterKind, actions, menu, Modifier.width(unit * key.weight).fillMaxHeight(), rollover) }
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
            Row(Modifier.fillMaxWidth().height(rowHeight)) {
                row.forEach { key -> KeyView(key, state, enterKind, actions, menu, Modifier.weight(key.weight).fillMaxHeight(), rollover) }
            }
        }
    }
}

/** Key widths a split half holds: the wider half of the widest split row, in key-weight units. */
internal fun splitKeyUnits(rows: List<SplitRow>): Float =
    rows.filterNot { it.fullWidth }.maxOf { maxOf(it.left.sumOf { key -> key.weight.toDouble() }, it.right.sumOf { key -> key.weight.toDouble() }) }.toFloat()

@Composable
private fun KeyView(
    key: Key,
    state: KeyboardState,
    enterKind: EnterKind,
    actions: KeyboardActions,
    menu: KeyMenus,
    modifier: Modifier,
    rollover: KeyRollover,
) {
    val c = LocalKeyboardColors.current
    when (key) {
        is Key.Gap -> Spacer(modifier)
        is Key.Char -> CharKey(key, state, actions, modifier, rollover)
        is Key.Shift -> {
            val on = state.shift != ShiftState.OFF
            val desc = when (state.shift) {
                ShiftState.OFF -> "Shift"
                ShiftState.ONCE -> "Shift on"
                ShiftState.LOCKED -> "Caps lock on"
            }
            PressTarget(desc, modifier, gestureKey = key, onDown = actions::onKeyDown, onTap = { actions.onKey(key) }) { pressed ->
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
        is Key.Backspace -> BackspaceKey(actions, modifier, sliderInPopup = true, onSwipeDown = { actions.onSetMinimized(true) })
        is Key.LayerSwitch -> {
            val desc = when (key.target) {
                Layer.LETTERS -> "Letters"
                Layer.SYMBOLS -> "Numbers and symbols"
                Layer.SYMBOLS_2 -> "More symbols"
            }
            PressTarget(desc, modifier, gestureKey = key, onDown = actions::onKeyDown, onTap = { actions.onKey(key) }) { pressed ->
                KeyCap(if (pressed) c.keyPressed else c.keyMod) {
                    Text(key.label, color = c.keyText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        is Key.Emoji -> EmojiKey(key, state, actions, menu, modifier)
        is Key.Globe -> PressTarget(
            "Next keyboard. Long-press to switch to another keyboard app",
            // Announce the keyboard a tap switched to, whichever key has focus.
            modifier.semantics {
                stateDescription = state.keyboard.fullLabel
                liveRegion = LiveRegionMode.Polite
            },
            gestureKey = key,
            onDown = actions::onKeyDown, onTap = { actions.onKey(key) }, onLongPress = actions::onGlobeLongPress,
        ) { pressed ->
            KeyCap(if (pressed) c.keyPressed else c.keyMod) { KeyIcon(SlimIcons.Globe) }
        }
        is Key.Space -> PressTarget(
            "Space, ${state.keyboard.fullLabel}. Long-press to switch keyboard app", modifier, gestureKey = key,
            rollover = rollover,
            onDown = actions::onKeyDown, onTap = { actions.onKey(key) }, onLongPress = actions::onSpaceLongPress,
        ) { pressed ->
            KeyCap(if (pressed) c.keyPressed else c.key) {
                Text(state.keyboard.spaceLabel, color = c.spaceText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        is Key.Enter -> PressTarget(
            enterKind.label ?: "Enter, new line", modifier, gestureKey = key, onDown = actions::onKeyDown, onTap = { actions.onKey(key) },
            rollover = rollover,
        ) { pressed ->
            KeyCap(if (pressed) c.enterPressed else c.enter) {
                val label = enterKind.label
                if (label == null) {
                    KeyIcon(SlimIcons.Enter, tint = c.enterInk)
                } else {
                    Text(label, color = c.enterInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** What the emoji key's hold menu offers, and where it draws. */
private data class KeyMenus(val keyboards: List<TypingKeyboard>, val languages: LanguageMenuState?)

/**
 * The emoji key: tap for emoji. With two or more keyboards, hold for a popup of keyboard languages
 * (like accents: slide to one and let go). Letting go without sliding keeps it open to tap one.
 */
@Composable
private fun EmojiKey(key: Key.Emoji, state: KeyboardState, actions: KeyboardActions, menus: KeyMenus, modifier: Modifier) {
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
        onDown = actions::onKeyDown,
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

/** Key size and left edge in the window, in pixels. */
private data class KeyPlacement(val left: Float, val width: Float, val height: Float, val window: Float)

/**
 * A letter or symbol key. Long-press opens a popup with its number and accents; slide to choose,
 * release to type. Sliding away from the popup and key cancels.
 */
@Composable
private fun CharKey(key: Key.Char, state: KeyboardState, actions: KeyboardActions, modifier: Modifier, rollover: KeyRollover) {
    val c = LocalKeyboardColors.current
    val density = LocalDensity.current
    val shifted = state.layer == Layer.LETTERS && state.shift != ShiftState.OFF
    fun cased(text: String) = if (shifted) shiftCase(text) else text
    val label = cased(key.label)
    val choices = key.alternates
    var open by remember(key) { mutableStateOf(false) }
    var selected by remember(key) { mutableStateOf<Int?>(null) }
    var placement by remember(key) { mutableStateOf<KeyPlacement?>(null) }
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
                placement = KeyPlacement(left, coords.size.width.toFloat(), coords.size.height.toFloat(), root.size.width.toFloat())
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
            key.opensEmojiOnHold -> ({ actions.onKey(Key.Emoji(fromHold = true)) })
            choices.isEmpty() -> null
            else -> ({ selected = 0 })
        },
        onLongPressRelease = if (choices.isEmpty()) null else { { selected?.let { actions.onKey(Key.Char(choices[it])) } } },
        onLongPressState = { open = it },
        onLongPressMove = popup?.let { p -> { at: Offset -> selected = p.select(at.x, at.y) } },
    ) { pressed ->
        KeyCap(if (pressed) c.keyPressed else c.key) {
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
                        layout(0, 0) { box.place(popup.left.roundToInt(), popup.top.roundToInt()) }
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
