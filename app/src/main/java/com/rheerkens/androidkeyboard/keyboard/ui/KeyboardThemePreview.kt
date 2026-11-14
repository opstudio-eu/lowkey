package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.ui.SlimIcons

/**
 * A small static keyboard in [c]'s colours for the theme editor: the suggestion strip, letter and
 * function keys (one pressed, Shift on), Enter, an accent popup and the voice row's model button,
 * over the same background (colour or dimmed [image]) the keyboard draws.
 * It draws only; the real keyboard is tried with Test keyboard.
 */
@Composable
fun KeyboardThemePreview(c: KeyboardColors, modifier: Modifier = Modifier, image: KeyboardBackgroundImage? = null) {
    // This non-interactive color illustration has fixed proportions, like a screenshot.
    // Its surrounding editor controls still follow the user's system text size.
    val density = LocalDensity.current.density
    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f)) {
        KeyboardColorIllustration(c, modifier, image)
    }
}

@Composable
private fun KeyboardColorIllustration(c: KeyboardColors, modifier: Modifier, image: KeyboardBackgroundImage?) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .keyboardBackground(c, image)
            .border(1.dp, c.divider, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = "Keyboard preview" },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SlimIcons.Book, null, tint = c.muted, modifier = Modifier.padding(horizontal = 6.dp).size(16.dp))
            listOf("hello" to false, "Lowkey" to true, "help" to false).forEachIndexed { i, (word, saved) ->
                if (i > 0) Box(Modifier.size(width = 1.dp, height = 16.dp).background(c.chipDivider))
                Text(
                    word,
                    color = if (saved) c.chipSaved else c.chipText,
                    fontSize = 13.sp,
                    fontWeight = if (saved) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Icon(SlimIcons.Mic, null, tint = c.keyText, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            MenuDots(c.muted, Modifier.padding(start = 3.dp, end = 6.dp))
        }
        Box {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PreviewRow { "qwertyuiop".forEach { ch -> Key(c, ch.toString(), if (ch == 'o') c.keyPressed else c.key) } }
                PreviewRow {
                    Key(c, null, c.keyOn, SlimIcons.ShiftFilled, weight = 1.4f)
                    "asdfghj".forEach { ch -> Key(c, ch.toString(), c.key) }
                    Key(c, null, c.keyMod, SlimIcons.Backspace, weight = 1.4f)
                }
                PreviewRow {
                    Key(c, "?123", c.keyMod, weight = 1.5f, size = 12)
                    Key(c, null, c.keyMod, SlimIcons.Globe)
                    Key(c, "English", c.key, weight = 4.5f, ink = c.spaceText, size = 12)
                    Key(c, ".", c.keyMod)
                    Key(c, null, c.enter, SlimIcons.Enter, weight = 1.5f, ink = c.enterInk)
                }
            }
            // An accent popup over the top row, as when E is held; O shows the pressed state.
            Row(
                Modifier
                    .padding(start = 44.dp, top = 0.dp)
                    .background(c.popup, RoundedCornerShape(8.dp))
                    .border(1.dp, c.popupBorder, RoundedCornerShape(8.dp))
                    .padding(3.dp),
            ) {
                listOf("é", "è", "ê").forEachIndexed { i, ch ->
                    Box(
                        Modifier.size(width = 24.dp, height = 26.dp)
                            .background(if (i == 0) c.enter else Color.Transparent, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(ch, color = if (i == 0) c.enterInk else c.keyText, fontSize = 14.sp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.background(c.pill, RoundedCornerShape(15.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).background(c.green, CircleShape))
                Text("Model", color = c.keyText, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
            Text("Listening", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Box(
                Modifier.background(c.stop, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(SlimIcons.Stop, null, tint = c.stopInk, modifier = Modifier.size(14.dp))
                    Text("Stop", color = c.stopInk, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
private fun RowScope.Key(
    c: KeyboardColors,
    label: String?,
    fill: Color,
    icon: ImageVector? = null,
    weight: Float = 1f,
    ink: Color = c.keyText,
    size: Int = 15,
) {
    Box(
        Modifier.weight(weight).height(34.dp).background(fill, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp))
        else if (label != null) Text(label, color = ink, fontSize = size.sp, maxLines = 1)
    }
}
