package com.rheerkens.androidkeyboard.keyboard

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class EmojiCatalogTest {
    private val source = File("src/main/assets/emoji/emoji-test.txt").readText()
    private val catalog = EmojiCatalog.parse(source)

    @Test fun allQualifiedSequencesAreAvailableWithNamesAndCategories() {
        val count = source.lineSequence().count { it.contains("; fully-qualified") }
        assertTrue(catalog.entries.size > 1500)
        assertEquals(count, catalog.entries.flatMap { it.variants }.map { it.value }.distinct().size)
        assertEquals("grinning face", catalog.find("😀")?.name)
        assertEquals("Smileys & Emotion", catalog.find("😀")?.category)
        assertNotNull(catalog.find("👩‍💻"))
        assertNotNull(catalog.find("🇧🇪"))
    }

    @Test fun skinTonesStayWithTheirBaseEmojiAndUnsupportedGlyphsAreFiltered() {
        assertEquals(6, catalog.find("👍")!!.variants.size)
        assertTrue(catalog.find("👍")!!.variants.any { it.value == "👍🏽" })
        val filtered = catalog.supported { it == "😀" || it == "👍🏽" }
        assertEquals(setOf("😀", "👍🏽"), filtered.entries.map { it.value }.toSet())
        assertNull(filtered.find("👍"))
    }

    @Test fun recentChoicesAreUniqueBoundedAndPreserveWholeSequences() {
        var recent = emptyList<String>()
        repeat(50) { recent = EmojiHistory.record(recent, catalog.entries[it].value) }
        assertEquals(40, recent.size)
        recent = EmojiHistory.record(recent, "👩🏽‍💻")
        recent = EmojiHistory.record(recent, "👩🏽‍💻")
        assertEquals(40, recent.size)
        assertEquals(1, recent.count { it == "👩🏽‍💻" })
        assertEquals(recent, EmojiHistory.decode(EmojiHistory.encode(recent)))
        assertEquals("👩🏽‍💻", catalog.suffixOf("Hello 👩🏽‍💻"))
        assertEquals(recent, EmojiHistory.record(recent, "😀", remember = false))
    }
}
