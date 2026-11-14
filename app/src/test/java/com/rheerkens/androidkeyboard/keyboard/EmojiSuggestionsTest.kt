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
        assertEquals("🤔", en("thinking"))
        assertEquals("👓", en("glasses"))
        assertEquals("🥚", en("eggs"))
        // A word's own emoji beats a longer name containing its plural ("money with wings").
        assertEquals("🪽", en("wings"))
    }

    @Test fun aliasesStillComeBeforeEmojiNames() {
        assertEquals("🐶", en("dog"))
        assertEquals("💧", en("water"))
        assertEquals("👍", EmojiSuggestions.suggest("duim", listOf("nl"), unicode)?.text)
    }

    @Test fun commonWordsShortWordsSymbolsAndFlagsOfferNoEmoji() {
        for (word in listOf("with", "face", "open", "right", "left", "from", "have", "this", "that", "time",
            "good", "make", "people", "hands", "black", "light", "watch", "note", "stop", "back", "short",
            "jordan", "georgia", "chad", "new", "free", "soon", "top", "cool", "egg", "eye", "dump")) {
            assertNull(word, en(word))
        }
    }

    @Test fun emojiNamesAreEnglishOnly() {
        assertNull(EmojiSuggestions.suggest("thumb", listOf("nl"), unicode))
        assertEquals("👍", EmojiSuggestions.suggest("thumb", listOf("nl", "en_GB"), unicode)?.text)
    }

    @Test fun emojiNamesOnlyOfferEmojiTheDeviceCanShow() {
        assertNull(EmojiSuggestions.suggest("unicorn", listOf("en_US"), unicode.supported { it != "🦄" }))
    }

    @Test fun pluralsFoldToTheirSingular() {
        assertEquals("thumb", EmojiNameIndex.stem("thumbs"))
        assertEquals("cherry", EmojiNameIndex.stem("cherries"))
        assertEquals("glass", EmojiNameIndex.stem("glasses"))
        assertEquals("box", EmojiNameIndex.stem("boxes"))
        assertEquals("grass", EmojiNameIndex.stem("grass"))
    }
}
