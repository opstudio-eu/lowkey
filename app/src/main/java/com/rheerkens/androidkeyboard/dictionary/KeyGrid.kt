package com.rheerkens.androidkeyboard.dictionary

import kotlin.math.abs

/**
 * Which letters are neighbours on a keyboard of three letter [rows] (top to bottom), so a slip
 * onto the next key costs less in [Corrector]. Keys are a unit wide; shorter top and middle rows
 * are centred like the on-screen keys, and the third row starts a key in (after Shift) when it
 * has seven letters, half a key further per missing letter. Only `a`–`z` take part.
 */
class KeyGrid(rows: List<String>) {
    // Edit distance visits this relation for every candidate character pair. Precompute it
    // once to avoid boxed Char/hash-map/Pair lookups in the hot loop.
    private val adjacentKeys = BooleanArray(26 * 26)

    init {
        val x = DoubleArray(26) { Double.NaN }
        val y = DoubleArray(26)
        rows.forEachIndexed { r, row ->
            val offset = if (r < 2) (FULL_ROW - row.length) / 2.0 else maxOf(0.0, 1.0 + (SHORT_ROW - row.length) / 2.0)
            row.forEachIndexed { c, ch ->
                val i = ch - 'a'
                if (i in 0..25) {
                    x[i] = c + offset
                    y[i] = r.toDouble()
                }
            }
        }
        for (a in 0..25) for (b in 0..25) {
            adjacentKeys[a * 26 + b] = !x[a].isNaN() && !x[b].isNaN() &&
                abs(x[a] - x[b]) <= 1.0 && abs(y[a] - y[b]) <= 1.0
        }
    }

    fun adjacent(a: Char, b: Char): Boolean {
        val x = a - 'a'
        val y = b - 'a'
        return x in 0..25 && y in 0..25 && adjacentKeys[x * 26 + y]
    }

    companion object {
        private const val FULL_ROW = 10
        private const val SHORT_ROW = 7

        val QWERTY = KeyGrid(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"))
    }
}
