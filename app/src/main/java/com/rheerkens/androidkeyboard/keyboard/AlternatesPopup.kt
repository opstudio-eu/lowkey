package com.rheerkens.androidkeyboard.keyboard

/**
 * Geometry of a long-press popup: a row of [count] cells above a key, in the key's pixel coordinates.
 *
 * Choice 0 sits over the key. The other choices extend right, or mirror to the left when the
 * row would leave the [windowWidth]. [keyLeft] is the key's left edge in the window.
 */
class AlternatesPopup(
    val count: Int,
    keyLeft: Float,
    keyWidth: Float,
    private val keyHeight: Float,
    windowWidth: Float,
    val cellWidth: Float,
    val cellHeight: Float,
    private val slop: Float,
) {
    val width = cellWidth * count

    /** True when choice 0 is the rightmost cell. */
    val mirrored: Boolean

    /** Popup left edge relative to the key's left edge. */
    val left: Float

    init {
        require(count > 0)
        val center = keyLeft + keyWidth / 2
        val first = center - cellWidth / 2
        mirrored = first + width > windowWidth && first + cellWidth - width >= 0f
        val start = if (mirrored) first + cellWidth - width else first
        left = start.coerceAtMost(windowWidth - width).coerceAtLeast(0f) - keyLeft
    }

    /** Popup top edge relative to the key's top edge. */
    val top: Float get() = -cellHeight

    /** Choice index shown in visual cell [cell], counted from the left. */
    fun choiceAt(cell: Int): Int = if (mirrored) count - 1 - cell else cell

    /**
     * Choice under a finger at ([x], [y]) in key coordinates, or null when the finger has left
     * the popup and key area, which cancels the long-press.
     */
    fun select(x: Float, y: Float): Int? {
        if (y < top - slop || y > keyHeight + slop) return null
        if (x < left - slop || x > left + width + slop) return null
        val cell = ((x - left) / cellWidth).toInt().coerceIn(0, count - 1)
        return choiceAt(cell)
    }
}
