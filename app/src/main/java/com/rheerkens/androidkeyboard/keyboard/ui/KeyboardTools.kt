package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.PlacementMode
import com.rheerkens.androidkeyboard.ui.SlimIcons

@Composable
internal fun KeyboardTools(state: KeyboardUiState, actions: KeyboardActions) {
    Column(Modifier.height((state.keyRows * state.rowHeightDp + 4).dp).padding(6.dp)) {
        val rows = listOf(
            listOf(Triple("Resize", SlimIcons.Resize, null), Triple("One-handed", SlimIcons.OneHand, PlacementMode.ONE_HANDED), Triple("Floating", SlimIcons.Floating, PlacementMode.FLOATING)),
            listOf(Triple("Split", SlimIcons.Split, PlacementMode.SPLIT), Triple("Dictionary", SlimIcons.Book, null), Triple("Settings", SlimIcons.Settings, null)),
        )
        rows.forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { (label, icon, mode) ->
                    val enabled = mode != PlacementMode.SPLIT || state.viewportWidthDp >= 480f
                    val selected = mode != null && state.placement.mode == mode
                    ToolTile(label, icon, selected, enabled, Modifier.weight(1f).fillMaxHeight()) {
                        when (label) {
                            "Resize" -> actions.onResizeKeyboard()
                            "Dictionary" -> actions.onOpenDictionary()
                            "Settings" -> actions.onOpenKeyboardSettings()
                            else -> if (enabled && mode != null) actions.onSelectPlacementMode(if (selected) PlacementMode.NORMAL else mode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolTile(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, modifier: Modifier, tap: () -> Unit) {
    val c = LocalKeyboardColors.current
    PressTarget(if (enabled) "$label${if (selected) ", selected. Tap for normal keyboard" else ""}" else "$label, requires a wider screen", modifier.padding(3.dp), onTap = if (enabled) tap else null) { pressed ->
        Column(Modifier.fillMaxSize().background(if (pressed || selected) c.keyPressed else c.key, RoundedCornerShape(10.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (enabled) c.keyText else c.muted, modifier = Modifier.size(23.dp))
            Text(label, fontSize = 12.sp, color = if (enabled) c.keyText else c.muted, textAlign = TextAlign.Center)
        }
    }
}
