package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.Suggestion
import com.rheerkens.androidkeyboard.dictionary.Suggester
import org.junit.Assert.*
import org.junit.Test

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
}
