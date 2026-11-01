package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Shows [content] above its anchor in a separate window, so it can extend past the top of the
 * keyboard (the slim bar has no room above its keys). Not focusable; it is shown only while a finger
 * is down on the keyboard, and that gesture stays with the keyboard window until release.
 * [endInset] right-aligns the popup that far inside the anchor's end; null centers it.
 */
@Composable
fun AbovePopup(gap: Dp = 6.dp, endInset: Dp? = null, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val provider = remember(gap, endInset, density) {
        with(density) { AboveAnchor(gap.roundToPx(), endInset?.roundToPx(), 8.dp.roundToPx()) }
    }
    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
        content = content,
    )
}

private class AboveAnchor(private val gap: Int, private val endInset: Int?, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferred = if (endInset != null) anchorBounds.right - endInset - popupContentSize.width
            else anchorBounds.center.x - popupContentSize.width / 2
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        return IntOffset(preferred.coerceIn(margin, maxX), anchorBounds.top - popupContentSize.height - gap)
    }
}
