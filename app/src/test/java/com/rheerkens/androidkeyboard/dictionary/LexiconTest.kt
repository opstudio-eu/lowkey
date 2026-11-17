package com.rheerkens.androidkeyboard.dictionary

import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The compact word list: lookups by lowercase form, spellings, accents and the search pool. */
class LexiconTest {
    private fun lexicon(text: String, folding: AccentFolding = AccentFolding.QWERTY) =
        Lexicon.parse(text.trimIndent().byteInputStream(), folding)

    @Test fun aWordStoredInSeveralCasesAnswersWithItsMostFrequentSpelling() {
        val lex = lexicon("""
            polish	150
            Polish	200
            Mark	90
            mark	120
            zebra	10
        """)
        assertEquals(5, lex.size)
        assertEquals(200, lex.frequency("POLISH"))
        assertEquals("Polish", lex.spelling("polish"))
        assertEquals("mark", lex.spelling("MARK"))
        assertTrue(lex.contains("Zebra"))
        assertNull(lex.frequency("zebr"))
        assertNull(lex.spelling("zebras"))
        // Exact spellings, and a stored lowercase word capitalized.
        assertTrue(lex.containsExact("polish") && lex.containsExact("Polish") && lex.containsExact("POLISH"))
        assertTrue(lex.containsExact("Zebra"))
        assertFalse(lex.containsExact("zEBRA"))
    }

    @Test fun aTieBetweenSpellingsGoesToTheFirstListed() {
        val lex = lexicon("""
            Bill	100
            bill	100
        """)
        assertEquals("Bill", lex.spelling("bill"))
    }

    @Test fun completionsAreRankedAndIgnoreMissingAccents() {
        val polish = AccentFolding("qwertyuiopasdfghjklzxcvbnm".toSet())
        val lex = lexicon("""
            zolw	40
            żółw	120
            żółty	200
            zoo	90
            zo	250
        """, polish)
        assertEquals(listOf("żółty", "żółw", "zoo", "zolw"), lex.completions("zo", 5).map { it.first })
        // An accent the user typed must match.
        assertEquals(listOf("żółty", "żółw"), lex.completions("żó", 5).map { it.first })
        val forms = ArrayList<String>()
        lex.forEachAccentedForm("zolw") { word, _, _ -> forms += word }
        assertEquals(listOf("zolw", "żółw"), forms)
    }

    @Test fun extraWordsJoinTheListOnce() {
        val lex = lexicon("""
            kotlin	100
        """).withExtraWords(listOf("Kotlin", "Compose", "compose"), 120)
        assertEquals(2, lex.size)
        assertEquals(100, lex.frequency("kotlin"))
        assertEquals("Compose", lex.spelling("compose"))
    }

    @Test fun shippedWordListFrequenciesFitInAByte() {
        for (id in listOf("en_US", "nl", "pl")) {
            GZIPInputStream(TestLanguages.dictFile(id).inputStream()).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line == "#whitelist") break
                    val freq = line.substringAfter('\t', "").toIntOrNull() ?: continue
                    assertTrue("$id: $line", freq in 0..255)
                }
            }
        }
    }
}
