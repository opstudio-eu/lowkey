package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.keyboard.Key
import com.rheerkens.androidkeyboard.keyboard.Layouts
import com.rheerkens.androidkeyboard.models.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LanguageCatalogTest {
    private val catalog = TestLanguages.catalog

    @Test
    fun shippedCatalogBundlesEnglishDutchAndPolishOnly() {
        assertEquals(listOf("en_US", "nl", "pl"), catalog.languages.filter { it.bundled }.map { it.id })
        assertEquals("en_US", catalog.default.id)
        // Only bundled word lists may sit in the APK's assets; the rest are downloaded.
        val assets = File("src/main/assets/dict").list()!!.filter { it.endsWith(".dict") }.toSet()
        assertEquals(catalog.languages.filter { it.bundled }.map { it.dictionaryFile }.toSet(), assets)
    }

    @Test
    fun everyLanguageHasAPinnedHttpsSourceAndThreeRows() {
        catalog.languages.forEach { l ->
            assertTrue(l.id, l.sourceUrl.startsWith("https://android.googlesource.com/") && l.sourceUrl.endsWith("${l.id}_wordlist.combined.gz?format=TEXT"))
            assertTrue(l.id, "/+/127336e9f29d69607eab55982324b210279ae8c5/" in l.sourceUrl)
            assertEquals(l.id, 3, l.rows.size)
            assertTrue(l.id, l.shortLabel.length in 2..3)
        }
    }

    @Test
    fun everySpeechCodeIsAWhisperLanguage() {
        catalog.languages.forEach { l -> assertTrue("${l.id} -> ${l.speech}", l.speech in ModelCatalog.WHISPER_CODES) }
        assertEquals(99, ModelCatalog.WHISPER_CODES.size)
        // The models' short list is a subset.
        assertTrue(ModelCatalog.byId("whisper-base")!!.languages.drop(1).all { it.code in ModelCatalog.WHISPER_CODES })
    }

    @Test
    fun everyLayoutHasEqualKeyWidthsInEachLetterRow() {
        catalog.languages.forEach { l ->
            val rows = Layouts.letters(l).rows.take(3)
            val widths = rows.map { row -> row.sumOf { it.weight.toDouble() } }
            widths.forEach { assertEquals(l.id, widths[0], it, 1e-6) }
            assertTrue(l.id, rows.flatten().filterIsInstance<Key.Char>().all { it.weight == 1f })
        }
    }

    @Test
    fun polishLettersAreOneLongPressAwayAndListedFirst() {
        val keys = Layouts.letters(TestLanguages.language("pl")).rows.flatten().filterIsInstance<Key.Char>().associateBy { it.label }
        assertEquals(listOf("ą", "á"), keys.getValue("a").accents.take(2))
        assertEquals("ć", keys.getValue("c").accents.first())
        assertEquals("ę", keys.getValue("e").accents.first())
        assertEquals(listOf("ł"), keys.getValue("l").accents)
        assertEquals("ń", keys.getValue("n").accents.first())
        assertEquals("ó", keys.getValue("o").accents.first())
        assertEquals("ś", keys.getValue("s").accents.first())
        assertEquals(listOf("ż", "ź"), keys.getValue("z").accents.take(2))
        // Holding the top row still types digits first.
        assertEquals(listOf("3", "ę"), keys.getValue("e").alternates.take(2))
    }

    @Test
    fun everyBundledLanguagesCommonLettersAreTypable() {
        catalog.languages.filter { it.bundled }.forEach { l ->
            val keys = Layouts.letters(l).rows.flatten().filterIsInstance<Key.Char>()
            val typable = keys.flatMap { listOf(it.label) + it.accents }.toSet()
            // The 20,000 most frequent words (the file is sorted by frequency).
            val missing = File("src/main/assets/dict/${l.dictionaryFile}").inputStream().use { input ->
                java.util.zip.GZIPInputStream(input).bufferedReader().lineSequence().take(20_000)
                    .map { it.substringBefore('\t').lowercase() }
                    .flatMap { w -> w.asSequence().filter(Char::isLetter).map { it.toString() } }
                    .filter { it !in typable }.toSet()
            }
            assertTrue("${l.id}: $missing", missing.isEmpty())
        }
    }

    @Test
    fun otherLayoutsMatchTheirLanguage() {
        fun top(id: String) = TestLanguages.language(id).rows[0]
        assertEquals("qwertzuiopü", top("de"))
        assertEquals("azertyuiop", top("fr"))
        assertEquals("qwertyuiopå", top("sv"))
        assertEquals("йцукенгшщзх", top("ru"))
        val german = Layouts.letters(TestLanguages.language("de")).rows[0].filterIsInstance<Key.Char>()
        assertEquals("0", german[9].longPressOutput)
        assertNull(german[10].longPressOutput) // ü has no digit
    }

    @Test
    fun keyGridFoldsOnlyAccentsWithoutTheirOwnKey() {
        val pl = TestLanguages.language("pl").keys
        assertEquals("zolw", pl.fold("żółw"))
        assertEquals("zrodlo", pl.fold("źródło"))
        assertEquals("dziekuje", pl.fold("dziękuję"))
        val sv = TestLanguages.language("sv").keys
        assertEquals("här", sv.fold("här")) // ä is a key of its own in Swedish
        assertEquals("cafe", sv.fold("café"))
        val ru = TestLanguages.language("ru").keys
        assertEquals("мой", ru.fold("мой")) // й is not и with a breve on a Russian keyboard
        assertEquals("еж", ru.fold("ёж"))
        assertEquals("sarbatoare", TestLanguages.language("ro").keys.fold("sărbătoare"))
    }

    @Test
    fun keyGridNeighboursFollowTheLayout() {
        val qwerty = KeyGrid.QWERTY
        assertTrue(qwerty.adjacent('q', 'w'))
        assertTrue(qwerty.adjacent('a', 'z'))
        assertFalse(qwerty.adjacent('q', 'p'))
        assertTrue(qwerty.adjacent('ż', 'x')) // an accented letter sits on its base key
        val azerty = TestLanguages.language("fr").keys
        assertTrue(azerty.adjacent('a', 'z'))
        assertTrue(azerty.adjacent('q', 's'))
        assertFalse(azerty.adjacent('q', 'w') && azerty.adjacent('q', 'p'))
        val qwertz = TestLanguages.language("de").keys
        assertTrue(qwertz.adjacent('t', 'z'))
        assertTrue(qwertz.adjacent('l', 'ö'))
        assertFalse(qwertz.adjacent('t', 'y'))
    }

    @Test
    fun invalidCatalogsAreRejected() {
        val good = """
            source = https://example.org/dictionaries
            [xx]
            name = X
            english = X
            short = XX
            rows = abc def ghi
            bundled = false
            size = 10
            sha256 = ${"a".repeat(64)}
        """.trimIndent()
        assertEquals("https://example.org/dictionaries/xx_wordlist.combined.gz?format=TEXT", LanguageCatalog.parse(good).languages[0].sourceUrl)
        assertNull(LanguageCatalog.parse(good).languages[0].speech)
        listOf(
            good.replace("source = https://", "source = http://"),
            good.replace("rows = abc def ghi", "rows = abc def"),
            good.replace("rows = abc def ghi", "rows = abc dea ghi"),
            good.replace("[xx]", "[Xx1]"),
            good.replace("a".repeat(64), "abc"),
            good.replace("bundled = false", "bundled = maybe"),
            good.replace("size = 10", "size = -1"),
            good.replace("name = X\n", ""),
            good + "\n" + good.substringAfter("\n"),
        ).forEach { bad -> assertThrows(IllegalArgumentException::class.java) { LanguageCatalog.parse(bad) } }
    }
}
