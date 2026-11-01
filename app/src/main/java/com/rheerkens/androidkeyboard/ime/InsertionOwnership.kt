package com.rheerkens.androidkeyboard.ime

import kotlin.math.min

/**
 * Tracks the editor position a dictation session owns, without timing heuristics.
 *
 * - A session owns its start selection (the anchor) until it writes composing text; from then on
 *   it owns the composing region that starts at the anchor.
 * - After each of our own writes the expected cursor is known exactly, so selection updates that
 *   our writes cause are recognized, and anything else is a user move.
 *
 * Positions are UTF-16 offsets, as in [android.view.inputmethod.InputConnection]. Pure Kotlin.
 */
class InsertionOwnership {
    var anchorStart = -1
        private set
    var anchorEnd = -1
        private set
    var composing = false
        private set
    private var composeStart = -1
    private var composedLength = 0

    /** Cursor after our last write, until the editor reports it. */
    private var pendingCursor: Int? = null

    /** True when [pendingCursor] comes from a commit (a continuous restart may start there). */
    private var pendingFromCommit = false

    /**
     * A session starts at the current selection. Only right after our own commit, before the
     * editor has reported it (a continuous-mode restart), does it start at the committed cursor.
     */
    fun begin(selStart: Int, selEnd: Int) {
        val pending = pendingCursor.takeIf { pendingFromCommit }
        anchorStart = pending ?: selStart
        anchorEnd = pending ?: selEnd
        composing = false
        composedLength = 0
    }

    /** We set composing text of [length] chars (replacing the selection on the first write). */
    fun composed(length: Int) {
        if (!composing) {
            composeStart = min(anchorStart, anchorEnd)
            composing = true
        }
        composedLength = length
        pendingCursor = composeStart + length
        pendingFromCommit = false
    }

    /** We removed our composing text. */
    fun clearedComposing() {
        if (composing) {
            pendingCursor = composeStart
            pendingFromCommit = false
        }
        composing = false
        composedLength = 0
    }

    /** We committed [length] chars at the owned position. */
    fun committed(length: Int) {
        val start = if (composing) composeStart else min(anchorStart, anchorEnd)
        pendingCursor = start + length
        pendingFromCommit = true
        composing = false
        composedLength = 0
    }

    /** Where the cursor must be right now for the session to still own the insertion point. */
    fun expectedSelection(): Pair<Int, Int> =
        if (composing) (composeStart + composedLength).let { it to it } else anchorStart to anchorEnd

    /**
     * Checks a selection update during a session. True when it is the result of our own write,
     * the cursor sits at the end of our composing region, or (before any write) at the anchor.
     */
    fun owns(newStart: Int, newEnd: Int, candidatesStart: Int, candidatesEnd: Int): Boolean {
        if (newStart == newEnd && newStart == pendingCursor) {
            reset()
            return true
        }
        if (composing) {
            return candidatesStart == composeStart && newStart == candidatesEnd && newEnd == candidatesEnd
        }
        return anchorStart < 0 || (newStart == anchorStart && newEnd == anchorEnd)
    }

    /** A selection update outside any session (in any mode); our earlier writes are settled. */
    fun observedIdle() = reset()

    /** Forgets pending writes, for example when the editor changes. */
    fun reset() {
        pendingCursor = null
        pendingFromCommit = false
    }
}
