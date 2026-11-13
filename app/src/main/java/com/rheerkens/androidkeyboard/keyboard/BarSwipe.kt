package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.abs

enum class BarSwipe { PENDING, IGNORE, TOGGLE }

/** One direction changes keyboard size; horizontal motion belongs to clipboard scrolling. */
fun barSwipe(dx: Float, dy: Float, minimized: Boolean, threshold: Float, held: Boolean = false,
    horizontalSlop: Float = threshold): BarSwipe = when {
    held -> BarSwipe.IGNORE
    abs(dx) >= horizontalSlop -> BarSwipe.IGNORE
    abs(dx) < threshold && abs(dy) < threshold -> BarSwipe.PENDING
    abs(dy) > abs(dx) && (if (minimized) dy < 0 else dy > 0) -> BarSwipe.TOGGLE
    else -> BarSwipe.IGNORE
}
