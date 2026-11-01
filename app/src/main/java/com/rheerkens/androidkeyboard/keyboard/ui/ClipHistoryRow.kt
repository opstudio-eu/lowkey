package com.rheerkens.androidkeyboard.keyboard.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.clipboard.ClipEntry
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recent clips and screenshots, newest first, scrolling sideways. Used by the minimized bar and by
 * the full keyboard's strip while no word is being typed. Tap a chip to paste it; hold to preview.
 */
@Composable
fun ClipHistoryRow(clips: List<ClipChip>, session: Int, actions: KeyboardActions, modifier: Modifier, chipHeight: Dp) {
    val listState = rememberLazyListState()
    // A lazy list keeps its first visible item in place when items are inserted before it, which
    // would push a new clip off-screen to the left. Show the newest item whenever one arrives and
    // each time the keyboard opens.
    val newest = clips.firstOrNull()?.entry?.key
    LaunchedEffect(newest, session) { listState.scrollToItem(0) }
    LazyRow(
        modifier,
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(clips, key = { it.entry.key }) { HistoryChip(it, chipHeight, actions) }
    }
}

/** One line of text, or an image thumbnail. */
@Composable
private fun HistoryChip(chip: ClipChip, height: Dp, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val latest by rememberUpdatedState(actions)
    var pressed by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    /** While previewing an image, the finger has slid up onto the preview: release opens the editor. */
    var editArmed by remember { mutableStateOf(false) }
    val editable = chip.entry !is ClipEntry.Text
    val shape = RoundedCornerShape(8.dp)
    val entry = chip.entry
    val description = when (entry) {
        is ClipEntry.Text -> "Paste copied text: ${entry.text.take(120)}"
        is ClipEntry.Image -> if (chip.pasteable) "Paste copied image" else "Copied image. This field doesn't accept images"
        is ClipEntry.Screenshot -> if (chip.pasteable) "Paste screenshot" else "Screenshot. This field doesn't accept images"
    }
    Box(
        Modifier
            .height(height)
            .clip(shape)
            .background(if (pressed) c.keyMod else c.key)
            .alpha(if (chip.pasteable) 1f else 0.4f)
            // Tap pastes; a sideways drag before the long-press timeout is left to the row's
            // scrolling; a hold previews; sliding up onto an image preview and releasing edits it.
            .pointerInput(entry.key, chip.pasteable) {
                val editLift = 36.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        var gesture = ChipGesture.HOLD
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change == null || change.isConsumed ||
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                ) {
                                    gesture = ChipGesture.CANCEL
                                    return@withTimeoutOrNull
                                }
                                if (!change.pressed) {
                                    change.consume()
                                    gesture = ChipGesture.TAP
                                    return@withTimeoutOrNull
                                }
                            }
                        }
                        when (gesture) {
                            ChipGesture.CANCEL -> Unit
                            ChipGesture.TAP -> if (chip.pasteable) {
                                latest.onKeyDown()
                                latest.onPasteHistory(entry)
                            }
                            ChipGesture.HOLD -> {
                                latest.onKeyDown()
                                previewing = true
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) {
                                        if (editArmed) latest.onEditClip(entry)
                                        break
                                    }
                                    val armed = editable && change.position.y < -editLift
                                    if (armed != editArmed) {
                                        editArmed = armed
                                        if (armed) latest.onKeyDown()
                                    }
                                }
                            }
                        }
                    } finally {
                        pressed = false
                        previewing = false
                        editArmed = false
                    }
                }
            }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (!chip.pasteable) disabled()
                if (chip.pasteable) onClick { actions.onPasteHistory(entry); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (previewing) AbovePopup { ClipPreview(entry, editArmed, actions) }
        when (entry) {
            is ClipEntry.Text -> Text(
                entry.text.trim().replace(Regex("\\s+"), " "),
                color = c.chipText, fontSize = if (height > 30.dp) 13.sp else 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp).padding(horizontal = 10.dp),
            )
            is ClipEntry.Image -> Thumbnail(entry.thumbnail, height)
            is ClipEntry.Screenshot -> Thumbnail(entry.thumbnail, height)
        }
    }
}

@Composable
private fun Thumbnail(bitmap: Bitmap?, height: Dp) {
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, Modifier.size(width = height * 4 / 3, height = height), contentScale = ContentScale.Crop)
    } else {
        Icon(SlimIcons.Clipboard, null, tint = LocalKeyboardColors.current.muted, modifier = Modifier.padding(horizontal = 10.dp).size(18.dp))
    }
}

/** The hold-to-preview card: the full text, or the image as large as fits above the keyboard. */
@Composable
private fun ClipPreview(entry: ClipEntry, editArmed: Boolean, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    val shape = RoundedCornerShape(12.dp)
    val small = when (entry) {
        is ClipEntry.Text -> null
        is ClipEntry.Image -> entry.thumbnail
        is ClipEntry.Screenshot -> entry.thumbnail
    }
    val large by produceState<Bitmap?>(null, entry.key) {
        if (entry !is ClipEntry.Text) value = withContext(Dispatchers.IO) { actions.loadPreview(entry) }
    }
    Box(
        Modifier
            .shadow(8.dp, shape)
            .background(c.popup, shape)
            .border(if (editArmed) 3.dp else 1.dp, if (editArmed) c.enter else c.popupBorder, shape)
            .padding(6.dp),
    ) {
        val bitmap = large ?: small
        if (entry is ClipEntry.Text) {
            Text(
                entry.text.trim(), color = c.keyText, fontSize = 14.sp, maxLines = 12, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 320.dp).padding(6.dp),
            )
        } else if (bitmap != null) {
            // Fit within 300 × 380 dp, keeping the image's shape.
            val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
            val (w, h) = if (ratio >= 300f / 380f) 300.dp to (300f / ratio).dp else (380f * ratio).dp to 380.dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap.asImageBitmap(), "Preview", Modifier.size(w, h).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    if (editArmed) "Release to edit" else "↑ Slide up to edit",
                    color = if (editArmed) c.enter else c.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
        }
    }
}

private enum class ChipGesture { TAP, HOLD, CANCEL }
