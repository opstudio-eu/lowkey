package com.rheerkens.androidkeyboard.keyboard

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

    /** Rows of the menu's grid, as [AlternatesPopup] lays out [count] cells (at most five per row). */
    fun rows(count: Int, windowWidth: Float, cellWidth: Float): Int {
        val columns = minOf(5, (windowWidth / cellWidth.coerceAtMost(windowWidth)).toInt().coerceAtLeast(1))
        return (count + columns - 1) / columns
    }

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
