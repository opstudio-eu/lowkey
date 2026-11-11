package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.KeyboardLanguage
import com.rheerkens.androidkeyboard.dictionary.LanguageCatalog
import com.rheerkens.androidkeyboard.settings.KeyArrangement

/**
 * One stop of the globe key's cycle: a keyboard [language] shown on its own layout (the rows in
 * `languages.txt`, such as German QWERTZ with Ü, Ö and Ä keys) or, for Latin-script languages, on
 * an alternative [arrangement] (QWERTY, AZERTY, QWERTZ, Dvorak, Colemak). The language picks the
 * word list and accents; the letter rows decide which keys neighbour each other.
 */
data class TypingKeyboard(val language: KeyboardLanguage, val arrangement: KeyArrangement? = null) {
    /** The three letter rows, top to bottom. */
    val rows: List<String> get() = arrangement?.rows ?: language.rows

    /** The layout's name: the language's own (`QWERTZ`, `ЙЦУКЕН`) or the arrangement's. */
    val layoutLabel: String get() = arrangement?.label ?: language.layoutName

    /** Short name; the language's own layout isn't spelled out. */
    val label: String get() = if (arrangement == null) language.name else "${language.name} · ${arrangement.label}"

    /** Name with the layout always spelled out, e.g. `Polski · QWERTY`: for Settings and announcements. */
    val fullLabel: String get() = "${language.name} · $layoutLabel"

    /** Space-bar name: like [label], but with the short language code next to a layout so it fits on a phone. */
    val spaceLabel: String get() = if (arrangement == null) language.name else "${language.shortLabel} · ${arrangement.label}"

    /** Stable preference value: `pl` for the language's own layout, `en_US:AZERTY` for another. */
    val id: String get() = if (arrangement == null) language.id else "${language.id}:${arrangement.name}"

    /** Globe order: languages in catalog order, each with its own layout first. */
    val order: Int get() = language.index * ORDER_STRIDE + (arrangement?.let { it.ordinal + 1 } ?: 0)

    override fun toString(): String = id

    companion object {
        private const val ORDER_STRIDE = 100

        /** Language names stored by 0.5.5 and earlier, when only English and Dutch existed. */
        private val LEGACY_LANGUAGES = mapOf("EN_US" to "en_US", "NL_NL" to "nl")

        /**
         * The keyboards [language] offers: its own layout, then (for Latin-script languages) every
         * arrangement whose name differs from it, so English has no second QWERTY.
         */
        fun options(language: KeyboardLanguage): List<TypingKeyboard> =
            listOf(TypingKeyboard(language)) +
                if (!language.latin) emptyList()
                else KeyArrangement.entries.filter { it.label != language.layoutName }.map { TypingKeyboard(language, it) }

        /** Every keyboard of [catalog], in globe order. */
        fun all(catalog: LanguageCatalog): List<TypingKeyboard> = catalog.languages.flatMap(::options)

        /**
         * Parses a stored [id]. Also reads 0.5.5's `EN_US:AZERTY` and older bare `EN_US`; an
         * arrangement that is the language's own layout (`EN_US:QWERTY`) means its own layout.
         */
        fun parse(id: String, catalog: LanguageCatalog): TypingKeyboard? {
            val languageId = id.substringBefore(':').let { LEGACY_LANGUAGES[it] ?: it }
            val language = catalog[languageId] ?: return null
            if (':' !in id) return TypingKeyboard(language)
            val arrangement = KeyArrangement.entries.firstOrNull { it.name == id.substringAfter(':') } ?: return null
            return options(language).firstOrNull { it.arrangement == arrangement }
                ?: TypingKeyboard(language).takeIf { arrangement.label == language.layoutName }
        }

        /** [keyboards] in globe order, without duplicates. */
        fun ordered(keyboards: Collection<TypingKeyboard>): List<TypingKeyboard> =
            keyboards.distinctBy { it.id }.sortedBy { it.order }
    }
}
