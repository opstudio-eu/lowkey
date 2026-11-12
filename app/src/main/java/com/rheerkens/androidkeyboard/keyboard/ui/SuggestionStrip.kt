package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.dictionary.Suggestion
import com.rheerkens.androidkeyboard.clipboard.ClipboardContent
import com.rheerkens.androidkeyboard.ui.SlimIcons

/**
 * Clipboard history when idle (led by the masked current clip when it is sensitive, or the current
 * clip alone if the history is empty), word suggestions while typing, or temporary prompts and
 * notes. History also works with word suggestions disabled.
 */
@Composable
fun RowScope.SuggestionStrip(strip: StripState, clips: List<ClipChip>, session: Int, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
        when {
            strip.showsClipHistory(clips.isNotEmpty()) -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // Sensitive clips stay out of history, so keep the masked current clip pasteable in front of it.
                val sensitive = strip.clipboard?.takeIf { it.sensitive }
                if (sensitive != null) {
                    Box(Modifier.width(148.dp).fillMaxHeight()) {
                        ClipboardChip(sensitive, strip.imagePasteSupported, actions)
                    }
                }
                ClipHistoryRow(clips, session, actions, Modifier.weight(1f).fillMaxHeight(), chipHeight = 34.dp)
            }
            strip.note != null -> Text(
                strip.note.text,
                color = if (strip.note.warn) c.amber else c.muted,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            strip.addPrompt != null -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                PressTarget(
                    "Add ${strip.addPrompt} to dictionary",
                    Modifier.weight(1f).fillMaxHeight(),
                    onTap = { actions.onAddToDictionary(strip.addPrompt) },
                ) { pressed ->
                    Row(
                        Modifier.fillMaxSize().background(if (pressed) c.keyMod else c.background, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(SlimIcons.Plus, null, tint = c.enter, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Add “${strip.addPrompt}” to dictionary",
                            color = c.chipText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                StripIconButton(SlimIcons.Close, "Dismiss", onTap = actions::onDismissStripPrompt)
            }
            !strip.enabled -> Spacer(Modifier.fillMaxSize())
            !strip.typingWord -> ClipboardChip(strip.clipboard, strip.imagePasteSupported, actions)
            else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                strip.slots.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.width(1.dp).height(20.dp).background(c.chipDivider))
                    SuggestionChip(s, Modifier.weight(1f).fillMaxHeight(), center = i == 1, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun ClipboardChip(content: ClipboardContent?, imageSupported: Boolean, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    if (content == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SlimIcons.Clipboard, null, tint = c.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clipboard empty", color = c.muted, fontSize = 13.sp)
        }
        return
    }
    val label = when (content) {
        is ClipboardContent.Text -> if (content.sensitive) "Copied text" else content.text
        is ClipboardContent.Image -> if (imageSupported) "Paste image" else "Image · unsupported here"
    }
    val description = when (content) {
        is ClipboardContent.Text -> if (content.sensitive) "Paste copied text" else "Paste clipboard text: ${content.text.take(120)}"
        is ClipboardContent.Image -> if (imageSupported) "Paste clipboard image" else "Clipboard image. This field doesn't accept images"
    }
    PressTarget(
        description, Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
        gestureKey = content, onDown = actions::onKeyDown,
        onTap = { actions.onPasteClipboard(content) },
    ) { pressed ->
        Row(
            Modifier.fillMaxSize().background(if (pressed) c.keyMod else c.key, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val thumbnail = (content as? ClipboardContent.Image)?.thumbnail
            if (thumbnail != null) {
                Image(thumbnail.asImageBitmap(), "Clipboard image preview",
                    Modifier.size(30.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            } else {
                Icon(SlimIcons.Clipboard, null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (content is ClipboardContent.Image && !imageSupported) c.muted else c.chipText,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SuggestionChip(s: Suggestion?, modifier: Modifier, center: Boolean, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    if (s == null) {
        Spacer(modifier)
        return
    }
    val desc = when {
        s.emojiName != null -> "Insert emoji: ${s.emojiName}"
        s.isLiteral -> "Typed word ${s.text}. Long-press to add to dictionary"
        s.isPersonal -> "Insert ${s.text}, saved term"
        else -> "Insert ${s.text}"
    }
    PressTarget(
        desc, modifier, gestureKey = s,
        onTap = { actions.onSuggestion(s) },
        onLongPress = if (s.emojiName == null) ({ actions.onSuggestionLongPress(s) }) else null,
    ) { pressed ->
        Row(
            Modifier.fillMaxSize().background(if (pressed) c.keyMod else c.background).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (s.isPersonal) {
                Box(Modifier.size(6.dp).background(c.muted, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                s.text,
                color = if (s.isPersonal || center) c.chipSaved else c.chipText,
                fontSize = if (s.emojiName != null) 22.sp else 14.sp,
                fontWeight = if (center || s.isPersonal) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StripIconButton(
    icon: ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color = LocalKeyboardColors.current.muted,
    onTap: () -> Unit,
) {
    PressTarget(description, Modifier.size(44.dp), onTap = onTap) { pressed ->
        Box(
            Modifier.size(40.dp).background(
                if (pressed) LocalKeyboardColors.current.divider else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(10.dp),
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
    }
}
