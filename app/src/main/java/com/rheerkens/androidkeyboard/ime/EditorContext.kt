package com.rheerkens.androidkeyboard.ime

/** One bounded editor read, shared by all suggestion-context checks. */
data class EditorContext(val before: String) {
    val word: String get() = before.takeLast(48).takeLastWhile(EditorBridge::isWordChar)
    val token: String get() = before.takeLast(96).takeLastWhile { !it.isWhitespace() }
    val wordStartsSentence: Boolean get() {
        val raw = before.takeLast(word.length + 64).dropLast(word.length)
        val trimmed = raw.trimEnd(' ', '\t', '"', '\'', '(')
        return if (trimmed.isEmpty()) raw.length < 64 else trimmed.last() in ".!?\n"
    }

    companion object {
        // Longest context: a 48-character word preceded by 64 characters.
        const val LIMIT = 112
    }
}
