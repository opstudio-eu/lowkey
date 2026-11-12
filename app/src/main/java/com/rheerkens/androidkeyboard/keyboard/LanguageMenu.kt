package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.hypot

/**
 * The emoji key's hold menu of keyboard languages. No Android dependencies; the popup geometry is
 * [AlternatesPopup]'s, so it opens and slides like the accent popup.
 */
object LanguageMenu {
    /**
     * The menu's cells: the [current] keyboard first, so it sits over the key like an accent popup's
     * first choice, then the other [keyboards] in globe order. One cell to the right is the keyboard
     * the globe key would switch to next.
     */
    fun choices(keyboards: List<TypingKeyboard>, current: TypingKeyboard): List<TypingKeyboard> {
        val i = keyboards.indexOfFirst { it.id == current.id }
        if (i < 0) return listOf(current) + keyboards.filter { it.id != current.id }
        return keyboards.drop(i) + keyboards.take(i)
    }

    /** Columns of the menu's grid, as [AlternatesPopup] lays out its cells (at most five per row). */
    fun columns(windowWidth: Float, cellWidth: Float): Int =
        minOf(5, (windowWidth / cellWidth.coerceAtMost(windowWidth)).toInt().coerceAtLeast(1))

    /** Rows of the menu's grid for [count] cells. */
    fun rows(count: Int, windowWidth: Float, cellWidth: Float): Int {
        val columns = columns(windowWidth, cellWidth)
        return (count + columns - 1) / columns
    }

    /**
     * How many cells fit while staying readable: the bottom row covers the key and every row above
     * it needs [minCell] of the [keyTop] space between the keyboard's top and the key.
     */
    fun capacity(windowWidth: Float, cellWidth: Float, keyTop: Float, minCell: Float): Int =
        columns(windowWidth, cellWidth) * (1 + (keyTop / minCell).toInt().coerceAtLeast(0))

    /**
     * The keyboards to show, and whether a last "More" cell (which opens Settings) replaces the
     * ones that don't fit in [capacity] cells. The current keyboard always stays, so the menu has
     * at least two cells (the current keyboard and More) however small [capacity] is.
     */
    fun fit(choices: List<TypingKeyboard>, capacity: Int): Pair<List<TypingKeyboard>, Boolean> {
        val cells = maxOf(2, capacity)
        return if (choices.size <= cells) choices to false else choices.take(cells - 1) to true
    }

    /**
     * Whether a held finger has really slid: more than [slop] from where it was when the menu
     * opened. Until then the menu keeps the current keyboard selected, even where the key's edge
     * lies under a neighbouring cell (the popup is clamped to the keyboard's sides).
     */
    fun hasMoved(fromX: Float, fromY: Float, x: Float, y: Float, slop: Float): Boolean =
        hypot(x - fromX, y - fromY) > slop

    /** What letting go of the held menu does. */
    enum class Release {
        /** The finger never left the current keyboard's cell: keep the menu open to tap a choice. */
        KEEP_OPEN,
        /** Slid away from the menu, or back to the current keyboard: nothing changes. */
        CLOSE,
        /** Switch to the selected keyboard. */
        CHOOSE,
    }

    /** [selected] is the cell under the finger (null off the menu); [moved] says whether it ever left cell 0. */
    fun release(selected: Int?, moved: Boolean): Release = when {
        selected == null -> Release.CLOSE
        selected == 0 -> if (moved) Release.CLOSE else Release.KEEP_OPEN
        else -> Release.CHOOSE
    }

    /** The cell's large text: the short language code, `EN`. */
    fun code(keyboard: TypingKeyboard): String = keyboard.language.shortLabel

    /** The cell's small text: the language's own name, or the arrangement for an alternative layout. */
    fun caption(keyboard: TypingKeyboard): String = keyboard.arrangement?.label ?: keyboard.language.name
}
