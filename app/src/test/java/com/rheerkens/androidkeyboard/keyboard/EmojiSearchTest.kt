package com.rheerkens.androidkeyboard.keyboard

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EmojiSearchTest {
    private val catalog by lazy {
        val annotations = listOf("en", "nl").associateWith {
            EmojiAnnotations.parse(File("src/main/assets/emoji/keywords-$it.txt").readText())
        }
        EmojiCatalog.parse(File("src/main/assets/emoji/emoji-test.txt").readText()).withAnnotations(annotations)
    }

    private fun find(query: String, vararg languages: String) =
        catalog.search.search(query, languages.toList().ifEmpty { listOf("en_US") }).map { it.value }

    @Test fun aWholeNameComesFirst() {
        assertEquals("🍕", find("pizza").first())
        assertEquals("🐈", find("cat").first())
        assertEquals("❤️", find("red heart").first())
    }

    @Test fun everyTypedWordStartsAWordOfTheNameOrKeywords() {
        assertEquals("❤️", find("red hea").first())
        assertTrue("👍" in find("thumbs").take(2))
        assertTrue(find("thumbs").none { it == "🍕" })
        // A keyword finds it too, not only the name.
        assertTrue("😂" in find("lol").take(3))
    }

    @Test fun pluralsFindTheirSingular() {
        assertTrue("🐈" in find("cats"))
        // As high as the singular: 🚗 car, not 🚍 for the keyword "cars".
        assertEquals("🚗", find("cars").first())
    }

    @Test fun caseAndAccentsDoNotMatter() {
        assertEquals(find("pizza"), find("PIZZA"))
        assertEquals(find("pinata"), find("piñata"))
        assertTrue("🪅" in find("pinata"))
    }

    @Test fun theKeyboardsLanguagesAddTheirNamesAndKeywords() {
        assertEquals("🚲", find("fiets", "nl").first())
        assertTrue("👍" in find("duim", "nl").take(2))
        assertEquals("🇳🇱", find("nederland", "nl").first())
        // English names and keywords always count; Dutch ones only with a Dutch keyboard.
        assertEquals("🚲", find("bicycle", "nl").first())
        assertTrue("🚲" !in find("fiets", "en_US"))
    }

    @Test fun nothingTypedOrNothingFoundGivesNoEmoji() {
        assertTrue(find("").isEmpty())
        assertTrue(find("   ").isEmpty())
        assertTrue(find("qqxqz").isEmpty())
    }

    @Test fun resultsAreCapped() {
        assertTrue(find("face").size <= EmojiSearch.MAX_RESULTS)
    }

    @Test fun annotationsSkipCommentsAndBrokenLines() {
        val parsed = EmojiAnnotations.parse("# header\n🍕\tpizza\tcheese|slice\nbroken\n🚲\tfiets\t\n")
        assertEquals("pizza", parsed.names["🍕"])
        assertEquals(listOf("cheese", "slice"), parsed.keywords["🍕"])
        assertEquals(emptyList<String>(), parsed.keywords["🚲"])
        assertEquals(2, parsed.names.size)
    }

    @Test fun dutchWordsThatAreAWholeEmojiNameSuggestIt() {
        fun nl(word: String) = EmojiSuggestions.suggest(word, listOf("nl"), catalog)?.text
        assertEquals("🚲", nl("fiets"))
        assertEquals("🐘", nl("olifant"))
        assertEquals("🚲", nl("Fiets"))
        // Everyday words stay words; multi-word names and symbols never match.
        assertNull(nl("elf"))
        assertNull(nl("man"))
        assertNull(nl("omhoog"))
        // Names that are everyday English words stay words for people who type both.
        assertNull(nl("mug"))
        assertNull(nl("pet"))
        assertEquals("🪑", nl("stoel"))
        // With Dutch first, the English word beer still gets 🍺, not the Dutch bear.
        assertEquals("🍺", EmojiSuggestions.suggest("beer", listOf("nl", "en_US"), catalog)?.text)
        // The small alias list still comes first.
        assertEquals("🐶", nl("hond"))
    }
}
