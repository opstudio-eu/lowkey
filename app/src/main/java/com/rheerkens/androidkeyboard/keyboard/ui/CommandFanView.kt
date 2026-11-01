package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.rheerkens.androidkeyboard.keyboard.CommandFan
import com.rheerkens.androidkeyboard.keyboard.FanCommand
import com.rheerkens.androidkeyboard.keyboard.FanSector
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What the fan offers in the current editor: its declared action ([action], null when it declares
 * none, so the fan sends a plain Enter key), whether the field takes a new line ([newLine]; New
 * line is dimmed otherwise), and whether a clipboard clear can be undone ([undoClear]: Undo clear
 * then takes Clear clips' place).
 */
@Immutable
data class FanSpec(val action: EnterKind?, val newLine: Boolean, val undoClear: Boolean = false) {
    val sectors: List<FanSector> = CommandFan.sectors(undoClear)
    val actionLabel: String get() = action?.label ?: "Enter"

    /** A dimmed wedge stays in place but does nothing; releasing on it cancels. */
    fun enabled(command: FanCommand): Boolean = command != FanCommand.NEW_LINE || newLine

    fun label(command: FanCommand): String = when (command) {
        FanCommand.WORDS -> "Words"
        FanCommand.SPACE -> "Space"
        FanCommand.CLEAR_CLIPBOARD -> "Clear\nclips"
        FanCommand.UNDO_CLEAR -> "Undo\nclear"
        FanCommand.ACTION -> actionLabel
        FanCommand.NEW_LINE -> "New line"
        FanCommand.KEYBOARD_ONCE -> "Letters"
        FanCommand.NUMBERS_ONCE -> "Numbers"
    }

    /** Screen-reader name of each command. */
    fun description(command: FanCommand): String = when (command) {
        FanCommand.WORDS -> "Erase words"
        FanCommand.SPACE -> "Insert a space"
        FanCommand.CLEAR_CLIPBOARD -> "Clear clipboard history"
        FanCommand.UNDO_CLEAR -> "Undo clearing the clipboard history"
        FanCommand.ACTION -> if (action == null) "Enter" else actionLabel
        FanCommand.NEW_LINE -> "New line"
        FanCommand.KEYBOARD_ONCE -> "Show the keyboard for this field only"
        FanCommand.NUMBERS_ONCE -> "Show the number keys for this field only"
    }
}

/**
 * Room above the button (the up-left wedges), to its right (the tip of the Words wedge, the
 * only one right of vertical) and below (inside the screen).
 */
private val ABOVE = (CommandFan.THIRD + 10).dp
private val LEFT = (CommandFan.THIRD + 6).dp
private val RIGHT = (CommandFan.THIRD * cos(Math.toRadians(CommandFan.WORDS_FROM.toDouble())).toFloat() + 6).dp
private val BELOW = 30.dp

/**
 * The fan, drawn in its own window centred on [origin] (the round button's centre, in the key's coordinates).
 * The touch stays with the keyboard window until release; this window only draws.
 */
@Composable
fun CommandFanPopup(spec: FanSpec, hot: FanCommand?, origin: Offset) {
    val density = LocalDensity.current
    val provider = remember(origin, density) {
        with(density) { FanAnchor(origin, LEFT.roundToPx(), ABOVE.roundToPx()) }
    }
    Popup(
        popupPositionProvider = provider,
        // Not clipped: near the screen's right edge the window must stay centred on the finger.
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false, clippingEnabled = false),
    ) { CommandFanView(spec, hot) }
}

private class FanAnchor(private val origin: Offset, private val left: Int, private val above: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        anchorBounds.left + origin.x.roundToInt() - left,
        anchorBounds.top + origin.y.roundToInt() - above,
    )
}

@Composable
private fun CommandFanView(spec: FanSpec, hot: FanCommand?) {
    val c = LocalKeyboardColors.current
    Box(Modifier.requiredSize(LEFT + RIGHT, ABOVE + BELOW)) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(LEFT.toPx(), ABOVE.toPx())
            val stroke = Stroke(1.5.dp.toPx())
            // A command with several wedges (the keyboard) is drawn as one shape.
            for ((command, wedges) in spec.sectors.groupBy { it.command }) {
                val path = wedges.map { wedge(center, it.inner.dp.toPx(), it.outer.dp.toPx(), it.fromDeg, it.toDeg) }
                    .reduce { a, b -> Path.combine(PathOperation.Union, a, b) }
                drawPath(path, fillOf(command, command == hot, c))
                drawPath(path, c.popupBorder, style = stroke)
            }
            // The middle stays clear: the round Backspace button shows through it.
        }
        // One label per command, in its last wedge (the keyboard's outer ring).
        for (s in spec.sectors.groupBy { it.command }.values.map { it.last() }) {
            val on = s.command == hot
            val ink = when {
                !spec.enabled(s.command) -> c.keyText.copy(alpha = 0.35f)
                on && s.command == FanCommand.ACTION -> c.enterInk
                on && s.command == FanCommand.CLEAR_CLIPBOARD -> c.dangerInk
                else -> c.keyText
            }
            val radius = (s.inner + s.outer) / 2
            val angle = Math.toRadians(((s.fromDeg + s.toDeg) / 2).toDouble())
            FanLabel(
                iconOf(s.command, spec.action), glyphOf(s.command), spec.label(s.command), ink,
                Modifier.centeredAt(
                    (LEFT.value + radius * cos(angle)).toFloat(),
                    (ABOVE.value - radius * sin(angle)).toFloat(),
                ),
            )
        }
    }
}

@Composable
private fun FanLabel(icon: ImageVector?, glyph: String?, text: String, ink: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
        else Text(glyph.orEmpty(), color = ink, fontSize = if (glyph == "␣") 18.sp else 15.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
        Text(text, color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp, textAlign = TextAlign.Center)
    }
}

/** Places the element centred on ([x], [y]) dp inside its parent. */
private fun Modifier.centeredAt(x: Float, y: Float) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { p.place((x.dp.toPx() - p.width / 2f).roundToInt(), (y.dp.toPx() - p.height / 2f).roundToInt()) }
}

private fun fillOf(command: FanCommand, on: Boolean, c: KeyboardColors): Color = when {
    !on -> c.popup
    command == FanCommand.ACTION -> c.enter
    command == FanCommand.CLEAR_CLIPBOARD -> c.danger
    else -> c.keyOn
}

/** Text standing in for an icon: the space mark and the keyboard halves. */
private fun glyphOf(command: FanCommand): String? = when (command) {
    FanCommand.SPACE -> "␣"
    FanCommand.NUMBERS_ONCE -> "123"
    FanCommand.KEYBOARD_ONCE -> "ABC"
    else -> null
}

private fun iconOf(command: FanCommand, action: EnterKind?): ImageVector? = when (command) {
    FanCommand.WORDS -> SlimIcons.ChevronUp
    FanCommand.SPACE -> null
    FanCommand.CLEAR_CLIPBOARD -> SlimIcons.Trash
    FanCommand.UNDO_CLEAR -> SlimIcons.Undo
    FanCommand.ACTION -> when (action) {
        EnterKind.SEND -> SlimIcons.Send
        EnterKind.SEARCH -> SlimIcons.Search
        EnterKind.GO, EnterKind.NEXT -> SlimIcons.ChevronRight
        EnterKind.DONE -> SlimIcons.Check
        else -> SlimIcons.Enter
    }
    FanCommand.NEW_LINE -> SlimIcons.Enter
    FanCommand.KEYBOARD_ONCE, FanCommand.NUMBERS_ONCE -> null
}

/** A ring segment between [from] and [to] degrees (counter-clockwise from the right). */
private fun wedge(center: Offset, inner: Float, outer: Float, from: Float, to: Float): Path = Path().apply {
    val sweep = to - from
    // Canvas angles run clockwise, so the counter-clockwise range [from, to] starts at -to.
    arcTo(Rect(center, outer), -to, sweep, forceMoveTo = true)
    arcTo(Rect(center, inner), -from, -sweep, forceMoveTo = false)
    close()
}
