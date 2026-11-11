package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.keyboard.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-language rules from `languages.txt`, on small word lists (downloaded lists are not in the repo). */
class LanguageRulesTest {
    private val german = TestLanguages.language("de")
    private val de = Lexicon.fromWords(
        listOf("der", "die", "und", "Zeit", "haben", "heute", "essen", "Essen", "schön", "Straße", "über", "Häuser", "Strasse", "Hauser"),
        folding = german.folding, rules = german.rules,
    )

    @Test
    fun germanCapitalizesNounsTypedInLowercase() {
        assertTrue(german.rules.capitalizedNouns)
        assertEquals("Zeit", Corrector.autocorrect("zeit", de))
        assertEquals("Zeit", Corrector.autocorrect("ziet", de))
        assertEquals("Straße", Corrector.autocorrect("strasse", de))
        // A more frequent spelling with umlauts or ß beats a rarer plain one (the surname Hauser).
        assertEquals("Häuser", Corrector.autocorrect("hauser", de))
        // A word that is also spelled in lowercase (the verb `essen`) is left alone.
        assertNull(Corrector.autocorrect("essen", de))
        assertNull(Corrector.autocorrect("heute", de))
    }

    @Test
    fun germanUmlautsAreKeysNotAccents() {
        // ü has its own key on the German layout, so `uber` is a real typo, not a missing accent.
        assertEquals("uber", german.folding.fold("uber"))
        assertEquals("über", german.folding.fold("über"))
        val keys = Layouts.keyGrid(TestLanguages.keyboard("de"))
        assertTrue(keys.adjacent('p', 'ü'))
        // ...but leaving out the umlaut still costs no more than a neighbouring key.
        assertTrue(KeyGrid.sameBaseLetter('u', 'ü'))
        assertEquals("über", Corrector.autocorrect("uber", de, keys = keys))
        // German on an alternative layout without umlaut keys (QWERTY) still finds it.
        assertEquals("über", Corrector.autocorrect("uber", de, keys = Layouts.keyGrid(TestLanguages.keyboard("de:QWERTY"))))
    }

    @Test
    fun otherLanguagesNeverCapitalizeALowercaseWord() {
        val english = Lexicon.fromWords(listOf("the", "Lil", "lot"))
        assertFalse(english.rules.capitalizedNouns)
        assertNull(Corrector.autocorrect("lil", english))
    }

    @Test
    fun onlyPolishRestoresAccentsOfKnownWordsAndOnlyGermanCapitalizesNouns() {
        val restoring = TestLanguages.catalog.languages.filter { it.rules.accentMargin > 0 }.map { it.id }
        assertEquals(listOf("pl"), restoring)
        val nouns = TestLanguages.catalog.languages.filter { it.rules.capitalizedNouns }.map { it.id }
        assertEquals(listOf("de"), nouns)
    }
}
