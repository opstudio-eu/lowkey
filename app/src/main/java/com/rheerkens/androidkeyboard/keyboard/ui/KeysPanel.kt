package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
    val rowHeight = (rowHeightDp * keyRows.toFloat() / layout.rows.size).dp
    if (splitGapDp > 0f) {
        val rows = remember(layout, state.keyboard, state.layer) { splitRows(layout, state.keyboard, state.layer) }
        val units = rows.filterNot { it.fullWidth }.maxOf { maxOf(it.left.sumOf { key -> key.weight.toDouble() }, it.right.sumOf { key -> key.weight.toDouble() }) }.toFloat()
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, bottom = 4.dp)) {
            val unit = ((maxWidth - splitGapDp.dp) / (2 * units)).coerceAtLeast(1.dp)
            Column {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().height(rowHeight)) {
                        if (row.fullWidth) {
                            row.left.forEach { key ->
                                val width = if (key is Key.Space) Modifier.weight(1f) else Modifier.width(unit * key.weight)
                                KeyView(key, state, enterKind, actions, menu, width.fillMaxHeight())
                            }
                        } else {
                            row.left.forEach { key -> KeyView(key, state, enterKind, actions, menu, Modifier.width(unit * key.weight).fillMaxHeight()) }
                            Spacer(Modifier.weight(1f))
                            row.right.forEach { key -> KeyView(key, state, enterKind, actions, menu, Modifier.width(unit * key.weight).fillMaxHeight()) }
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
                row.forEach { key -> KeyView(key, state, enterKind, actions, menu, Modifier.weight(key.weight).fillMaxHeight()) }
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
) {
    val c = LocalKeyboardColors.current
    when (key) {
        is Key.Gap -> Spacer(modifier)
        is Key.Char -> CharKey(key, state, actions, modifier)
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
            onDown = actions::onKeyDown, onTap = { actions.onKey(key) }, onLongPress = actions::onSpaceLongPress,
        ) { pressed ->
            KeyCap(if (pressed) c.keyPressed else c.key) {
                Text(state.keyboard.spaceLabel, color = c.spaceText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        is Key.Enter -> PressTarget(
            enterKind.label ?: "Enter, new line", modifier, gestureKey = key, onDown = actions::onKeyDown, onTap = { actions.onKey(key) },
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
    val holder = remember { EmojiKeyHolder() }
    // Read at the moment of the long-press: the gesture callbacks below can outlive this composition.
    val latestChoices = rememberUpdatedState(choices)
    fun open() {
        val menu = menu ?: return
        val choices = latestChoices.value
        val host = menu.host ?: return
        val coords = holder.coords?.takeIf { it.isAttached && host.isAttached } ?: return
        val at = host.localPositionOf(coords, Offset.Zero)
        val popup = with(density) {
            val windowWidth = host.size.width.toFloat()
            val cellWidth = maxOf(coords.size.width.toFloat(), 64.dp.toPx()).coerceAtMost(windowWidth)
            // Many keyboards make several rows; they shrink to stay inside the keyboard above the key.
            val rows = LanguageMenu.rows(choices.size, windowWidth, cellWidth)
            val cellHeight = if (rows > 1) minOf(52.dp.toPx(), at.y / (rows - 1)).coerceAtLeast(1f) else 52.dp.toPx()
            AlternatesPopup(
                count = choices.size, keyLeft = at.x, keyWidth = coords.size.width.toFloat(),
                keyHeight = coords.size.height.toFloat(), windowWidth = windowWidth,
                cellWidth = cellWidth, cellHeight = cellHeight, slop = 12.dp.toPx(),
            )
        }
        holder.moved = false
        menu.open = OpenLanguageMenu(choices, popup, at.x, at.y, selected = 0, sticky = false)
    }
    fun release() {
        val menu = menu ?: return
        val open = menu.open?.takeIf { !it.sticky } ?: return
        when (LanguageMenu.release(open.selected, holder.moved)) {
            LanguageMenu.Release.KEEP_OPEN -> menu.open = open.copy(sticky = true)
            LanguageMenu.Release.CLOSE -> menu.close()
            LanguageMenu.Release.CHOOSE -> {
                menu.close()
                actions.onSelectKeyboard(open.choices[open.selected ?: 0].id)
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
        onLongPress = if (switches) ({ open() }) else null,
        onLongPressRelease = if (switches) ({ release() }) else null,
        // A held menu whose press was cancelled (not released) closes; a released one stays.
        onLongPressState = { held -> if (!held) menu?.let { if (it.open?.sticky == false) it.close() } },
        onLongPressMove = if (switches && menu != null) { at: Offset ->
            menu.open?.takeIf { !it.sticky }?.let { open ->
                val selected = open.popup.select(at.x, at.y)
                if (selected != 0) holder.moved = true
                if (selected != open.selected) menu.open = open.copy(selected = selected)
            }
        } else null,
    ) { pressed ->
        KeyCap(if (pressed) c.keyPressed else c.keyMod) {
            KeyIcon(SlimIcons.Emoji)
            // A small corner mark, like a key with long-press choices.
            if (switches) {
                val mark = c.muted.copy(alpha = 0.6f)
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp).size(5.dp).drawBehind {
                        drawPath(Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, mark)
                    },
                )
            }
        }
    }
}

/** The emoji key's layout and gesture memory; not state, so updating it doesn't recompose. */
private class EmojiKeyHolder {
    var coords: LayoutCoordinates? = null
    var moved = false
}

/** Key size and left edge in the window, in pixels. */
private data class KeyPlacement(val left: Float, val width: Float, val height: Float, val window: Float)

/**
 * A letter or symbol key. Long-press opens a popup with its number and accents; slide to choose,
 * release to type. Sliding away from the popup and key cancels.
 */
@Composable
private fun CharKey(key: Key.Char, state: KeyboardState, actions: KeyboardActions, modifier: Modifier) {
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
        gestureKey = key, onDown = actions::onKeyDown,
        onTap = { actions.onKey(key) },
        onLongPress = when {
            key.opensEmojiOnHold -> ({ actions.onKey(Key.Emoji()) })
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
