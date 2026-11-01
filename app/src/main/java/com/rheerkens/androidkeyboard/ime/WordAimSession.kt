package com.rheerkens.androidkeyboard.ime

import android.graphics.Matrix
import android.graphics.RectF
import android.text.SegmentFinder
import android.view.inputmethod.InputConnection
import android.view.inputmethod.TextBoundsInfo
import android.view.inputmethod.TextBoundsInfoResult
import androidx.annotation.RequiresApi
import com.rheerkens.androidkeyboard.keyboard.WordBox
import com.rheerkens.androidkeyboard.keyboard.keptForCut
import com.rheerkens.androidkeyboard.keyboard.pickCut
import java.util.concurrent.Executor

/**
 * Where the editor's words are on screen (Android 14+), read once when a Backspace slide starts,
 * before it deletes anything ([InputConnection.requestTextBoundsInfo]). Sliding on into the editor
 * then points into that original layout, so the finger can also go back to where deleted words
 * were. Editors that can't report text bounds (Compose fields, Chrome) are [unsupported].
 */
@RequiresApi(34)
internal class WordAimSession(
    ic: InputConnection,
    executor: Executor,
    /** The screen area to ask about: everything above the keyboard. */
    area: RectF,
    /** How far from a line or word the finger may be and still count, in pixels. */
    private val slopPx: Float,
    /** The words arrived, or the editor said it can't report them. */
    private val onReady: (WordAimSession) -> Unit,
    /** The text differs from when the slide started (the slider has deleted something). */
    private val textChanged: () -> Boolean,
) {
    /** The cursor's absolute offset when the slide started, or -1 if the editor won't say. */
    private val cursor: Int = runCatching {
        ic.getSurroundingText(0, 0, 0)?.takeIf { it.offset >= 0 && it.selectionStart >= 0 }
            ?.let { it.offset + it.selectionStart }
    }.getOrNull() ?: -1

    private var words: List<WordBox>? = null
    private var ended = false
    var unsupported = cursor < 0
        private set
    val ready: Boolean get() = words != null

    init {
        if (!unsupported) {
            ic.requestTextBoundsInfo(area, executor) { result ->
                if (ended) return@requestTextBoundsInfo
                val info = result.textBoundsInfo
                // In our own process (the Try it page) the binder call isn't parcelled: the word
                // finder reads the live text, which the slider may already have shortened. It can
                // then throw or quietly miss words, so only trust it while the text is unchanged.
                val found = if (result.resultCode == TextBoundsInfoResult.CODE_SUCCESS && info != null && !textChanged()) {
                    try { wordsOnScreen(info) } catch (_: RuntimeException) { null }
                } else {
                    null
                }
                if (found != null) {
                    words = found
                } else {
                    unsupported = true
                }
                onReady(this)
            }
        }
    }

    /**
     * How much of the slider's text before the cursor ([beforeLength] characters) stays for a
     * finger at ([x], [y]) on screen; null while not over any line of text.
     */
    fun keptAt(x: Float, y: Float, beforeLength: Int): Int? {
        val cut = pickCut(words ?: return null, x, y, slopPx) ?: return null
        return keptForCut(cut, cursor, beforeLength)
    }

    fun end() {
        ended = true
    }
}

/** Word boxes in screen pixels, one per word and line (a hyphenated word can wrap). */
@RequiresApi(34)
private fun wordsOnScreen(info: TextBoundsInfo): List<WordBox> {
    val start = info.startIndex
    val end = info.endIndex
    if (start >= end) return emptyList()
    val matrix = Matrix().also(info::getMatrix)
    val finder = info.wordSegmentFinder
    val out = ArrayList<WordBox>()
    val char = RectF()
    val box = RectF()
    var s = finder.previousStartBoundary(start + 1).takeIf { it != SegmentFinder.DONE && it >= start }
        ?: finder.nextStartBoundary(start)
    while (s != SegmentFinder.DONE && s < end) {
        val e = minOf(finder.nextEndBoundary(s).takeIf { it != SegmentFinder.DONE } ?: end, end)
        var started = false
        for (i in s until e) {
            if (info.getCharacterFlags(i) and TextBoundsInfo.FLAG_CHARACTER_WHITESPACE != 0) continue
            info.getCharacterBounds(i, char)
            if (started && char.top != box.top) {
                out += screenBox(s, e, box, matrix)
                started = false
            }
            if (!started) {
                started = true
                box.set(char)
            } else {
                box.union(char)
            }
        }
        if (started) out += screenBox(s, e, box, matrix)
        s = finder.nextStartBoundary(s)
    }
    return out
}

private fun screenBox(start: Int, end: Int, local: RectF, matrix: Matrix): WordBox {
    val r = RectF(local)
    matrix.mapRect(r)
    return WordBox(start, end, r.left, r.top, r.right, r.bottom)
}
