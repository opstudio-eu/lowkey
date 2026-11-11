package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import java.io.File

/**
 * The shipped language catalog and word lists, read from the source tree (tests run in `app/`).
 * Bundled lists come from the app's assets; Dutch and Polish, which download on the phone, from
 * `src/test/resources/dict/` (written by `scripts/build-dictionaries.py`).
 */
object TestLanguages {
    val catalog: LanguageCatalog by lazy { LanguageCatalog.parse(File("src/main/assets/dict/languages.txt").readText()) }

    fun language(id: String): KeyboardLanguage = catalog[id] ?: error("No language $id")

    /** A keyboard by its id: `pl`, `en_US:AZERTY`. */
    fun keyboard(id: String): TypingKeyboard = TypingKeyboard.parse(id, catalog) ?: error("No keyboard $id")

    /** The word list file of [id]: the APK's for a bundled language, else the test copy. */
    fun dictFile(id: String): File {
        val language = language(id)
        return File(if (language.bundled) "src/main/assets/dict" else "src/test/resources/dict", language.dictionaryFile)
    }

    /** A language's full lexicon plus the built-in extra words, as the app loads it. */
    fun lexicon(id: String): Lexicon {
        val language = language(id)
        val base = dictFile(id).inputStream().use { Lexicon.parseGzip(it, language.folding, language.rules) }
        val extra = if (id.startsWith("en_")) BuiltInWords.TECH else BuiltInWords.TECH + BuiltInWords.ENGLISH
        return base.withExtraWords(BuiltInWords.ENGLISH + extra, 120)
    }
}
