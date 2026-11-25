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
        // An emoji named with the plural itself still comes first.
        assertEquals("🏘️", find("houses").first())
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

    @Test fun winkAndItsEmoticonOfferTheWink() {
        assertEquals("😉", EmojiSuggestions.suggest("wink", listOf("en_US"), catalog)?.text)
        assertEquals("😉", EmojiSuggestions.suggest("knipoog", listOf("nl"), catalog)?.text)
        assertEquals("😉", find(";)").first())
        // The search types lower case: ":D", "xD" and "B)" arrive as ":d", "xd" and "b)".
        assertEquals("😃", find(":d").first())
        assertEquals("😆", find("xd").first())
        assertEquals("😎", find("b)").first())
        assertTrue("😉" in find("wink").take(3))
    }

    private fun face(before: String) = EmojiSuggestions.emoticon(before, catalog)

    @Test fun anEmoticonJustTypedOffersItsEmojiToReplaceIt() {
        val wink = face("See you ;)")!!
        assertEquals("😉", wink.text)
        assertEquals(2, wink.replaceChars)
        assertNotNull(wink.emojiName)
        assertEquals(3, face("ok ;-)")?.replaceChars)
        assertEquals("🙂", face(":)")?.text)
        assertEquals("😃", face("great :D")?.text)
        assertEquals("❤️", face("love you <3")?.text)
        assertEquals("💔", face("</3")?.text)
        assertEquals("😆", face("haha xD")?.text)
        // Straight after a word, faces with eyes still count.
        assertEquals("😉", face("see you;)")?.text)
        // After a tapped suggestion the space moves behind the eyes: "you ;)" arrives as "you; )".
        assertEquals(3, face("See you; )")?.replaceChars)
        assertEquals("😃", face("you: D")?.text)
        assertEquals(3, face("you: D")?.replaceChars)
        assertEquals("😕", face("hmm: /")?.text)
        // After an emoji just tapped, the next emoticon is split the same way.
        assertEquals("🙂", face("😉: )")?.text)
        assertEquals("😆", face("lol xd")?.text)
        assertEquals("😜", face("ha ;-P")?.text)
        assertEquals("😐", face("hmm :-|")?.text)
        assertEquals("😃", face("great :d")?.text)
    }

    @Test fun emoticonLookalikesInTextOfferNothing() {
        assertNull(face("https:/"))
        assertNull(face("taxD"))
        assertNull(face("f(x)"))
        assertNull(face("see you ;) "))
        assertNull(face("3<3"))
        assertNull(face("; )"))
        assertNull(face("a: d"))
        assertNull(EmojiSuggestions.emoticon(";)", null))
    }
}
