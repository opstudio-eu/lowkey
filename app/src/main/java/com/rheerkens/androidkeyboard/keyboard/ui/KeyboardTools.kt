package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.PlacementMode
import com.rheerkens.androidkeyboard.ui.SlimIcons

@Composable
internal fun KeyboardTools(state: KeyboardUiState, actions: KeyboardActions) {
    val effectiveMode = state.placement.frame(state.viewportWidthDp, state.viewportHeightDp).mode
    fun modeTool(label: String, icon: ImageVector, mode: PlacementMode): Tool {
        val enabled = mode != PlacementMode.SPLIT || state.viewportWidthDp >= 480f
        val selected = effectiveMode == mode
        return Tool(label, icon, selected, enabled,
            if (!enabled) "Wider screen required" else if (selected) "On" else "Off") {
            actions.onSelectPlacementMode(if (selected) PlacementMode.NORMAL else mode)
        }
    }
    val panelHeight = state.keyRows * state.rowHeightDp + 4
    val rowHeight = ((panelHeight - 12) / 3f).coerceAtLeast(60f).dp
    Column(Modifier.height(panelHeight.dp).verticalScroll(rememberScrollState()).padding(6.dp)) {
        val rows = listOf(
            listOf(Tool("Size & position", SlimIcons.Resize, tap = actions::onResizeKeyboard),
                modeTool("One-handed", SlimIcons.OneHand, PlacementMode.ONE_HANDED),
                modeTool("Floating", SlimIcons.Floating, PlacementMode.FLOATING)),
            listOf(modeTool("Split", SlimIcons.Split, PlacementMode.SPLIT),
                Tool("Number row", SlimIcons.Keyboard, state.numberRow, status = if (state.numberRow) "On" else "Off") {
                    actions.onSetNumberRow(!state.numberRow)
                },
                Tool("Settings", SlimIcons.Settings, tap = actions::onOpenKeyboardSettings)),
            listOf(
                Tool(if (state.toolsReturnToMinimized) "Show keyboard" else "Minimize keyboard",
                    if (state.toolsReturnToMinimized) SlimIcons.Keyboard else SlimIcons.ChevronDown) {
                    actions.onSetMinimized(!state.toolsReturnToMinimized)
                },
                Tool("Voice model", SlimIcons.Models, status = state.models.activeModel?.name ?: "Choose model",
                    tap = actions::onOpenVoicePicker),
                Tool("Voice settings", SlimIcons.Mic, tap = actions::onOpenVoiceSettings),
            ),
        )
        rows.forEach { row ->
            Row(Modifier.height(rowHeight)) {
                row.forEach { tool ->
                    ToolTile(tool, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

private data class Tool(val label: String, val icon: ImageVector, val selected: Boolean = false,
    val enabled: Boolean = true, val status: String? = null, val tap: () -> Unit)

@Composable
private fun ToolTile(tool: Tool, modifier: Modifier) {
    val c = LocalKeyboardColors.current
    val tint = if (tool.enabled) c.keyText else c.muted
    val description = tool.label + if (tool.selected && tool.label != "Number row") ", tap for normal keyboard" else ""
    PressTarget(description, modifier.padding(3.dp).semantics {
        if (!tool.enabled) disabled()
        tool.status?.let { stateDescription = it; selected = tool.selected }
    }, onTap = if (tool.enabled) tool.tap else null) { pressed ->
        Box(Modifier.fillMaxSize().background(if (pressed || tool.selected) c.keyPressed else c.key, RoundedCornerShape(10.dp))) {
            if (tool.selected) Icon(SlimIcons.Check, null, tint = tint,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp))
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(tool.icon, null, tint = tint, modifier = Modifier.size(23.dp))
                Text(tool.label, fontSize = 12.sp, color = tint, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                tool.status?.let { Text(if (tool.enabled) it else "Wide only", fontSize = 10.sp, color = tint,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
