package com.rheerkens.androidkeyboard.ime

/** Tracks consecutive space-key presses, independently of editor callbacks. */
class DoubleSpacePeriod {
    private var lastSpaceAt: Long? = null
    private var expectedBefore: String? = null

    fun onSpace(before: String, now: Long): Boolean {
        val previous = lastSpaceAt
        val replace = previous != null && now - previous in 0..700 &&
            before == expectedBefore && before.endsWith(' ') && canEndSentence(before.dropLast(1))
        // A third space must stay a space; it must not create another sentence.
        lastSpaceAt = if (replace) null else now
        expectedBefore = if (replace) null else (before + " ").takeLast(CONTEXT_LIMIT)
        return replace
    }

    fun reset() { lastSpaceAt = null; expectedBefore = null }

    /** The first space may also have corrected a word, so remember the committed spelling. */
    fun spaceCommitted(before: String) {
        if (lastSpaceAt != null) expectedBefore = before.takeLast(CONTEXT_LIMIT)
    }

    private fun canEndSentence(text: String): Boolean {
        val content = text.trimEnd('"', '\'', '”', '’', ')', ']', '}')
        return content.lastOrNull()?.isLetterOrDigit() == true
    }

    companion object { const val CONTEXT_LIMIT = 64 }
}
