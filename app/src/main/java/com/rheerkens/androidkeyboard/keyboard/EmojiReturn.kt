package com.rheerkens.androidkeyboard.keyboard

/**
 * Where the emoji panel's way back to letters sits in [bottomRow]: on the key that opened the
 * panel. That is the emoji key, or the comma that opens emoji on hold when the language-switch
 * key takes the emoji key's place. A row with neither falls back to Space, then the first key.
 */
fun emojiReturnIndex(bottomRow: List<Key>): Int =
    bottomRow.indexOfFirst { it is Key.Emoji }.takeIf { it >= 0 }
        ?: bottomRow.indexOfFirst { it is Key.Char && it.opensEmojiOnHold }.takeIf { it >= 0 }
        ?: bottomRow.indexOfFirst { it is Key.Space }.takeIf { it >= 0 }
        ?: 0
