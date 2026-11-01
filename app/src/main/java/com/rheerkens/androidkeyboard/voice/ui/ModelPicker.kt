package com.rheerkens.androidkeyboard.voice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardActions
import com.rheerkens.androidkeyboard.keyboard.ui.LocalKeyboardColors
import com.rheerkens.androidkeyboard.keyboard.ui.PressTarget
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.ModelInfo
import com.rheerkens.androidkeyboard.models.ModelStoreState
import com.rheerkens.androidkeyboard.ui.SlimIcons

/**
 * Model picker list, drawn inside the keyboard above the voice row (no separate popup window).
 * Installed models (check on the active one), available models with download/progress/cancel,
 * and a footer with Manage models and Voice settings. Tapping the margin around the list closes it.
 */
@Composable
fun ModelPicker(models: ModelStoreState, height: Dp, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val installed = ModelCatalog.models.filter { it.id in models.installed }
    val available = ModelCatalog.models.filter { it.id !in models.installed }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(interactionSource = null, indication = null, onClickLabel = "Close model picker") {
                actions.onClosePicker()
            }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
    ) {
        val shape = RoundedCornerShape(14.dp)
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .background(c.popup, shape)
                .border(1.dp, c.popupBorder, shape)
                // Swallow taps on the list background so they don't close the picker.
                .clickable(interactionSource = null, indication = null, enabled = true) {},
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                SectionLabel("Installed")
                if (installed.isEmpty()) {
                    Text(
                        "No models yet. Download one below.",
                        color = c.muted, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                installed.forEach { InstalledRow(it, active = it.id == models.activeId, actions) }
                if (available.isNotEmpty()) {
                    SectionLabel("Available")
                    available.forEach { AvailableRow(it, models.downloads[it.id], models.errors[it.id], actions) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.popupBorder))
            Row(Modifier.fillMaxWidth().height(40.dp)) {
                FooterAction("Manage models", SlimIcons.Models, Modifier.weight(1f), actions::onOpenModels)
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(c.popupBorder))
                FooterAction("Voice settings", SlimIcons.Settings, Modifier.weight(1f), actions::onOpenVoiceSettings)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = LocalKeyboardColors.current.muted,
        fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** One-line hint, e.g. `Live · 246 MB`. */
private fun hint(m: ModelInfo): String = "${m.language} · ${m.sizeShort}"

@Composable
private fun InstalledRow(m: ModelInfo, active: Boolean, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    PickerRow(
        if (active) "${m.name}, active" else "Use ${m.name}",
        onTap = { actions.onSelectModel(m.id) },
    ) {
        NameAndHint(m.name, hint(m), bold = active)
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (active) Icon(SlimIcons.Check, null, tint = c.enter, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AvailableRow(m: ModelInfo, progress: Float?, error: String?, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    PickerRow(
        if (progress != null) "Downloading ${m.name}" else "Download ${m.name}, ${m.sizeLabel}",
        onTap = if (progress == null) ({ actions.onDownloadModel(m.id) }) else null,
    ) {
        if (progress == null) {
            NameAndHint(m.name, if (error != null) "Download failed · tap to retry" else hint(m), bold = false)
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(SlimIcons.Download, null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
        } else {
            NameAndHint(m.name, null, bold = false)
            Box(Modifier.width(64.dp).height(4.dp).background(c.busy, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.enter, RoundedCornerShape(2.dp)))
            }
            Text(
                "${(progress * 100).toInt()}%",
                color = c.muted, fontSize = 12.5.sp, maxLines = 1,
                modifier = Modifier.width(40.dp).padding(start = 6.dp),
            )
            PressTarget("Cancel download of ${m.name}", Modifier.size(32.dp), onTap = { actions.onCancelDownload(m.id) }) { pressed ->
                Box(
                    Modifier.size(28.dp).background(if (pressed) c.keyPressed else c.pill, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SlimIcons.Close, null, tint = c.keyText, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Name plus muted hint, filling the row up to the trailing icon. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.NameAndHint(name: String, hint: String?, bold: Boolean) {
    val c = LocalKeyboardColors.current
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            color = c.keyText, fontSize = 14.5.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (hint != null) {
            Spacer(Modifier.width(10.dp))
            Text(hint, color = c.muted, fontSize = 12.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PickerRow(
    description: String,
    onTap: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val c = LocalKeyboardColors.current
    PressTarget(description, Modifier.fillMaxWidth().height(40.dp), onTap = onTap) { pressed ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .background(if (pressed && onTap != null) c.keyPressed else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(9.dp))
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun FooterAction(label: String, icon: ImageVector, modifier: Modifier, onTap: () -> Unit) {
    val c = LocalKeyboardColors.current
    PressTarget(label, modifier.fillMaxHeight(), onTap = onTap) { pressed ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .background(if (pressed) c.keyPressed else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(9.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = c.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = c.chipText, fontSize = 13.5.sp, maxLines = 1)
        }
    }
}
