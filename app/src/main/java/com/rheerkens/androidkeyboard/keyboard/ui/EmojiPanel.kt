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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.keyboard.EmojiCatalog
import com.rheerkens.androidkeyboard.keyboard.EmojiItem
import com.rheerkens.androidkeyboard.ui.SlimIcons

private const val RECENT = "Recent"
private val categoryIcons = mapOf(
    "Smileys & Emotion" to "😀", "People & Body" to "👋", "Animals & Nature" to "🐾",
    "Food & Drink" to "🍕", "Travel & Places" to "🚗", "Activities" to "⚽",
    "Objects" to "💡", "Symbols" to "🔣", "Flags" to "🏳️", RECENT to "◷",
)

/** Uses the active keyboard's frame; no separate popup can escape a narrow or floating panel. */
@Composable
internal fun EmojiPanel(catalog: EmojiCatalog?, recent: List<String>, height: Dp, actions: KeyboardActions) {
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
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            PressTarget("Return to letters", Modifier.width(48.dp).fillMaxHeight(), onTap = actions::onCloseEmoji) {
                Text("ABC", color = c.keyText, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            }
            if (variants != null) {
                PressTarget("Back to emoji categories", Modifier.width(40.dp).fillMaxHeight(), onTap = { variants = null }) {
                    Text("‹", color = c.keyText, fontSize = 30.sp, modifier = Modifier.align(Alignment.Center))
                }
                Text("Skin tones", color = c.keyText, fontSize = 13.sp, modifier = Modifier.weight(1f))
            } else {
                LazyRow(Modifier.weight(1f)) {
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
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (catalog == null) "Loading emoji…" else if (category == RECENT) "No recent emoji yet" else "No emoji available",
                    color = c.muted, fontSize = 13.sp)
            }
        } else {
            key(category, variants?.value) {
                LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp)) {
                    items(visible, key = { it.value }) { item ->
                        val hasVariants = variants == null && item.variants.size > 1
                        PressTarget(item.name + if (hasVariants) ". Hold for skin tones" else "",
                            Modifier.fillMaxWidth().height(48.dp), gestureKey = item.value,
                            onDown = actions::onKeyDown,
                            onTap = { actions.onChooseEmoji(item.value); if (variants != null) variants = null },
                            onLongPress = if (hasVariants) ({ variants = item }) else null) { pressed ->
                            Box(Modifier.fillMaxSize().padding(2.dp)
                                .background(if (pressed) c.keyPressed else c.key, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center) {
                                Text(item.value, fontSize = 27.sp, maxLines = 1, overflow = TextOverflow.Clip)
                            }
                        }
                    }
                }
            }
        }
    }
}
