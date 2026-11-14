package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.Suggestion
import com.rheerkens.androidkeyboard.dictionary.Suggester
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EmojiSuggestionsTest {
    private val catalog = EmojiCatalog(listOf(
        EmojiItem("👋", "waving hand", "People", listOf(EmojiVariant("👋", "waving hand"))),
        EmojiItem("☕", "hot beverage", "Food", listOf(EmojiVariant("☕", "hot beverage"))),
    ))

    @Test fun exactGreetingsUseTheExistingSupportedCatalog() {
        assertEquals("👋", EmojiSuggestions.suggest("Hi", listOf("en_US"), catalog)?.text)
        assertEquals("👋", EmojiSuggestions.suggest("hoi", listOf("nl"), catalog)?.text)
        assertEquals("☕", EmojiSuggestions.suggest("koffie", listOf("nl"), catalog)?.text)
        assertEquals("☕", EmojiSuggestions.suggest("coffee", listOf("en_GB"), catalog)?.text)
    }

    @Test fun ambiguousPrefixesAndDisabledLanguagesOfferNoEmoji() {
        for (word in listOf("h", "he", "hel", "shelloworld")) {
            assertNull(EmojiSuggestions.suggest(word, listOf("en_US", "nl"), catalog))
        }
        assertNull(EmojiSuggestions.suggest("coffee", listOf("nl"), catalog))
        assertNull(EmojiSuggestions.suggest("hoi", listOf("en_US"), catalog))
    }

    @Test fun unsupportedOrUnloadedEmojiAreNotOffered() {
        assertNull(EmojiSuggestions.suggest("hi", listOf("en_US"), null))
        assertNull(EmojiSuggestions.suggest("hi", listOf("en_US"), catalog.supported { it != "👋" }))
    }

    @Test fun emojiKeepsTheLiteralWordAndBestCompletionInPlace() {
        val emoji = EmojiSuggestions.suggest("hi", listOf("en_US"), catalog)
        val slots = Suggester.arrangeForStrip("hi", listOf(Suggestion("high"), Suggestion("hint")), emoji = emoji)
        assertEquals(listOf("high", "hi", "👋"), slots.map { it?.text })
        assertEquals("waving hand", slots[2]?.emojiName)
        assertTrue(slots[1]!!.isLiteral)
    }

    @Test fun emojiNeverChangesWhatSpaceWillAutocorrect() {
        val emoji = EmojiSuggestions.suggest("hi", listOf("en_US"), catalog)
        val slots = Suggester.arrangeForStrip("hi", listOf(Suggestion("his")), Suggestion("his"), emoji)
        assertEquals(listOf("hi", "his", "👋"), slots.map { it?.text })
        assertNull(slots[1]?.emojiName)
    }

    @Test fun emojiDoesNotDisplaceASavedPersonalTerm() {
        val emoji = EmojiSuggestions.suggest("hi", listOf("en_US"), catalog)
        val slots = Suggester.arrangeForStrip("hi", listOf(Suggestion("Hikari", isPersonal = true),
            Suggestion("Hildegard", isPersonal = true)), emoji = emoji)
        assertEquals(listOf("Hikari", "hi", "Hildegard"), slots.map { it?.text })
    }

    private val unicode by lazy { EmojiCatalog.parse(File("src/main/assets/emoji/emoji-test.txt").readText()) }
    private fun en(word: String) = EmojiSuggestions.suggest(word, listOf("en_US"), unicode)?.text

    @Test fun englishWordsFromEmojiNamesSuggestThatEmoji() {
        assertEquals("👍", en("thumb"))
        assertEquals("👍", en("Thumbs"))
        assertEquals("thumbs up", EmojiSuggestions.suggest("thumb", listOf("en_US"), unicode)?.emojiName)
        assertEquals("🥞", en("pancakes"))
        assertEquals("🥞", en("pancake"))
        assertEquals("🍒", en("cherries"))
        assertEquals("🦄", en("unicorn"))
        assertEquals("🪨", en("stones"))
        assertEquals("🎪", en("circus"))
        assertEquals("🌵", en("cactus"))
        assertEquals("🗺️", en("maps"))
        // The word names the picture: last in the name, or with only generic words around it.
        assertEquals("🥩", en("meat"))
        assertEquals("👏", en("clapping"))
        assertEquals("🤷", en("shrugging"))
        assertEquals("👕", en("shirts"))
        assertEquals("👟", en("shoes"))
        assertEquals("🏮", en("lantern"))
        assertEquals("🧢", en("caps"))
        assertEquals("💃", en("dancer"))
        assertEquals("😷", en("mask"))
        assertEquals("👆", en("finger"))
        assertEquals("🤔", en("thinking"))
        assertEquals("👓", en("glasses"))
        assertEquals("🥚", en("eggs"))
        // A word's own emoji beats a longer name containing its plural ("money with wings").
        assertEquals("🪽", en("wings"))
        // The word itself beats a plural reading of another name ("houses").
        assertEquals("🏠", en("house"))
        assertEquals("🏘️", en("houses"))
    }

    @Test fun aliasesStillComeBeforeEmojiNames() {
        assertEquals("🐶", en("dog"))
        assertEquals("💧", en("water"))
        assertEquals("👍", EmojiSuggestions.suggest("duim", listOf("nl"), unicode)?.text)
    }

    @Test fun commonWordsShortWordsSymbolsAndFlagsOfferNoEmoji() {
        for (word in listOf("with", "face", "open", "right", "left", "from", "have", "this", "that", "time",
            "good", "make", "people", "hands", "black", "light", "watch", "note", "stop", "back", "short",
            "glass", "dishes", "bullets", "shootings", "machines", "fields", "piles", "clauses", "department", "passengers", "ceremony", "mechanical", "rays", "postal", "loudly", "hair", "kitchen", "medical", "steam", "cooked", "military", "musical", "electric", "milky", "backhand", "feeding", "cream", "drops", "polo", "leftward", "spiral", "straws", "cork", "uniform", "bottles", "lamp", "jordan", "georgia", "chad", "new", "free", "soon", "top", "cool", "egg", "eye", "dump",
            "news", "ways", "ones", "sees", "dies", "offs", "billed", "capped", "skin", "tone", "aids")) {
            assertNull(word, en(word))
        }
    }

    @Test fun emojiNamesAreEnglishOnly() {
        assertNull(EmojiSuggestions.suggest("thumb", listOf("nl"), unicode))
        assertEquals("👍", EmojiSuggestions.suggest("thumb", listOf("nl", "en_GB"), unicode)?.text)
    }

    @Test fun emojiNamesOnlyOfferEmojiTheDeviceCanShow() {
        assertNull(EmojiSuggestions.suggest("unicorn", listOf("en_US"), unicode.supported { it != "🦄" }))
        // A keyword whose emoji is missing does not fall through to a weaker name match.
        val noRock = unicode.supported { it != "🪨" }
        assertNull(EmojiSuggestions.suggest("stone", listOf("en_US"), noRock))
        assertNull(EmojiSuggestions.suggest("stones", listOf("en_US"), noRock))
        val older = unicode.supported { it != "🪽" && it != "🛞" }
        for (word in listOf("wing", "wings", "wheel", "wheels")) {
            assertNull(word, EmojiSuggestions.suggest(word, listOf("en_US"), older))
        }
    }

    @Test fun pluralsFindTheirSingular() {
        assertEquals(listOf("thumb"), EmojiNameIndex.singulars("thumbs"))
        assertTrue("cookie" in EmojiNameIndex.singulars("cookies"))
        assertTrue("cherry" in EmojiNameIndex.singulars("cherries"))
        assertTrue("glass" in EmojiNameIndex.singulars("glasses"))
        assertTrue("box" in EmojiNameIndex.singulars("boxes"))
        assertEquals(emptyList<String>(), EmojiNameIndex.singulars("grass"))
        for ((word, emoji) in listOf("cookies" to "🍪", "zombies" to "🧟", "neckties" to "👔",
            "cherries" to "🍒", "cherry" to "🍒", "unicorns" to "🦄")) {
            assertEquals(word, emoji, en(word))
        }
    }

    @Test fun oesAndSesPluralsFindTheirSingular() {
        assertEquals("🍅", en("tomatoes"))
        assertEquals("🥔", en("potatoes"))
        assertEquals("🦟", en("mosquitoes"))
        assertEquals("🚌", en("buses"))
        assertEquals("🚐", en("minibuses"))
    }

    @Test fun onlyRealSingularsOfNameWordsBecomeKeywords() {
        // "fries" and "glasses" and "cherries" must not turn "frie", "glasse" or "cherrie" into keywords,
        // or typing "friend" would offer fries on the way.
        for (word in listOf("frie", "glasse", "cherrie", "friend", "cooky", "circu", "ferri", "cactu", "tenni", "anxiou")) assertNull(word, en(word))
    }

    @Test fun pluralsOfAliasesKeepTheAlias() {
        for ((singular, plural) in listOf("car" to "cars", "computer" to "computers", "moon" to "moons",
            "water" to "waters", "dog" to "dogs", "flower" to "flowers")) {
            assertEquals(plural, en(singular), en(plural))
        }
        // Dutch aliases do not use English plural rules.
        assertNull(EmojiSuggestions.suggest("autos", listOf("nl"), unicode))
    }
}
