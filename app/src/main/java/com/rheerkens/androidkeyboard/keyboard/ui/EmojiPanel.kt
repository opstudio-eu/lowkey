package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.EmojiCatalog
import com.rheerkens.androidkeyboard.keyboard.EmojiItem
import com.rheerkens.androidkeyboard.keyboard.Key
import com.rheerkens.androidkeyboard.keyboard.emojiReturnIndex
import com.rheerkens.androidkeyboard.ui.SlimIcons

private const val RECENT = "Recent"
private val categoryIcons = mapOf(
    "Smileys & Emotion" to "😀", "People & Body" to "👋", "Animals & Nature" to "🐾",
    "Food & Drink" to "🍕", "Travel & Places" to "🚗", "Activities" to "⚽",
    "Objects" to "💡", "Symbols" to "🔣", "Flags" to "🏳️", RECENT to "◷",
)

/** Height of the strip under the emoji sheet where the bottom key row peeks out. */
private val RETURN_STRIP = 34.dp
private val EMOJI_HEADER = 48.dp
/** Emoji cells shrink to fit whole rows, but not below this. */
private val MIN_EMOJI_CELL = 36.dp

/**
 * The bottom key row as it lies under the emoji sheet: its keys, and when the keyboard is split,
 * the key-weight units of one half ([splitUnits]) and the gap between the halves.
 */
internal data class PeekRow(val keys: List<Key>, val splitUnits: Float? = null, val splitGapDp: Float = 0f)

/**
 * Uses the active keyboard's frame; no separate popup can escape a narrow or floating panel.
 * The panel is a sheet over the keys: the tops of the bottom row peek out beneath it, and the key
 * that opened emoji shows as a lit keyboard tab. Tapping that strip, or swiping the sheet's top or
 * bottom edge down, returns to letters.
 */
@Composable
internal fun EmojiPanel(catalog: EmojiCatalog?, recent: List<String>, height: Dp, peek: PeekRow, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    var category by remember(catalog) {
        mutableStateOf(if (recent.isNotEmpty()) RECENT else catalog?.categories?.firstOrNull().orEmpty())
    }
    var variants by remember(catalog) { mutableStateOf<EmojiItem?>(null) }
    val visible = when {
        catalog == null -> emptyList()
        variants != null -> variants!!.variants.mapNotNull { catalog.find(it.value) }
        category == RECENT -> recent.mapNotNull(catalog::find)
        else -> catalog.entries.filter { it.category == category }
    }
    Column(Modifier.fillMaxWidth().height(height)) {
        Row(Modifier.fillMaxWidth().height(EMOJI_HEADER).keyboardBarSwipe(minimized = false, onToggle = actions::onCloseEmoji),
            verticalAlignment = Alignment.CenterVertically) {
            if (variants != null) {
                PressTarget("Back to emoji categories", Modifier.width(48.dp).fillMaxHeight(), onTap = { variants = null }) {
                    Text("‹", color = c.keyText, fontSize = 30.sp, modifier = Modifier.align(Alignment.Center))
                }
                Text("Skin tones", color = c.keyText, fontSize = 13.sp, modifier = Modifier.weight(1f))
            } else {
                LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(start = 4.dp)) {
                    items(listOf(RECENT) + catalog?.categories.orEmpty(), key = { it }) { name ->
                        PressTarget(name, Modifier.size(48.dp).semantics {
                            stateDescription = if (category == name) "Selected" else "Not selected"
                        }, onTap = { category = name }) { pressed ->
                            Box(Modifier.fillMaxSize().padding(3.dp)
                                .background(if (pressed || category == name) c.keyPressed else Color.Transparent, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center) {
                                Text(categoryIcons[name] ?: name.take(1), fontSize = 24.sp, color = c.keyText)
                            }
                        }
                    }
                }
            }
            PressTarget("Backspace", Modifier.width(48.dp).fillMaxHeight(),
                onDown = actions::onKeyDown, onTap = actions::onBackspace) {
                Icon(SlimIcons.Backspace, null, tint = c.keyText, modifier = Modifier.align(Alignment.Center).size(22.dp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Whole rows only, so the sheet's lower edge never cuts an emoji in half.
            val rows = (maxHeight / MIN_EMOJI_CELL).toInt().coerceAtLeast(1)
            val cell = maxHeight / rows
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (catalog == null) "Loading emoji…" else if (category == RECENT) "No recent emoji yet" else "No emoji available",
                        color = c.muted, fontSize = 13.sp)
                }
            } else {
                key(category, variants?.value) {
                    LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                        items(visible, key = { it.value }) { item ->
                            val hasVariants = variants == null && item.variants.size > 1
                            PressTarget(item.name + if (hasVariants) ". Hold for skin tones" else "",
                                Modifier.fillMaxWidth().height(cell), gestureKey = item.value,
                                onDown = actions::onKeyDown,
                                onTap = { actions.onChooseEmoji(item.value); if (variants != null) variants = null },
                                onLongPress = if (hasVariants) ({ variants = item }) else null) { pressed ->
                                Box(Modifier.fillMaxSize().padding(2.dp)
                                    .background(if (pressed) c.keyPressed else c.key, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text(item.value, fontSize = if (cell < 44.dp) 24.sp else 27.sp, maxLines = 1, overflow = TextOverflow.Clip)
                                }
                            }
                        }
                    }
                }
            }
        }
        ReturnStrip(peek, actions::onCloseEmoji)
    }
}

/**
 * The bottom key row peeking out under the emoji sheet, laid out as [KeysPanel] lays it out. The
 * whole strip is one "Back to letters" button, so a hurried tap near the bottom edge still lands.
 */
@Composable
private fun ReturnStrip(peek: PeekRow, onClose: () -> Unit) {
    val c = LocalKeyboardColors.current
    val tab = emojiReturnIndex(peek.keys)
    PressTarget("Back to letters", Modifier.fillMaxWidth().height(RETURN_STRIP)
        .keyboardBarSwipe(minimized = false, onToggle = onClose), onTap = onClose) { pressed ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 3.dp)) {
            val unit = peek.splitUnits?.let { ((maxWidth - peek.splitGapDp.dp) / (2 * it)).coerceAtLeast(1.dp) }
            Row(Modifier.fillMaxSize()) {
                peek.keys.forEachIndexed { index, key ->
                    // The same widths as the full-width bottom row in KeysPanel, split or not.
                    val width = when {
                        unit == null -> Modifier.weight(key.weight)
                        key is Key.Space -> Modifier.weight(1f)
                        else -> Modifier.width(unit * key.weight)
                    }
                    if (index == tab) ReturnTab(pressed, width) else KeyPeek(key, width)
                }
            }
        }
        // The sheet's edge casts a soft shadow over the keys beneath it.
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(6.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent))))
    }
}

private val peekShape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)

/** The top of a key under the sheet: decoration only; the strip around it is the button. */
@Composable
private fun KeyPeek(key: Key, modifier: Modifier) {
    val c = LocalKeyboardColors.current
    if (key is Key.Gap) {
        Spacer(modifier)
        return
    }
    val color = when (key) {
        is Key.Char, is Key.Space, is Key.Emoji -> c.key
        else -> c.keyMod
    }
    Box(modifier.fillMaxHeight().padding(start = 2.5.dp, end = 2.5.dp, top = 12.dp)
        .background(color.copy(alpha = color.alpha * 0.8f), peekShape))
}

/** The emoji key's slot risen above the sheet's edge as a lit keyboard tab, pointing down. */
@Composable
private fun ReturnTab(pressed: Boolean, modifier: Modifier) {
    val c = LocalKeyboardColors.current
    Box(modifier.fillMaxHeight().padding(start = 2.5.dp, end = 2.5.dp, top = 2.dp)
        .clip(peekShape)
        .background(if (pressed) c.keyPressed else c.keyOn)
        .drawBehind { drawRect(c.enter, size = Size(size.width, 2.dp.toPx())) },
        contentAlignment = Alignment.Center) {
        // Both glyphs leave empty space in their 24-unit boxes, so they overlap to fit the tab.
        Icon(SlimIcons.Keyboard, null, tint = c.keyText, modifier = Modifier.offset(y = (-4).dp).size(19.dp))
        Icon(SlimIcons.ChevronDown, null, tint = c.enter, modifier = Modifier.offset(y = 8.dp).size(16.dp))
    }
}
