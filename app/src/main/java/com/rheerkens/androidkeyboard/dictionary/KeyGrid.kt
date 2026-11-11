package com.rheerkens.androidkeyboard.dictionary

import kotlin.math.abs

/**
 * Which letters are neighbours on a keyboard, so a slip onto the next key costs less in
 * [Corrector]. [rows] gives each letter row's keys (top to bottom) with the x of their centres,
 * in key widths. Two letters are neighbours when they're at most a key width apart horizontally,
 * in the same or adjacent rows. Letters of any script take part (`й` and `ц` on a Russian
 * keyboard); an accented letter without a key of its own sits on its base letter's key (`ż` on Z).
 */
class KeyGrid(rows: List<List<Pair<Char, Double>>>) {
    private val keyCount: Int
    /** Key index per character (its own key, else its base letter's), or -1. */
    private val keyIndex = IntArray(TABLE_SIZE) { -1 }
    // Edit distance visits this relation for every candidate character pair. Precompute it
    // once to avoid boxed Char/hash-map/Pair lookups in the hot loop.
    private val neighbours: BooleanArray

    init {
        val keys = rows.flatMapIndexed { r, row -> row.filter { it.first.code < TABLE_SIZE }.map { (ch, x) -> Triple(ch, x, r) } }
        keyCount = keys.size
        keys.forEachIndexed { i, (ch, _, _) -> keyIndex[ch.code] = i }
        for (code in 0 until TABLE_SIZE) {
            if (keyIndex[code] >= 0) continue
            val base = AccentFolding.baseLetter(code.toChar())
            if (base.code != code && base.code < TABLE_SIZE) keyIndex[code] = keyIndex[base.code]
        }
        neighbours = BooleanArray(keyCount * keyCount)
        for (a in 0 until keyCount) for (b in 0 until keyCount) {
            val (_, xa, ra) = keys[a]
            val (_, xb, rb) = keys[b]
            neighbours[a * keyCount + b] = abs(xa - xb) <= 1.0 + EPSILON && abs(ra - rb) <= 1
        }
    }

    /** True if [a] and [b] (lowercase) sit on neighbouring keys. */
    fun adjacent(a: Char, b: Char): Boolean {
        val x = if (a.code < TABLE_SIZE) keyIndex[a.code] else -1
        val y = if (b.code < TABLE_SIZE) keyIndex[b.code] else -1
        return x >= 0 && y >= 0 && neighbours[x * keyCount + y]
    }

    companion object {
        private const val EPSILON = 1e-6
        private const val TABLE_SIZE = AccentFolding.TABLE_SIZE

        /** Every letter's base letter, even if it has a key of its own (`ü` → `u`). */
        private val bases = CharArray(TABLE_SIZE) { AccentFolding.baseLetter(it.toChar()) }

        /**
         * True if [a] and [b] are the same letter apart from accents, even when the accented one
         * has its own key (`u` and `ü` on German): a slip as likely as a neighbouring key.
         */
        fun sameBaseLetter(a: Char, b: Char): Boolean =
            a.code < TABLE_SIZE && b.code < TABLE_SIZE && bases[a.code] == bases[b.code]

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
