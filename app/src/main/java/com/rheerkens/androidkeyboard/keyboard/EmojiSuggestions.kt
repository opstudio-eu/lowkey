package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.Suggestion

/**
 * One emoji for a typed word, from small offline aliases and, for English, the emoji's own
 * Unicode names ([EmojiNameIndex]). Never guesses from ambiguous prefixes such as "he".
 */
object EmojiSuggestions {
    private fun words(vararg entries: Pair<String, String>) = buildMap {
        for ((emoji, aliases) in entries) aliases.split(' ').forEach { put(it, emoji) }
    }

    private val keywords = mapOf(
        "en" to words(
            "👋" to "hi hello hey bye goodbye", "❤️" to "love heart",
            "🍺" to "beer", "🥂" to "cheers", "🍕" to "pizza", "🎂" to "birthday cake",
            "😊" to "happy smile", "😂" to "laugh laughing", "😢" to "sad crying",
            "🙏" to "thanks thank", "👍" to "okay", "🎉" to "party congratulations",
            "☀️" to "sun sunny", "🌧️" to "rain", "🔥" to "fire", "🐶" to "dog", "🐱" to "cat",
            "💧" to "water", "⚽" to "ball balls soccer", "❄️" to "snow", "🏃" to "running",
            "🍽️" to "food dinner", "💻" to "computer laptop", "🌸" to "flower flowers",
            "🌙" to "moon", "📦" to "box boxes", "🎮" to "game games", "🍚" to "rice", "🚗" to "car auto",
            "🪨" to "stone stones", "🥜" to "nut nuts", "🏢" to "building buildings", "📹" to "video videos",
            "💎" to "diamond diamonds", "☕" to "coffee tea beverage beverages", "🗺️" to "map maps",
            "👆" to "finger fingers", "🎪" to "circus", "💰" to "money", "🎄" to "christmas", "🎅" to "santa",
            "🏖️" to "beach beaches", "🌃" to "night nights", "🏢" to "office offices",
            "💵" to "dollar dollars", "💶" to "euro euros", "🥅" to "goal goals", "🎣" to "fishing",
            "🧩" to "puzzle puzzles", "🧀" to "cheese", "🍫" to "chocolate", "🍯" to "honey", "🍷" to "wine wines",
            "🍸" to "cocktail cocktails", "🤠" to "cowboy cowboys", "👮" to "police", "💑" to "couple couples",
            "🎬" to "movie movies", "🎞️" to "film films", "👜" to "bag bags",
            "🛍️" to "shopping", "♟️" to "chess", "🎓" to "graduation", "🌍" to "globe", "⏰" to "alarm alarms",
            "🌴" to "palm palms", "👕" to "shirt shirts", "👟" to "shoe shoes", "🏮" to "lantern lanterns",
            "🧢" to "cap caps", "💃" to "dancer dancers", "😷" to "mask masks",
            "🪽" to "wing wings", "🛞" to "wheel wheels", "🫧" to "bubble bubbles",
        ),
        "nl" to words(
            "👋" to "hoi hallo hey dag doei", "❤️" to "liefde hart", "☕" to "koffie thee",
            "🍺" to "bier", "🥂" to "proost", "🍕" to "pizza", "🎂" to "verjaardag taart",
            "😊" to "blij glimlach", "😂" to "lach lachen", "😢" to "verdrietig huilen",
            "🙏" to "dank bedankt", "👍" to "oké oke duim duimpje", "🎉" to "feest gefeliciteerd",
            "☀️" to "zon zonnig", "🌧️" to "regen", "🔥" to "vuur", "🐶" to "hond", "🐱" to "kat",
            "🚗" to "auto",
        ),
    )

    /**
     * The emoji for [typed] in the first of [languages] that has one: its aliases first, then for
     * English the emoji names, so "thumb" offers 👍 (thumbs up) and "pancakes" 🥞.
     */
    fun suggest(typed: String, languages: List<String>, catalog: EmojiCatalog?): Suggestion? {
        if (catalog == null) return null
        val word = typed.lowercase()
        for (language in languages) {
            val code = language.substringBefore('_')
            val aliases = keywords[code]
            // English plurals reach the aliases too, so "cars" stays 🚗 rather than a railway car.
            val alias = aliases?.get(word) ?: if (code == "en" && word.length >= 4) {
                EmojiNameIndex.singulars(word).firstNotNullOfOrNull { aliases?.get(it) }
            } else null
            // A keyword's emoji the device cannot show blocks name guesses for that language too:
            // without 🪨, "stone" must not fall through to a curling stone.
            val emoji = if (alias != null) catalog.find(alias) else if (code == "en") catalog.nameIndex.find(word) else null
            if (emoji != null) return Suggestion(emoji.value, emojiName = emoji.name)
        }
        return null
    }
}

/**
 * English emoji names as single-word keywords: "thumbs up" answers "thumbs" and "thumb". Only
 * pictures count (smileys, people, animals, food, travel, activities and objects); symbols such
 * as "NEW button" and flags such as "flag: Jordan" would answer everyday words and names.
 *
 * A word counts only where it names the picture: as the last word of the name ("cut of meat"),
 * or anywhere when the rest of the name is generic ("thinking face", "thumbs up", "person
 * running"). So "medical" does not answer "face with medical mask", nor "steam" a face with steam
 * from its nose. Typed words shorter than four letters, and common words that name no picture of
 * their own ("open", "right"), never match; the emoji takes a suggestion slot, so it has to be
 * worth it. Among emoji sharing a word, the one whose name starts with it wins, then the shortest
 * name, then the first in Unicode order ("thumb" picks thumbs up over thumbs down).
 */
class EmojiNameIndex(entries: List<EmojiItem>) {
    private val exact = HashMap<String, Ranked>()
    /** Name words that are plurals, under their likely singular: thumb for "thumbs up". */
    private val plurals = HashMap<String, Ranked>()

    private data class Ranked(val item: EmojiItem, val rank: Long)

    init {
        entries.forEachIndexed { order, item ->
            // Mixed skin-tone pairs that have no untoned base are listed as emoji of their own.
            if (item.category !in PICTURES || item.name.contains("skin tone")) return@forEachIndexed
            val words = item.name.lowercase().split(SEPARATORS).filter { it.isNotEmpty() }
            words.forEachIndexed { position, word ->
                if (word.length < MIN_LENGTH - 1 || word in COMMON) return@forEachIndexed
                val namesPicture = position == words.lastIndex ||
                    words.all { it == word || it.length < 2 || it in GENERIC }
                if (!namesPicture) return@forEachIndexed
                // Lower is better: name starts with the word, then fewer words, then Unicode order.
                val ranked = Ranked(item, (if (position == 0) 0L else 1L shl 40) + (words.size.toLong() shl 20) + order)
                keep(exact, word, ranked)
                // One likely singular, not every reading: "fries" must not make "frie" a keyword.
                singular(word)?.takeIf { it !in COMMON }?.let { keep(plurals, it, ranked) }
            }
        }
    }

    fun find(word: String): EmojiItem? {
        val key = word.lowercase()
        if (!usable(key)) return null
        // A typed plural may name a singular emoji ("cookies"); a typed singular may name a plural
        // one ("thumb"). The best rank wins, so "wings" is the wing before "money with wings".
        // A plural reading loses a tie with the word itself, so "house" is 🏠 rather than 🏘️ houses.
        val singular = singulars(key).filter { it !in COMMON }
        val viaPlural = (listOf(plurals[key]) + singular.flatMap { listOf(exact[it], plurals[it]) })
            .filterNotNull().map { it.copy(rank = it.rank + PLURAL_PENALTY) }
        return (listOfNotNull(exact[key]) + viaPlural).minByOrNull { it.rank }?.item
    }

    private fun keep(map: HashMap<String, Ranked>, key: String, ranked: Ranked) {
        val current = map[key]
        if (current == null || ranked.rank < current.rank) map[key] = ranked
    }

    companion object {
        private val SEPARATORS = Regex("[^\\p{L}]+")
        private val PICTURES = setOf(
            "Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink",
            "Travel & Places", "Activities", "Objects",
        )
        /** Shortest typed word to look up; a name's own words may be one letter shorter ("eggs"). */
        private const val MIN_LENGTH = 4
        /** Below the word-count step (1 shl 20) and above any Unicode order (under 4000 emoji). */
        private const val PLURAL_PENALTY = 1L shl 19

        /** Name words that leave the rest of a name to say what it shows ("thinking face"). */
        private val GENERIC = setOf("face", "person", "man", "woman", "men", "women", "people", "hand",
            "hands", "up", "with", "eyes")

        /** Words in emoji names that are too common or too vague to stand for that emoji. */
        private val COMMON = (
            "with without from over under behind away back down front left right middle upper " +
            "first last half full empty open closed round flat straight soft high long " +
            "large small little older face hand hands person people woman women adult " +
            "sign symbol symbols mark button place line level points position piece head " +
            "hear speak call done getting taking holding showing playing wearing facing " +
            "standing raised raising lowered crossed folded rolling rolled pointing upside " +
            "horizontal horizontally vertical vertically diagonal control test track note " +
            "notes kick pick roll watch drop tear match mate sake jack service guide health " +
            "together national speed eight nine eleven twelve thirty hundred quarter three " +
            "four five seven black white yellow green blue purple brown pink grey orange " +
            "light dark medium cold short brief print prints spring springs room rooms evil " +
            "floor volume ground grounds cover score board linked flag stop growing thought " +
            "slightly post polish french american japanese classical sport paper card cards " +
            "suit struck jean lady handle sweet viewer viewers viewing manual pole billed capped " +
            "skin tone modifier modifiers and big but cut die low new not off old one out red see " +
            "six ten the top two way you mrs aid glass dish dishes frame frames mount decoration " +
            "decorations decorative pushing arm arms index arrow arrows utility beating incoming " +
            "convenience shallow tilted tray slash salute dotted bullet bullets shooting shootings " +
            "machine machines field fields pile piles engine engines worker workers club clubs pool " +
            "pools gesture gestures folding claus department departments passenger passengers " +
            "ceremony ceremonies mechanical suspension suspensions optical aerial ray rays postal " +
            "roller rollers nesting articulated wedge stuffed reminder roasted loudly shaved curl " +
            "performing tropical flying hair hairs motor motors root roots bar bars fallen kitchen " +
            "kitchens triangular clutch clutches revolving pouring hairy bouncing bunny oncoming " +
            "popping tipping safety speaker speakers increasing decreasing feeding cream creams drops " +
            "polo splayed leftward leftwards rightward rightwards spiral spirals straw straws cork corks " +
            "uniform uniforms bottle bottles lamp lamps"
            ).split(' ').toSet()

        private fun usable(word: String) = word.length >= MIN_LENGTH && word !in COMMON

        /**
         * What a typed [word] may be the plural of: thumbs → thumb; cherries → cherry or cherrie;
         * cookies → cooky or cookie; tomatoes → tomato or tomatoe; buses → bus or buse. Only
         * readings that are real words of an emoji name ever match.
         */
        internal fun singulars(word: String): List<String> = buildList {
            if (word.length > 4 && word.endsWith("ies")) add(word.dropLast(3) + "y")
            if (word.length > 4 && (word.endsWith("ses") || word.endsWith("ches") ||
                    word.endsWith("shes") || word.endsWith("xes") || word.endsWith("oes"))) add(word.dropLast(2))
            if (word.length > 3 && word.endsWith("s") && !word.endsWith("ss")) add(word.dropLast(1))
        }

        /**
         * The likely singular of a plural name word (cherries → cherry, glasses → glass), or null.
         * Singular words ending in s (circus, cactus, tennis, anxious) are not plurals.
         */
        private fun singular(word: String): String? = when {
            word.endsWith("us") || word.endsWith("is") -> null
            word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 4 && (word.endsWith("sses") || word.endsWith("ches") ||
                word.endsWith("shes") || word.endsWith("xes")) -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> null
        }
    }
}
