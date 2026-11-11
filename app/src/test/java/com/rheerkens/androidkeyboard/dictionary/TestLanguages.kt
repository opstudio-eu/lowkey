package com.rheerkens.androidkeyboard.dictionary

import java.io.File

/** The shipped language catalog and word lists, read from the source tree (tests run in `app/`). */
object TestLanguages {
    val catalog: LanguageCatalog by lazy { LanguageCatalog.parse(File("src/main/assets/dict/languages.txt").readText()) }

    fun language(id: String): KeyboardLanguage = catalog[id] ?: error("No language $id")

    /** A bundled language's full lexicon plus the built-in extra words, as the app loads it. */
    fun bundledLexicon(id: String): Lexicon {
        val language = language(id)
        val base = File("src/main/assets/dict/${language.dictionaryFile}").inputStream().use { Lexicon.parseGzip(it, language.keys, language.rules) }
        val extra = if (id.startsWith("en_")) BuiltInWords.TECH else BuiltInWords.TECH + BuiltInWords.ENGLISH
        return base.withExtraWords(BuiltInWords.ENGLISH + extra, 120)
    }
}
