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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.EmojiCatalog
import com.rheerkens.androidkeyboard.keyboard.EmojiItem
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

/** One category in the emoji list, and where its header sits among the grid's items. */
private data class EmojiSection(val name: String, val items: List<EmojiItem>, val start: Int)

/**
 * Uses the active keyboard's frame; no separate popup can escape a narrow or floating panel.
 * All emoji scroll in one grid, Recent first; a category icon jumps to its section. A tab joined to
 * the panel's bottom-left corner leads back to letters, wherever the keyboard is placed. The grid
 * runs on behind it, so the tab costs no emoji rows. [below] is the keyboard under the panel that
 * the tab reaches down over (a floating keyboard's drag handle).
 */
@Composable
internal fun EmojiPanel(catalog: EmojiCatalog?, recent: List<String>, height: Dp, below: Dp, actions: KeyboardActions) {
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
                                Text(categoryIcons[section.name] ?: section.name.take(1), fontSize = emojiSize(24.dp), color = c.keyText)
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
            // The tab's touch area is at least 48 dp tall; what of it lies above the panel's bottom
            // edge is kept clear, so the last row scrolls fully above the tab and its touch area.
            val touchHeight = maxOf(MIN_TOUCH, LETTERS_TAB_HEIGHT + below)
            val padding = PaddingValues(start = 4.dp, end = 4.dp, bottom = touchHeight - below)
            val shown = variants
            if (catalog == null || sections.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = touchHeight - below), contentAlignment = Alignment.Center) {
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
                                modifier = Modifier.fillMaxWidth().heightIn(min = SECTION_HEADER).wrapContentHeight(Alignment.Bottom)
                                    .padding(start = 6.dp, bottom = 4.dp))
                        }
                        // Recent repeats emoji from other sections, so keys carry the section too.
                        items(section.items, key = { "${section.name}/${it.value}" }, contentType = { "emoji" }) { item ->
                            EmojiCell(item, hasVariants = item.variants.size > 1, actions, onChosen = {}, onVariants = { variants = item })
                        }
                    }
                }
            }
            // Unbounded, so the reach over a floating keyboard's handle never squeezes the label:
            // the tab keeps its full height above the panel's bottom edge and hangs down from there.
            LettersTab(below, actions::onCloseEmoji, Modifier.align(Alignment.BottomStart).offset(y = below)
                .wrapContentHeight(Alignment.Bottom, unbounded = true).height(touchHeight))
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
            Text(item.value, fontSize = emojiSize(26.dp), maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

/** Emoji are pictures in fixed cells: they follow screen density, not the system text size. */
@Composable
private fun emojiSize(size: Dp) = with(LocalDensity.current) { size.toSp() }

private val LETTERS_TAB_HEIGHT = 32.dp
private val MIN_TOUCH = 48.dp
private val lettersTabShape = RoundedCornerShape(topEnd = 10.dp)

/**
 * The way back to letters: a small tab joined to the keyboard's left and bottom edges, a little
 * wider when large text needs it. On a floating keyboard it reaches down over the drag handle ([below]) to
 * the edge. Its touch area, the height [modifier] gives it, may reach a little above the visible
 * tab so it is at least 48 dp tall. Only that area takes touches; emoji beside it stay usable.
 */
@Composable
private fun LettersTab(below: Dp, onClose: () -> Unit, modifier: Modifier) {
    val c = LocalKeyboardColors.current
    PressTarget("Back to letters", modifier, onTap = onClose) { pressed ->
        Row(Modifier.align(Alignment.BottomStart).height(LETTERS_TAB_HEIGHT + below).widthIn(min = 56.dp)
            .shadow(2.dp, lettersTabShape, clip = false)
            .background(if (pressed) c.keyPressed else c.keyOn, lettersTabShape)
            .padding(start = 8.dp, end = 8.dp, bottom = below),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(SlimIcons.Keyboard, null, tint = c.keyText, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
            // The label follows the system text size up to 1.3x, so it always fits the small tab.
            val scale = LocalDensity.current.fontScale
            Text("ABC", color = c.keyText, fontSize = (12f * minOf(scale, 1.3f) / scale).sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
