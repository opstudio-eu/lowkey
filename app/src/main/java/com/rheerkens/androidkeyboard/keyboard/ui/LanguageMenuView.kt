package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.AlternatesPopup
import com.rheerkens.androidkeyboard.keyboard.LanguageMenu
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import kotlin.math.roundToInt

/** The emoji key's language menu while it is open, in the keyboard content's pixel coordinates. */
data class OpenLanguageMenu(
    val choices: List<TypingKeyboard>,
    val popup: AlternatesPopup,
    /** The emoji key's top-left corner; [AlternatesPopup] places the cells relative to it. */
    val keyLeft: Float,
    val keyTop: Float,
    /** The cell under the finger while held; null when the finger is off the menu. */
    val selected: Int?,
    /** Still held (false), or released and waiting for a tap (true). */
    val sticky: Boolean,
)

/**
 * Shared by the emoji key, which opens the menu and drives it while held, and the keyboard content,
 * which draws it above every key and, once released, catches the taps.
 */
@Stable
class LanguageMenuState {
    var open by mutableStateOf<OpenLanguageMenu?>(null)

    /** The keyboard content's layout, which the menu is drawn in. */
    var host: LayoutCoordinates? = null

    fun close() { open = null }
}

/**
 * Draws the open language menu. While held it only shows the cell under the finger; once released
 * it stays open: tap a language to switch, tap anywhere else (or Back) to close.
 */
@Composable
fun BoxScope.LanguageMenuOverlay(state: LanguageMenuState, actions: KeyboardActions) {
    val menu = state.open ?: return
    val c = LocalKeyboardColors.current
    val density = LocalDensity.current
    val popup = menu.popup
    if (menu.sticky) {
        DisposableEffect(state, actions) {
            actions.onLayoutBackHandler { state.close(); true }
            onDispose { actions.onLayoutBackHandler(null) }
        }
        // Catches the next touch outside the menu: it only closes the menu, so it types nothing.
        Box(
            Modifier.matchParentSize().pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    state.close()
                }
            },
        )
    }
    val shape = RoundedCornerShape(10.dp)
    fun describe(choice: Int?): String =
        choice?.let { "Switch to ${menu.choices[it].fullLabel}" } ?: "Slide back to choose a language"
    Column(
        Modifier
            .offset { IntOffset((menu.keyLeft + popup.left).roundToInt(), (menu.keyTop + popup.top).roundToInt()) }
            .size(with(density) { popup.width.toDp() }, with(density) { popup.height.toDp() })
            .shadow(3.dp, shape).background(c.popup, shape).border(1.dp, c.popupBorder, shape)
            .semantics {
                paneTitle = "Keyboard languages"
                if (!menu.sticky) contentDescription = describe(menu.selected)
            },
    ) {
        for (row in 0 until popup.rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (column in 0 until popup.columns) {
                    val choice = popup.choiceAt(column, row)
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    if (choice == null) {
                        Box(cell)
                        continue
                    }
                    val keyboard = menu.choices[choice]
                    PressTarget(
                        if (choice == 0) "${keyboard.fullLabel}, current" else describe(choice),
                        cell,
                        gestureKey = keyboard.id,
                        onDown = actions::onKeyDown,
                        onTap = {
                            state.close()
                            if (choice != 0) actions.onSelectKeyboard(keyboard.id)
                        },
                    ) { pressed ->
                        val on = if (menu.sticky) pressed else choice == menu.selected
                        val cellShape = RoundedCornerShape(7.dp)
                        Box(
                            Modifier.fillMaxSize().padding(3.dp)
                                .background(if (on) c.enter else Color.Transparent, cellShape)
                                .then(if (choice == 0 && !on) Modifier.border(1.5.dp, c.enter, cellShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            val ink = if (on) c.enterInk else c.keyText
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(LanguageMenu.code(keyboard), color = ink, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(
                                    LanguageMenu.caption(keyboard), color = if (on) c.enterInk else c.muted, fontSize = 10.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
