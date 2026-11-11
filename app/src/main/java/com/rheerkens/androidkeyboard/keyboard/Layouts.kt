package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.KeyboardLanguage
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Accents per letter, most common first (Dutch, French, German, Spanish, Nordic, Polish, Czech).
     * A keyboard language puts its own accents (`languages.txt`) before these.
     */
    val ACCENTS: Map<String, List<String>> = mapOf(
        "a" to "á à â ä ã å æ ą",
        "c" to "ç ć č",
        "e" to "é è ê ë ē ę ė",
        "i" to "í ì î ï ī",
        "l" to "ł",
        "n" to "ñ ń",
        "o" to "ó ò ô ö õ ø œ",
        "s" to "ß ś š",
        "u" to "ú ù û ü ū",
        "y" to "ý ÿ",
        "z" to "ž ź ż",
    ).mapValues { it.value.split(' ') }

    /** Long-press accents for [letter]: the language's own first, then the general list. */
    fun accents(letter: String, language: KeyboardLanguage): List<String> =
        (language.accents[letter].orEmpty() + ACCENTS[letter].orEmpty()).distinct()

    private fun bottomRow(switchKey: Key.LayerSwitch) = listOf(
        switchKey, Key.Globe(), Key.Char(","), Key.Space(), Key.Char("."), Key.Enter(),
    )

    private val letterLayouts = ConcurrentHashMap<String, KeyboardLayout>()

    /**
     * The letter layer of [language], from its three rows in `languages.txt` (QWERTY, QWERTZ,
     * AZERTY, Nordic or Cyrillic). Every letter key is as wide; shorter rows are centred, and
     * Shift and Backspace fill the bottom row's sides. Holding the first ten keys of the top row
     * types 1–0.
     */
    fun letters(language: KeyboardLanguage): KeyboardLayout = letterLayouts.getOrPut(language.id) {
        val (top, middle, bottom) = language.letterRows
        val width = maxOf(top.size, middle.size, bottom.size + 2).toFloat()
        fun key(letter: String, index: Int = -1) = Key.Char(
            letter,
            longPressOutput = if (index in 0..9) ((index + 1) % 10).toString() else null,
            accents = accents(letter, language),
        )
        fun centred(keys: List<Key>): List<Key> {
            val side = (width - keys.size) / 2
            return if (side > 0f) listOf(Key.Gap(side)) + keys + Key.Gap(side) else keys
        }
        val side = maxOf(1f, (width - bottom.size) / 2)
        KeyboardLayout(
            listOf(
                centred(top.mapIndexed { i, letter -> key(letter, i) }),
                centred(middle.map { key(it) }),
                listOf(Key.Shift(side)) + bottom.map { key(it) } + Key.Backspace(side),
                bottomRow(Key.LayerSwitch(Layer.SYMBOLS, "?123")),
            )
        )
    }

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

    fun forLayer(layer: Layer, language: KeyboardLanguage): KeyboardLayout = when (layer) {
        Layer.LETTERS -> letters(language)
        Layer.SYMBOLS -> SYMBOLS
        Layer.SYMBOLS_2 -> SYMBOLS_2
    }
}
