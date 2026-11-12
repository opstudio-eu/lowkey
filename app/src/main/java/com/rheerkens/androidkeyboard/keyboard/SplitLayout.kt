package com.rheerkens.androidkeyboard.keyboard

data class SplitRow(val left: List<Key>, val right: List<Key>, val fullWidth: Boolean = false)

/** Keep a stable typing boundary instead of bisecting the weighted row (including modifiers). */
fun splitRows(layout: KeyboardLayout, keyboard: TypingKeyboard, layer: Layer): List<SplitRow> =
    layout.rows.mapIndexed { index, row ->
        if (row.any { it is Key.Space }) {
            SplitRow(row, emptyList(), fullWidth = true)
        } else {
            val chars = row.filterIsInstance<Key.Char>()
            val letterIndex = index - if (layer == Layer.LETTERS) layout.rows.size - 4 else 0
            val count = if (layer != Layer.LETTERS || letterIndex < 0) (chars.size + 1) / 2
                else if (letterIndex == 2) 4 else 5
            val left = mutableListOf<Key>()
            val right = mutableListOf<Key>()
            var seen = 0
            for (key in row) when (key) {
                is Key.Gap -> Unit
                is Key.Char -> { (if (seen++ < count) left else right).add(key) }
                is Key.Backspace, is Key.Enter -> right.add(key)
                else -> left.add(key)
            }
            // The shared inner key is reachable with either thumb (G/V on QWERTY).
            // Symbols retain a single copy of every character.
            if (layer == Layer.LETTERS && letterIndex in 1..2) {
                left.lastOrNull { it is Key.Char }?.let { right.add(0, it) }
            }
            SplitRow(left, right)
        }
    }
