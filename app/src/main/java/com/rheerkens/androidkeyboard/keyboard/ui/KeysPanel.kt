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
) {
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
                                KeyView(key, state, enterKind, actions, width.fillMaxHeight())
                            }
                        } else {
                            row.left.forEach { key -> KeyView(key, state, enterKind, actions, Modifier.width(unit * key.weight).fillMaxHeight()) }
                            Spacer(Modifier.weight(1f))
                            row.right.forEach { key -> KeyView(key, state, enterKind, actions, Modifier.width(unit * key.weight).fillMaxHeight()) }
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
                row.forEach { key -> KeyView(key, state, enterKind, actions, Modifier.weight(key.weight).fillMaxHeight()) }
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
            "Space, ${state.keyboard.fullLabel}. Long-press to choose a keyboard", modifier, gestureKey = key,
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
                cellWidth = maxOf(p.width, 32.dp.toPx()), cellHeight = 43.dp.toPx(), slop = 12.dp.toPx(),
            )
        }
    }
    fun choiceDescription(i: Int?): String = when {
        i == null -> "Slide back to choose"
        i == 0 && key.longPressOutput != null -> "Number preview: ${choices[0]}"
        else -> "Accent: ${cased(choices[i])}"
    }
    val description = when {
        choices.isEmpty() -> label
        open -> "$label. ${choiceDescription(selected)}. Release to type"
        key.longPressOutput == null -> "$label. Long-press for accents"
        key.accents.isEmpty() -> "$label. Long-press for ${key.longPressOutput}"
        else -> "$label. Long-press for ${key.longPressOutput} or accents"
    }
    PressTarget(
        description,
        modifier
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
        onLongPress = if (choices.isEmpty()) null else { { selected = 0 } },
        onLongPressRelease = if (choices.isEmpty()) null else { { selected?.let { actions.onKey(Key.Char(choices[it])) } } },
        onLongPressState = { open = it },
        onLongPressMove = popup?.let { p -> { at: Offset -> selected = p.select(at.x, at.y) } },
    ) { pressed ->
        KeyCap(if (pressed) c.keyPressed else c.key) {
            val hint = key.longPressOutput
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
            Row(
                Modifier
                    // Popup geometry is relative to the key's top-left corner, not its centre.
                    .align(Alignment.TopStart)
                    .layout { measurable, _ ->
                        val box = measurable.measure(Constraints.fixed(popup.width.roundToInt(), popup.cellHeight.roundToInt()))
                        layout(0, 0) { box.place(popup.left.roundToInt(), popup.top.roundToInt()) }
                    }
                    .shadow(3.dp, shape).background(c.popup, shape).border(1.dp, c.popupBorder, shape)
                    .padding(3.dp)
                    .semantics { contentDescription = choiceDescription(selected) },
            ) {
                for (cell in 0 until popup.count) {
                    val choice = popup.choiceAt(cell)
                    val on = choice == selected
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(if (on) c.enter else Color.Transparent, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            cased(choices[choice]), color = if (on) c.enterInk else c.keyText, maxLines = 1,
                            fontSize = if (popup.count == 1) 28.sp else 22.sp, fontWeight = FontWeight.Medium,
                        )
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
