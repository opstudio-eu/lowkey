package com.rheerkens.androidkeyboard.keyboard

/**
 * Hold-and-slide Backspace: the text around the cursor when the gesture began, and the word steps
 * the slider moves through. Level 0 keeps everything, each level removes one more word before the
 * cursor, and the top level removes all text before the cursor. Text after the cursor always stays,
 * so with the cursor at the end the top level clears the field. [truncatedBefore] text reaches
 * past the snapshot's start, so its top level deletes text the slider can't put back and is final;
 * [truncatedAfter] text only reaches past its end. [SlideScale] places the levels on the slider.
 */
class BackspaceScrub(
    val before: String,
    val after: String,
    val truncatedBefore: Boolean = false,
    val truncatedAfter: Boolean = false,
) {
    /** How much of [before] each level keeps. */
    private val cuts: List<Int> = buildList {
        var p = before.length
        add(p)
        while (p > 0) {
            while (p > 0 && before[p - 1].isWhitespace()) p--
            while (p > 0 && !before[p - 1].isWhitespace()) p--
            add(p)
        }
    }

    /** The level that deletes everything before the cursor; 0 when there is nothing before it. */
    val top: Int get() = cuts.lastIndex

    fun kept(level: Int): Int = cuts[level.coerceIn(0, top)]

    /** The top level deletes text past the snapshot, so reaching it can't be undone. */
    val topIsFinal: Boolean get() = truncatedBefore

    /** The top level empties the field: nothing follows the cursor. */
    val topClearsAll: Boolean get() = after.isEmpty() && !truncatedAfter
}
