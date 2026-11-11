package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.settings.KeyArrangement
import com.rheerkens.androidkeyboard.settings.TypingKeyboard

/** One key. [weight] is its share of the row width (a letter key is 1). */
sealed interface Key {
    val weight: Float

    data class Char(
        val label: String,
        val output: String = label,
        override val weight: Float = 1f,
        val longPressOutput: String? = null,
        /** Accented letters offered after [longPressOutput] in the long-press popup. */
        val accents: List<String> = emptyList(),
    ) : Key {
        /** Long-press popup choices; the first is selected when the popup opens. */
        val alternates: List<String> get() = listOfNotNull(longPressOutput) + accents
    }
    data class Shift(override val weight: Float = 1.5f) : Key
    data class Backspace(override val weight: Float = 1.5f) : Key
    /** Switches between layers; [label] is `?123`, `=\<`, or `ABC`. */
    data class LayerSwitch(val target: Layer, val label: String, override val weight: Float = 1.5f) : Key
    data class Globe(override val weight: Float = 1f) : Key
    data class Space(override val weight: Float = 4f) : Key
    data class Enter(override val weight: Float = 1.5f) : Key
    /** Flexible spacer used to center the middle letter row. */
    data class Gap(override val weight: Float = 0.5f) : Key
}

enum class Layer { LETTERS, SYMBOLS, SYMBOLS_2 }

/** Four rows of keys. */
data class KeyboardLayout(val rows: List<List<Key>>)

object Layouts {
    private fun chars(s: String): List<Key> = s.split(' ').filter { it.isNotEmpty() }.map { Key.Char(it) }

    /** Accents per letter, most common first (Dutch, French, German, Spanish, Nordic, Polish, Czech). */
    val ACCENTS: Map<String, List<String>> = mapOf(
        "a" to "á à â ä ã å æ",
        "c" to "ç ć č",
        "e" to "é è ê ë ē ę ė",
        "i" to "í ì î ï ī",
        "n" to "ñ ń",
        "o" to "ó ò ô ö õ ø œ",
        "s" to "ß ś š",
        "u" to "ú ù û ü ū",
        "y" to "ý ÿ",
        "z" to "ž ź ż",
    ).mapValues { it.value.split(' ') }

    private fun bottomRow(switchKey: Key.LayerSwitch) = listOf(
        switchKey, Key.Globe(), Key.Char(","), Key.Space(), Key.Char("."), Key.Enter(),
    )

    /**
     * The letter layer for [keyboard]'s arrangement. The language changes only suggestions and
     * the space-bar label. The top row offers the digits 1–0 on long-press; shorter middle rows
     * are centred with gaps; Shift and Backspace share what the third row leaves of ten keys,
     * but stay at least a key wide.
     */
    fun letters(keyboard: TypingKeyboard): KeyboardLayout = lettersCache.getValue(keyboard.arrangement)

    private val lettersCache: Map<KeyArrangement, KeyboardLayout> =
        KeyArrangement.entries.associateWith(::buildLetters)

    private fun buildLetters(arrangement: KeyArrangement): KeyboardLayout {
        val (top, middle, bottom) = arrangement.rows.map { row -> row.map { letter -> letterKey(letter.toString()) } }
        val digits = top.mapIndexed { index, key ->
            if (top.size == ROW_KEYS) key.copy(longPressOutput = ((index + 1) % 10).toString()) else key
        }
        val gap = (ROW_KEYS - middle.size) / 2f
        val side = maxOf(1f, (ROW_KEYS - bottom.size) / 2f)
        return KeyboardLayout(
            listOf(
                digits,
                if (gap > 0f) listOf(Key.Gap(gap)) + middle + Key.Gap(gap) else middle,
                listOf(Key.Shift(side)) + bottom + Key.Backspace(side),
                bottomRow(Key.LayerSwitch(Layer.SYMBOLS, "?123")),
            )
        )
    }

    private fun letterKey(letter: String) = Key.Char(letter, accents = ACCENTS[letter].orEmpty())

    /** Letter keys in a full row. */
    private const val ROW_KEYS = 10

    val SYMBOLS = KeyboardLayout(
        listOf(
            chars("1 2 3 4 5 6 7 8 9 0"),
            chars("@ # $ _ & - + ( ) /"),
            listOf(Key.LayerSwitch(Layer.SYMBOLS_2, "=\\<")) + chars("* \" ' : ; ! ?") + Key.Backspace(),
            bottomRow(Key.LayerSwitch(Layer.LETTERS, "ABC")),
        )
    )

    val SYMBOLS_2 = KeyboardLayout(
        listOf(
            chars("[ ] { } < > = ^ % ~"),
            chars("` | \\ € £ ¥ ° • ±") ,
            listOf(Key.LayerSwitch(Layer.SYMBOLS, "?123")) + chars("× ÷ … ¿ ¡ § ¶") + Key.Backspace(),
            bottomRow(Key.LayerSwitch(Layer.LETTERS, "ABC")),
        )
    )

    fun forLayer(layer: Layer, keyboard: TypingKeyboard): KeyboardLayout = when (layer) {
        Layer.LETTERS -> letters(keyboard)
        Layer.SYMBOLS -> SYMBOLS
        Layer.SYMBOLS_2 -> SYMBOLS_2
    }
}
