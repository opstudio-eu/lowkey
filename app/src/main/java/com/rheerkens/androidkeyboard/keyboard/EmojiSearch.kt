package com.rheerkens.androidkeyboard.keyboard

import java.text.Normalizer

/**
 * One language's emoji names and search keywords from Unicode CLDR (`emoji/keywords-<lang>.txt`,
 * built by `scripts/build-emoji-keywords.py`), by emoji.
 */
class EmojiAnnotations(val names: Map<String, String>, val keywords: Map<String, List<String>>) {
    companion object {
        /** Lines of `emoji<TAB>name<TAB>keyword|keyword`; `#` starts a comment line. */
        fun parse(text: String): EmojiAnnotations {
            val names = HashMap<String, String>()
            val keywords = HashMap<String, List<String>>()
            for (line in text.lineSequence()) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('\t')
                if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) continue
                names[parts[0]] = parts[1]
                keywords[parts[0]] = parts.getOrNull(2)?.split('|')?.filter { it.isNotBlank() }.orEmpty()
            }
            return EmojiAnnotations(names, keywords)
        }
    }
}

/**
 * Finds emoji by what a person would call them: the Unicode name ("thumbs up") and, for each
 * language asked for, CLDR's name and keywords ("duim omhoog", "goed", "like"). Every word typed
 * has to start a word of the emoji's names or keywords, so "red hea" finds ❤️. Whole names first,
 * then whole keywords and names that start with the query, then other prefixes of a name or
 * keyword, then whole-word matches, then word prefixes; within each, shorter names first, then
 * the panel's own order (smileys first).
 */
class EmojiSearch(private val catalog: EmojiCatalog) {
    private class Phrase(val text: String, val words: List<String>, val isName: Boolean)

    /** Unicode names, and each language's phrases, per emoji in catalog order (built per language on first use). */
    private val unicode: List<List<Phrase>> = catalog.entries.map { listOf(phrase(it.name, isName = true)) }
    private val byLanguage = HashMap<String, List<List<Phrase>>>()

    private fun phrases(language: String): List<List<Phrase>>? {
        val annotations = catalog.annotations[language] ?: return null
        return synchronized(byLanguage) {
            byLanguage.getOrPut(language) {
                catalog.entries.map { item ->
                    buildList {
                        annotations.names[item.value]?.let { add(phrase(it, isName = true)) }
                        annotations.keywords[item.value]?.forEach { add(phrase(it, isName = false)) }
                    }
                }
            }
        }
    }

    /** Builds the tables for [languages] now (off the main thread), not on the first keystroke. */
    fun prepare(languages: List<String>) {
        languages.forEach { phrases(it) }
    }

    /**
     * Emoji for [query] in [languages] (keyboard language ids such as "nl" or "en_US"); English
     * always counts, as the Unicode names are English. An empty query finds nothing.
     */
    fun search(query: String, languages: List<String>, limit: Int = MAX_RESULTS): List<EmojiItem> {
        val q = normalize(query)
        val tokens = q.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val tables = (languages.map { it.substringBefore('_').lowercase() } + "en").distinct().mapNotNull(::phrases)
        // "cars" is the keyword "car" as much as the keyword "cars".
        val singulars = EmojiNameIndex.singulars(q)
        val found = ArrayList<IntArray>()
        catalog.entries.forEachIndexed { index, item ->
            val rank = score(q, singulars, tokens, unicode[index] + tables.flatMap { it[index] }) ?: return@forEachIndexed
            // Then the emoji people use most, then the shorter Unicode name ("car": 🚗 automobile
            // before 🚃 railway car), then the panel's order.
            found += intArrayOf(rank, popularity[item.value] ?: POPULAR.size, unicode[index][0].words.size, index)
        }
        found.sortWith { a, b -> a.indices.firstNotNullOfOrNull { i -> a[i].compareTo(b[i]).takeIf { it != 0 } } ?: 0 }
        return found.take(limit).map { catalog.entries[it[3]] }
    }

    private fun score(query: String, singulars: List<String>, tokens: List<String>, phrases: List<Phrase>): Int? {
        var best = Int.MAX_VALUE
        for (p in phrases) {
            val rank = when {
                p.text == query -> if (p.isName) EXACT_NAME else EXACT_KEYWORD
                // A singular reading counts as a keyword: "houses" is 🏘️ houses before 🏠 house.
                p.text in singulars -> EXACT_KEYWORD
                // "thumbs" names 👍 thumbs up as well as a keyword "thumbs" names 👎.
                p.isName && p.text.startsWith("$query ") -> EXACT_KEYWORD
                p.text.startsWith(query) -> if (p.isName) NAME_PREFIX else KEYWORD_PREFIX
                else -> continue
            }
            best = minOf(best, rank)
        }
        if (best != Int.MAX_VALUE) return best
        var allWhole = true
        for (token in tokens) {
            // A plural finds its singular too: "cats" is 🐈 cat.
            val forms = listOf(token) + EmojiNameIndex.singulars(token)
            if (phrases.any { p -> p.words.any { it in forms } }) continue
            if (phrases.none { p -> p.words.any { it.startsWith(token) } }) return null
            allWhole = false
        }
        return if (allWhole) WHOLE_WORDS else PREFIXES
    }

    companion object {
        const val MAX_RESULTS = 96

        /** The most used emoji, most used first (after Unicode's emoji frequency ranking). */
        private val POPULAR = ("😂 ❤️ 🤣 👍 😭 🙏 😘 🥰 😍 😊 🎉 😁 💕 🥺 😅 🔥 ☺️ 🤦 ♥️ 🤷 🙄 😆 🤗 😉 " +
            "🎂 🤔 👏 🙂 😳 🥳 😎 👌 💜 😔 💪 ✨ 💖 👀 😋 😏 😢 👉 💗 😩 💯 🌹 💞 🎈 💙 😃 😡 💐 😜 🙈 " +
            "🤞 😄 🤤 🙌 🤪 ❣️ 😀 💋 💀 👇 💔 😌 💓 🤩 🙃 😬 😱 😴 🤭 😐 🌞 😒 😇 🌸 😈 🎶 ✌️ 🎊 🥵 😞 " +
            "💚 ☀️ 🖤 💰 😚 👑 🎁 💥 🙋 ☹️ 😑 🥴 👈 💩 ✅ 🐶 🐱 🍕 🍺 ☕ 🚗 🏠").split(' ')
        private val popularity = POPULAR.withIndex().associate { it.value to it.index }
        private const val EXACT_NAME = 0
        private const val EXACT_KEYWORD = 1
        private const val NAME_PREFIX = 2
        private const val KEYWORD_PREFIX = 3
        private const val WHOLE_WORDS = 4
        private const val PREFIXES = 5
        private val MARKS = Regex("\\p{Mn}+")
        private val SEPARATORS = Regex("[^\\p{L}\\p{N}+]+")

        /** Lower case, accents dropped ("café" finds "cafe"), words separated by single spaces. */
        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
                .replace(SEPARATORS, " ").trim()

        private fun phrase(text: String, isName: Boolean): Phrase {
            val normal = normalize(text)
            return Phrase(normal, normal.split(' ').filter { it.isNotEmpty() }, isName)
        }
    }
}
