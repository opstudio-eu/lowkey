package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.abs

/**
 * One word (or the part of it on one line) in the editor, with its on-screen bounds in pixels.
 * [start] is the absolute text offset where the word begins, also for a part wrapped to the next line.
 */
data class WordBox(
    val start: Int,
    val end: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** [pickCut]'s answer when the finger is past the last word: nothing is deleted. */
const val CUT_NOTHING = Int.MAX_VALUE

/**
 * Where a Backspace slide into the editor starts deleting, for a finger at ([x], [y]): the start of
 * the word under it, or of the closest word on that line. Past the end of a line (by more than
 * [slop]) it is the next line's first word, so the whole line stays; past the last word it is
 * [CUT_NOTHING]. Null when the finger is not within [slop] of any line.
 */
fun pickCut(words: List<WordBox>, x: Float, y: Float, slop: Float): Int? {
    var line: WordBox? = null
    var bestDy = Float.MAX_VALUE
    for (w in words) {
        val dy = distance(y, w.top, w.bottom)
        if (dy <= slop && dy < bestDy) {
            line = w
            bestDy = dy
        }
    }
    val row = line ?: return null
    var best: WordBox? = null
    var bestDx = Float.MAX_VALUE
    var last: WordBox? = null
    for (w in words) {
        if (abs(w.top - row.top) > 1f || abs(w.bottom - row.bottom) > 1f) continue
        if (last == null || w.right > last.right) last = w
        val dx = distance(x, w.left, w.right)
        if (dx < bestDx) {
            best = w
            bestDx = dx
        }
    }
    val end = last ?: return null
    if (x > end.right + slop) return words.filter { it.start >= end.end }.minOfOrNull { it.start } ?: CUT_NOTHING
    return best?.start
}

/**
 * How many characters of the text before the cursor stay for a [cut] (absolute offset), where the
 * slider's snapshot holds the [beforeLength] characters before the absolute [cursor].
 */
fun keptForCut(cut: Int, cursor: Int, beforeLength: Int): Int {
    val snapshotStart = cursor - beforeLength
    return when {
        cut >= cursor -> beforeLength
        cut <= snapshotStart -> 0
        else -> cut - snapshotStart
    }
}

/** Words (runs of non-space) in [text] from [from] on. */
fun wordsFrom(text: String, from: Int): Int {
    var n = 0
    var inWord = false
    for (i in from.coerceAtLeast(0) until text.length) {
        val space = text[i].isWhitespace()
        if (!space && !inWord) n++
        inWord = !space
    }
    return n
}

private fun distance(v: Float, lo: Float, hi: Float): Float = when {
    v < lo -> lo - v
    v > hi -> v - hi
    else -> 0f
}
