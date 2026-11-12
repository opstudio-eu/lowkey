package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.KeyGrid
import com.rheerkens.androidkeyboard.settings.BottomKey
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
        val opensEmojiOnHold: Boolean = false,
    ) : Key {
        /** Long-press popup choices; the first is selected when the popup opens. */
        val alternates: List<String> get() = listOfNotNull(longPressOutput) + accents
    }
    data class Shift(override val weight: Float = 1.5f) : Key
    data class Backspace(override val weight: Float = 1.5f) : Key
    /** Switches between layers; [label] is `?123`, `=\<`, or `ABC`. */
    data class LayerSwitch(val target: Layer, val label: String, override val weight: Float = 1.5f) : Key
    data class Globe(override val weight: Float = 1f) : Key
    data class Emoji(override val weight: Float = 1f) : Key
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
    private fun accents(letter: String, keyboard: TypingKeyboard): List<String> =
        (keyboard.language.accents[letter].orEmpty() + ACCENTS[letter].orEmpty()).distinct()

    private fun bottomRow(switchKey: Key.LayerSwitch) = listOf(
        switchKey, Key.Globe(), Key.Char(",", opensEmojiOnHold = true), Key.Space(), Key.Char("."), Key.Enter(),
    )

    /**
     * The letter layer for [keyboard]: its language's own rows (QWERTZ with Ü, Ö and Ä for German,
     * ЙЦУКЕН for Russian) or an alternative arrangement, with the language's accents first on
     * long-press. The first ten keys of the top row offer the digits 1–0 on long-press. Rows are
     * as wide as the widest of ten keys and the top two rows; shorter rows are centred with gaps,
     * and Shift and Backspace share what the third row leaves, but stay a key and a half wide
     * (Dvorak's nine-letter row is then twelve keys wide, and its letters a little narrower).
     */
    fun letters(keyboard: TypingKeyboard): KeyboardLayout = lettersCache.getOrPut(keyboard.id) { buildLetters(keyboard) }

    private val lettersCache = ConcurrentHashMap<String, KeyboardLayout>()

    private fun buildLetters(keyboard: TypingKeyboard): KeyboardLayout {
        val (top, middle, bottom) = keyboard.rows.map { row -> row.map { letter -> letterKey(letter.toString(), keyboard) } }
        val width = maxOf(ROW_KEYS, top.size, middle.size).toFloat()
        val digits = top.mapIndexed { index, key ->
            if (index < ROW_KEYS) key.copy(longPressOutput = ((index + 1) % 10).toString()) else key
        }
        fun centred(keys: List<Key>): List<Key> {
            val gap = (width - keys.size) / 2f
            return if (gap > 0f) listOf(Key.Gap(gap)) + keys + Key.Gap(gap) else keys
        }
        val side = maxOf(MIN_SIDE_KEY, (width - bottom.size) / 2f)
        return KeyboardLayout(
            listOf(
                centred(digits),
                centred(middle),
                listOf(Key.Shift(side)) + bottom + Key.Backspace(side),
                bottomRow(Key.LayerSwitch(Layer.SYMBOLS, "?123")),
            )
        )
    }

    /**
     * Neighbouring letters for autocorrect on [keyboard]: where its keys are drawn, except
     * QWERTY rows, which keep the grid their corrections were tuned on ([KeyGrid.QWERTY]).
     */
    fun keyGrid(keyboard: TypingKeyboard): KeyGrid =
        if (keyboard.rows == QWERTY_ROWS) KeyGrid.QWERTY else gridCache.getOrPut(keyboard.id) { KeyGrid(letterCentres(keyboard)) }

    private val gridCache = ConcurrentHashMap<String, KeyGrid>()

    /**
     * Centres of [keyboard]'s letter keys as drawn, per letter row, in key widths of a
     * ten-key row: rows wider than ten keys (Dvorak's third, Nordic rows) are scaled down to fit,
     * as on screen.
     */
    fun letterCentres(keyboard: TypingKeyboard): List<List<Pair<Char, Double>>> =
        letters(keyboard).rows.take(3).map { row ->
            val scale = ROW_KEYS / row.sumOf { it.weight.toDouble() }
            var left = 0.0
            row.mapNotNull { key ->
                val centre = (left + key.weight / 2.0) * scale
                left += key.weight
                (key as? Key.Char)?.label?.singleOrNull()?.takeIf { it.isLetter() }?.let { it to centre }
            }
        }

    private fun letterKey(letter: String, keyboard: TypingKeyboard) = Key.Char(letter, accents = accents(letter, keyboard))

    private val QWERTY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    /** Width of Shift and Backspace at least, in keys: as on QWERTY, so they stay easy to hit. */
    private const val MIN_SIDE_KEY = 1.5f

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

    fun forLayer(layer: Layer, keyboard: TypingKeyboard, numberRow: Boolean = false, bottomKey: BottomKey = BottomKey.EMOJI): KeyboardLayout {
        val layout = when (layer) {
            Layer.LETTERS -> letters(keyboard)
            Layer.SYMBOLS -> SYMBOLS
            Layer.SYMBOLS_2 -> SYMBOLS_2
        }
        val rows = layout.rows.map { row ->
            row.map { key -> if (key is Key.Globe && bottomKey == BottomKey.EMOJI) Key.Emoji(key.weight) else key }
        }
        return KeyboardLayout(if (numberRow && layer == Layer.LETTERS) listOf(SYMBOLS.rows.first()) + rows else rows)
    }
}
