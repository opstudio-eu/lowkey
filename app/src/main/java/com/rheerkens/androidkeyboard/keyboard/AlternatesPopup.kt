package com.rheerkens.androidkeyboard.keyboard

/** Compact long-press grid, expressed in coordinates relative to the pressed key. */
class AlternatesPopup(
    val count: Int,
    keyLeft: Float,
    keyWidth: Float,
    private val keyHeight: Float,
    windowWidth: Float,
    cellWidth: Float,
    val cellHeight: Float,
    private val slop: Float,
) {
    init {
        require(count > 0 && windowWidth > 0f && cellWidth > 0f && cellHeight > 0f)
    }

    val cellWidth = cellWidth.coerceAtMost(windowWidth)
    private val maxColumns = minOf(5, (windowWidth / this.cellWidth).toInt().coerceAtLeast(1))
    val rows = (count + maxColumns - 1) / maxColumns
    val columns = (count + rows - 1) / rows
    val width = this.cellWidth * columns
    val height = cellHeight * rows

    /** One-row choices stay above the key; a larger grid's bottom row covers the key. */
    val top = -cellHeight * maxOf(1, rows - 1)
    val left: Float
    private val anchorColumn: Int

    init {
        val center = keyLeft + keyWidth / 2
        val preferred = center - ((columns - 1) / 2 + .5f) * this.cellWidth
        val start = preferred.coerceIn(0f, (windowWidth - width).coerceAtLeast(0f))
        left = start - keyLeft
        anchorColumn = ((center - start) / this.cellWidth).toInt().coerceIn(0, columns - 1)
    }

    /** Fill the bottom row first, starting over the key; empty cells are not selectable. */
    fun choiceAt(column: Int, row: Int): Int? {
        if (column !in 0 until columns || row !in 0 until rows) return null
        val choice = (rows - 1 - row) * columns + (column - anchorColumn + columns) % columns
        return choice.takeIf { it < count }
    }

    /**
     * Choice under a finger at ([x], [y]) in key coordinates, or null when the finger has left
     * the popup and key area, which cancels the long-press.
     */
    fun select(x: Float, y: Float): Int? {
        if (y < top - slop || y > maxOf(keyHeight, top + height) + slop) return null
        if (x < left - slop || x > left + width + slop) return null
        val column = ((x - left) / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = ((y - top) / cellHeight).toInt().coerceIn(0, rows - 1)
        return choiceAt(column, row)
    }
}
