package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import com.rheerkens.androidkeyboard.ui.SlimIcons

/** The enabled keyboards, in globe order, and the one in use. */
data class KeyboardChoices(val keyboards: List<TypingKeyboard>, val current: TypingKeyboard)

private const val HEADER_DP = 48
private const val ROW_DP = 48
private const val FOOTER_DP = 48
/** Margin around the card (top and bottom) plus its border. */
private const val CHROME_DP = 18

/** Height that shows [rows] keyboards without scrolling. */
fun chooserHeightDp(rows: Int): Int = CHROME_DP + HEADER_DP + ROW_DP * rows + FOOTER_DP

/**
 * Keyboard chooser, opened by long-pressing the space bar: every enabled language and layout in
 * the globe key's order, the current one checked, so any of them is one tap away however many
 * are on. Drawn in place of the keys (no popup window); tapping the margin closes it. The footer
 * links to the layout settings and to Android's picker for other keyboard apps.
 */
@Composable
fun KeyboardChooser(choices: KeyboardChoices, height: Dp, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(interactionSource = null, indication = null, onClickLabel = "Close keyboard chooser") {
                actions.onCloseKeyboardChooser()
            }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val shape = RoundedCornerShape(14.dp)
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .background(c.popup, shape)
                .border(1.dp, c.popupBorder, shape)
                // Swallow taps on the list background so they don't close the chooser.
                .clickable(interactionSource = null, indication = null, enabled = true) {},
        ) {
            Row(Modifier.fillMaxWidth().height(HEADER_DP.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Languages & layouts", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                PressTarget("Close keyboard chooser", Modifier.size(48.dp), onTap = actions::onCloseKeyboardChooser) { pressed ->
                    Box(
                        Modifier.size(32.dp).background(if (pressed) c.keyPressed else Color.Transparent, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(SlimIcons.Close, null, tint = c.keyText, modifier = Modifier.size(18.dp)) }
                }
            }
            // When the list must scroll, open it at the current keyboard.
            val scroll = rememberScrollState()
            val rowPx = with(LocalDensity.current) { ROW_DP.dp.roundToPx() }
            LaunchedEffect(choices.current) {
                scroll.scrollTo((choices.keyboards.indexOf(choices.current).coerceAtLeast(0) * rowPx - rowPx).coerceAtLeast(0))
            }
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                choices.keyboards.forEach { keyboard ->
                    ChoiceRow(keyboard, current = keyboard == choices.current) { actions.onChooseKeyboard(keyboard) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.popupBorder))
            Row(Modifier.fillMaxWidth().height(FOOTER_DP.dp)) {
                FooterAction("Layouts", SlimIcons.Settings, Modifier.weight(1f), "Choose layouts in Settings", actions::onOpenKeyboardSettings)
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(c.popupBorder))
                FooterAction("Other keyboards", SlimIcons.Globe, Modifier.weight(1f), "Switch to another keyboard app", actions::onGlobeLongPress)
            }
        }
    }
}

/**
 * One keyboard. A plain clickable rather than a key's press handler, so a drag over the rows
 * scrolls the list instead of being held by the row under the finger.
 */
@Composable
private fun ChoiceRow(keyboard: TypingKeyboard, current: Boolean, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_DP.dp)
            .selectable(selected = current, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onTap)
            .semantics(mergeDescendants = true) {
                contentDescription = if (current) "${keyboard.fullLabel}, current keyboard" else "Switch to ${keyboard.fullLabel}"
            }
            .padding(horizontal = 4.dp)
            .background(if (pressed) c.keyPressed else Color.Transparent, RoundedCornerShape(9.dp))
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            keyboard.language.name,
            color = c.keyText, fontSize = 15.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text(keyboard.layoutLabel, color = c.muted, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (current) Icon(SlimIcons.Check, null, tint = c.enter, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FooterAction(label: String, icon: ImageVector, modifier: Modifier, description: String, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    PressTarget(description, modifier.fillMaxHeight(), onTap = onTap) { pressed ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .background(if (pressed) c.keyPressed else Color.Transparent, RoundedCornerShape(9.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = c.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = c.chipText, fontSize = 13.5.sp, maxLines = 1)
        }
    }
}
