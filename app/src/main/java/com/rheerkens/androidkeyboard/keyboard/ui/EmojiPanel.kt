package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch

private const val RECENT = "Recent"
private val categoryIcons = mapOf(
    "Smileys & Emotion" to "😀", "People & Body" to "👋", "Animals & Nature" to "🐾",
    "Food & Drink" to "🍕", "Travel & Places" to "🚗", "Activities" to "⚽",
    "Objects" to "💡", "Symbols" to "🔣", "Flags" to "🏳️", RECENT to "◷",
)

private val EMOJI_CELL = 44.dp
private val EMOJI_HEADER = 48.dp
private val SECTION_HEADER = 24.dp

/**
 * The bottom key row the emoji panel replaces: its keys, the height of one row, and when the
 * keyboard is split, the key-weight units of one half ([splitUnits]) and the gap between the halves.
 */
internal data class PeekRow(
    val keys: List<Key>,
    val rowHeight: Dp,
    val splitUnits: Float? = null,
    val splitGapDp: Float = 0f,
    /** Emoji were opened by holding a key (the comma), so the way back sits there. */
    val fromHold: Boolean = false,
)

/** One category in the emoji list, and where its header sits among the grid's items. */
private data class EmojiSection(val name: String, val items: List<EmojiItem>, val start: Int)

/**
 * Uses the active keyboard's frame; no separate popup can escape a narrow or floating panel.
 * All emoji scroll in one grid, Recent first; a category icon jumps to its section. A keyboard key
 * sits exactly where the key that opened emoji was, so the same spot leads back to letters. The
 * grid runs on behind the rest of that row, so the key costs no emoji rows.
 */
@Composable
internal fun EmojiPanel(catalog: EmojiCatalog?, recent: List<String>, height: Dp, peek: PeekRow, actions: KeyboardActions) {
    val c = LocalKeyboardColors.current
    var variants by remember(catalog) { mutableStateOf<EmojiItem?>(null) }
    val sections = remember(catalog, recent) {
        if (catalog == null) return@remember emptyList()
        val groups = listOf(RECENT to recent.mapNotNull(catalog::find)) +
            catalog.categories.map { name -> name to catalog.entries.filter { it.category == name } }
        var start = 0
        groups.filter { it.second.isNotEmpty() }.map { (name, items) ->
            EmojiSection(name, items, start).also { start += 1 + items.size }
        }
    }
    val grid = rememberLazyGridState()
    val tabs = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The category whose section holds the first visible row.
    val current by remember(sections) {
        derivedStateOf { sections.lastOrNull { it.start <= grid.firstVisibleItemIndex }?.name }
    }
    LaunchedEffect(current, sections) {
        val index = sections.indexOfFirst { it.name == current }
        if (index >= 0) tabs.animateScrollToItem(maxOf(index - 2, 0))
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
                LazyRow(Modifier.weight(1f), state = tabs, contentPadding = PaddingValues(start = 4.dp)) {
                    items(sections, key = { it.name }) { section ->
                        val selected = current == section.name
                        PressTarget(section.name, Modifier.size(48.dp).semantics {
                            stateDescription = if (selected) "Selected" else "Not selected"
                        }, onTap = { scope.launch { grid.scrollToItem(section.start) } }) { pressed ->
                            Box(Modifier.fillMaxSize().padding(3.dp)
                                .background(if (pressed || selected) c.keyPressed else Color.Transparent, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center) {
                                Text(categoryIcons[section.name] ?: section.name.take(1), fontSize = 24.sp, color = c.keyText)
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
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // The last row can scroll up clear of the keyboard key.
            val padding = PaddingValues(start = 4.dp, end = 4.dp, bottom = peek.rowHeight + 4.dp)
            val shown = variants
            if (catalog == null || sections.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = peek.rowHeight), contentAlignment = Alignment.Center) {
                    Text(if (catalog == null) "Loading emoji…" else "No emoji available", color = c.muted, fontSize = 13.sp)
                }
            } else if (shown != null) {
                LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                    items(shown.variants.mapNotNull { catalog.find(it.value) }, key = { it.value }) { item ->
                        EmojiCell(item, hasVariants = false, actions, onChosen = { variants = null }, onVariants = {})
                    }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), state = grid, modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                    sections.forEach { section ->
                        item(key = "header/${section.name}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                            Text(section.name, color = c.muted, fontSize = 12.sp, maxLines = 1,
                                modifier = Modifier.fillMaxWidth().height(SECTION_HEADER).wrapContentHeight(Alignment.Bottom)
                                    .padding(start = 6.dp, bottom = 4.dp))
                        }
                        // Recent repeats emoji from other sections, so keys carry the section too.
                        items(section.items, key = { "${section.name}/${it.value}" }, contentType = { "emoji" }) { item ->
                            EmojiCell(item, hasVariants = item.variants.size > 1, actions, onChosen = {}, onVariants = { variants = item })
                        }
                    }
                }
            }
            ReturnRow(peek, actions::onCloseEmoji, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun EmojiCell(item: EmojiItem, hasVariants: Boolean, actions: KeyboardActions,
                      onChosen: () -> Unit, onVariants: () -> Unit) {
    val c = LocalKeyboardColors.current
    PressTarget(item.name + if (hasVariants) ". Hold for skin tones" else "",
        Modifier.fillMaxWidth().height(EMOJI_CELL), gestureKey = item.value,
        onDown = actions::onKeyDown,
        onTap = { actions.onChooseEmoji(item.value); onChosen() },
        onLongPress = if (hasVariants) onVariants else null) { pressed ->
        Box(Modifier.fillMaxSize().padding(2.dp)
            .background(if (pressed) c.keyPressed else c.key, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            Text(item.value, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

/**
 * The bottom row as [KeysPanel] lays it out, but empty except for one keyboard key where the key
 * that opened emoji sits, so the way back is under the same finger. The empty slots take no
 * touches, so the emoji behind them stay tappable and scrollable.
 */
@Composable
private fun ReturnRow(peek: PeekRow, onClose: () -> Unit, modifier: Modifier) {
    val c = LocalKeyboardColors.current
    val tab = emojiReturnIndex(peek.keys, peek.fromHold)
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, bottom = 4.dp).height(peek.rowHeight)) {
        val unit = peek.splitUnits?.let { ((maxWidth - peek.splitGapDp.dp) / (2 * it)).coerceAtLeast(1.dp) }
        Row(Modifier.fillMaxSize()) {
            peek.keys.forEachIndexed { index, key ->
                // The same widths as the full-width bottom row in KeysPanel, split or not.
                val width = when {
                    unit == null -> Modifier.weight(key.weight)
                    key is Key.Space -> Modifier.weight(1f)
                    else -> Modifier.width(unit * key.weight)
                }
                if (index == tab) {
                    PressTarget("Back to letters", width.fillMaxHeight(), onTap = onClose) { pressed ->
                        KeyCap(if (pressed) c.keyPressed else c.keyOn) {
                            Icon(SlimIcons.Keyboard, null, tint = c.keyText, modifier = Modifier.size(21.dp))
                        }
                    }
                } else {
                    Spacer(width)
                }
            }
        }
    }
}
