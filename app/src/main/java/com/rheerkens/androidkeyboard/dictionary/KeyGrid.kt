package com.rheerkens.androidkeyboard.dictionary

import kotlin.math.abs

/**
 * Which letters are neighbours on a keyboard, so a slip onto the next key costs less in
 * [Corrector]. [rows] gives each letter row's keys (top to bottom) with the x of their centres,
 * in key widths. Two letters are neighbours when they're at most a key width apart horizontally,
 * in the same or adjacent rows. Only `a`–`z` take part.
 */
class KeyGrid(rows: List<List<Pair<Char, Double>>>) {
    // Edit distance visits this relation for every candidate character pair. Precompute it
    // once to avoid boxed Char/hash-map/Pair lookups in the hot loop.
    private val adjacentKeys = BooleanArray(26 * 26)

    init {
        val x = DoubleArray(26) { Double.NaN }
        val y = IntArray(26)
        rows.forEachIndexed { r, row ->
            for ((ch, centre) in row) {
                val i = ch - 'a'
                if (i in 0..25) {
                    x[i] = centre
                    y[i] = r
                }
            }
        }
        for (a in 0..25) for (b in 0..25) {
            adjacentKeys[a * 26 + b] = !x[a].isNaN() && !x[b].isNaN() &&
                abs(x[a] - x[b]) <= 1.0 + EPSILON && abs(y[a] - y[b]) <= 1
        }
    }

    fun adjacent(a: Char, b: Char): Boolean {
        val x = a - 'a'
        val y = b - 'a'
        return x in 0..25 && y in 0..25 && adjacentKeys[x * 26 + y]
    }

    companion object {
        private const val EPSILON = 1e-6

        /**
         * QWERTY as autocorrect was tuned on: rows staggered by half a key each, so a key has
         * two neighbours in the rows above and below. (On screen the third row sits a whole key
         * in, after the wider Shift; this older model is kept so QWERTY corrections don't change.)
         */
        val QWERTY = KeyGrid(
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").mapIndexed { r, row ->
                row.mapIndexed { c, ch -> ch to c + r * 0.5 }
            }
        )
    }
}
