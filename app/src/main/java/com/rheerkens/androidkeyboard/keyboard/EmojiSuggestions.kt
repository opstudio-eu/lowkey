package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.Suggestion

/** Small, offline aliases for complete words; never guess from ambiguous prefixes such as "he". */
object EmojiSuggestions {
    private fun words(vararg entries: Pair<String, String>) = buildMap {
        for ((emoji, aliases) in entries) aliases.split(' ').forEach { put(it, emoji) }
    }

    private val keywords = mapOf(
        "en" to words(
            "👋" to "hi hello hey bye goodbye", "❤️" to "love heart", "☕" to "coffee tea",
            "🍺" to "beer", "🥂" to "cheers", "🍕" to "pizza", "🎂" to "birthday cake",
            "😊" to "happy smile", "😂" to "laugh laughing", "😢" to "sad crying",
            "🙏" to "thanks thank", "👍" to "okay", "🎉" to "party congratulations",
            "☀️" to "sun sunny", "🌧️" to "rain", "🔥" to "fire", "🐶" to "dog", "🐱" to "cat",
        ),
        "nl" to words(
            "👋" to "hoi hallo hey dag doei", "❤️" to "liefde hart", "☕" to "koffie thee",
            "🍺" to "bier", "🥂" to "proost", "🍕" to "pizza", "🎂" to "verjaardag taart",
            "😊" to "blij glimlach", "😂" to "lach lachen", "😢" to "verdrietig huilen",
            "🙏" to "dank bedankt", "👍" to "oké oke", "🎉" to "feest gefeliciteerd",
            "☀️" to "zon zonnig", "🌧️" to "regen", "🔥" to "vuur", "🐶" to "hond", "🐱" to "kat",
        ),
    )

    fun suggest(typed: String, languages: List<String>, catalog: EmojiCatalog?): Suggestion? {
        if (catalog == null) return null
        val word = typed.lowercase()
        for (language in languages) {
            val value = keywords[language.substringBefore('_')]?.get(word) ?: continue
            val emoji = catalog.find(value) ?: continue
            return Suggestion(emoji.value, emojiName = emoji.name)
        }
        return null
    }
}
