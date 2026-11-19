package com.rheerkens.androidkeyboard.keyboard

/**
 * Something done on the keyboard that the gesture tour in the app waits for. It says what
 * happened, never what was typed.
 */
sealed interface KeyboardGesture {
    /** A slide from the pointing stick (the red dot by G) moved the cursor, or changed the selection when [selecting]. */
    data class CursorMoved(val chars: Int, val lines: Int, val selecting: Boolean) : KeyboardGesture

    /** A hold-and-slide on Backspace ended with [words] words deleted. */
    data class WordsDeleted(val words: Int) : KeyboardGesture
}
