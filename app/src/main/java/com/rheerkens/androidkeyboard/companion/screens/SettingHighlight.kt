package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.delay

/**
 * The setting a search result opened. The row with this title scrolls into view and flashes once,
 * then the target clears so it doesn't flash again when the page comes back.
 */
@Stable
class SettingHighlighter {
    var target by mutableStateOf<String?>(null)
        private set
    /** Counts requests, so a row that finishes late can't clear a newer one. */
    var request by mutableIntStateOf(0)
        private set
    /** Set when the row has composed, so a lazy list stops paging down to find it. */
    var found by mutableStateOf(false)
        internal set

    fun show(title: String?) {
        found = false
        target = title
        request++
    }

    fun clear() = show(null)

    /** Clears [forRequest] only if it is still the current one. */
    fun finish(forRequest: Int) {
        if (request == forRequest && target != null) clear()
    }
}

/** Absent outside the companion app (e.g. in the keyboard), where rows never flash. */
val LocalSettingHighlighter = compositionLocalOf<SettingHighlighter?> { null }

/** The modifier for a setting row titled [title]: scrolls it into view and flashes it when it's the target. */
@Composable
internal fun settingHighlight(title: String): Modifier {
    val highlighter = LocalSettingHighlighter.current
    val active = highlighter != null && highlighter.target == title
    val request = highlighter?.request ?: 0
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    val tint = MaterialTheme.colorScheme.primary
    LaunchedEffect(active, request) {
        if (!active || highlighter == null) {
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        highlighter.found = true
        try {
            withFrameNanos {}
            requester.bringIntoView()
            // Compose scales these with the system animator duration; with animations off, the row still shows a tint.
            flash.animateTo(1f, tween(200))
            delay(1400)
            flash.animateTo(0f, tween(700))
        } finally {
            // Also when the row scrolls out of composition mid-flash, so the request never lingers.
            highlighter.finish(request)
        }
    }
    return Modifier.bringIntoViewRequester(requester).drawBehind {
        if (flash.value > 0f) drawRect(tint.copy(alpha = .16f * flash.value))
    }
}

/**
 * Brings the highlighted row's lazy item into composition (rows below the fold aren't composed, so
 * they can't scroll themselves into view). [itemIndex] gives the item for a title when the page
 * knows it, e.g. below a long word list; otherwise the list pages down until the row appears.
 */
@Composable
internal fun RevealSettingHighlight(list: LazyListState, itemIndex: (String) -> Int? = { null }) {
    val highlighter = LocalSettingHighlighter.current ?: return
    val target = highlighter.target
    LaunchedEffect(target, highlighter.request) {
        if (target == null) return@LaunchedEffect
        withFrameNanos {}
        if (highlighter.found) return@LaunchedEffect
        itemIndex(target)?.let { index ->
            list.scrollToItem(index)
            return@LaunchedEffect
        }
        repeat(MAX_PAGES) {
            withFrameNanos {}
            withFrameNanos {}
            if (highlighter.found || !list.canScrollForward) return@LaunchedEffect
            list.scrollBy(list.layoutInfo.viewportSize.height * .8f)
        }
    }
}

/** Settings pages are a few screens long; long lists pass an [itemIndex] instead. */
private const val MAX_PAGES = 20
