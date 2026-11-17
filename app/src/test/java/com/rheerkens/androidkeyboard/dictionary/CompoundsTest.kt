package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompoundsTest {
    private val dutch = SpellingRules(compoundLinkers = listOf("s", "e", "en"))
    /** Frequent words first: fromWords ranks them 255 down, all above the compound part threshold. */
    private val words = listOf("toetsenbord", "instellingen", "verjaardag", "feest", "thuis", "werk", "dag", "werkdag",
        "klembord", "geschiedenis", "spraak", "herkenning", "boek", "bord", "dagboek")
    private val lexicon = Lexicon.fromWords(words, rules = dutch)

    @Test fun twoCommonWordsMakeAWordWithOrWithoutALinkingLetter() {
        assertTrue(Compounds.isCompound("toetsenbordinstellingen", lexicon))
        assertTrue(Compounds.isCompound("verjaardagsfeest", lexicon))
        assertTrue(Compounds.isCompound("Thuiswerkdag", lexicon))
        assertFalse(Compounds.isCompound("toetsenbrodinstellingen", lexicon))
        // Too short, or a language that doesn't join words.
        assertFalse(Compounds.isCompound("dagbord", lexicon))
        assertFalse(Compounds.isCompound("toetsenbordinstellingen", Lexicon.fromWords(words)))
    }

    @Test fun aSlipInOnePartIsRepaired() {
        val keys = KeyGrid.QWERTY
        assertEquals("toetsenbordinstellingen", Compounds.repair("toetsenbrodinstellingen", lexicon, keys)?.word)
        assertEquals("klembordgeschiedenis", Compounds.repair("klembordgeschiedenus", lexicon, keys)?.word)
        assertEquals("Spraakherkenning", Compounds.repair("Spraakherkennimg", lexicon, keys)?.word)
        // Both parts broken, or parts too short to repair safely.
        assertNull(Compounds.repair("toetsenbrodinstelingen", lexicon, keys))
        assertNull(Compounds.repair("dagboekk", lexicon, keys))
    }

    @Test fun autocorrectKeepsCompoundsAndRepairsThem() {
        val suggester = Suggester({ lexicon })
        assertNull(suggester.autocorrection("verjaardagsfeest", emptyList()))
        assertNull(suggester.autocorrection("toetsenbordinstellingen", emptyList()))
        assertEquals("toetsenbordinstellingen", suggester.autocorrection("toetsenbrodinstellingen", emptyList()))
    }

    @Test fun onlyDutchJoinsWordsInTheCatalog() {
        assertEquals(listOf("s", "e", "en"), TestLanguages.language("nl").rules.compoundLinkers)
        assertNull(TestLanguages.language("en_US").rules.compoundLinkers)
        assertNull(TestLanguages.language("pl").rules.compoundLinkers)
    }
}
