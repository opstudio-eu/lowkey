package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.settings.TypingLayout

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

    private fun letters(s: String): List<Key> = chars(s).map { key ->
        (key as Key.Char).copy(accents = ACCENTS[key.label].orEmpty())
    }

    private fun bottomRow(switchKey: Key.LayerSwitch) = listOf(
        switchKey, Key.Globe(), Key.Char(","), Key.Space(), Key.Char("."), Key.Enter(),
    )

    /** QWERTY letters. Dutch also uses QWERTY; only suggestions and the space label differ. */
    fun letters(@Suppress("UNUSED_PARAMETER") layout: TypingLayout) = KeyboardLayout(
        listOf(
            "qwertyuiop".mapIndexed { index, letter ->
                Key.Char(
                    letter.toString(),
                    longPressOutput = ((index + 1) % 10).toString(),
                    accents = ACCENTS[letter.toString()].orEmpty(),
                )
            },
            listOf(Key.Gap()) + letters("a s d f g h j k l") + Key.Gap(),
            listOf(Key.Shift()) + letters("z x c v b n m") + Key.Backspace(),
            bottomRow(Key.LayerSwitch(Layer.SYMBOLS, "?123")),
        )
    )

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

    fun forLayer(layer: Layer, layout: TypingLayout): KeyboardLayout = when (layer) {
        Layer.LETTERS -> letters(layout)
        Layer.SYMBOLS -> SYMBOLS
        Layer.SYMBOLS_2 -> SYMBOLS_2
    }
}
