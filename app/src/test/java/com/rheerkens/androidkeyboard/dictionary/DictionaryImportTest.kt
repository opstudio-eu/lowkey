package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryImportTest {
    @Test fun aBackupAddsOnlyWhatIsNotSavedYet() {
        val current = DictionaryState(
            terms = listOf(PersonalTerm(1, "Kotlin", 100)),
            rules = listOf(PhraseRule(2, "new line", "\n")),
        )
        val (merged, terms, rules) = current.withImported(
            terms = listOf(PersonalTerm(0, "Kotlin", 5), PersonalTerm(0, " Lowkey ", 200), PersonalTerm(0, "", 1),
                PersonalTerm(0, "x".repeat(DictionaryStore.MAX_TERM_LENGTH + 1), 1), PersonalTerm(0, "Lowkey", 300)),
            rules = listOf(PhraseRule(0, "new line", "\n"), PhraseRule(0, "smiley", ":)", enabled = false), PhraseRule(0, " ", "x")),
            firstId = 3,
        )
        assertEquals(1, terms)
        assertEquals(1, rules)
        assertEquals(listOf(PersonalTerm(1, "Kotlin", 100), PersonalTerm(3, "Lowkey", 200)), merged.terms)
        assertEquals(listOf(PhraseRule(2, "new line", "\n"), PhraseRule(4, "smiley", ":)", enabled = false)), merged.rules)
    }

    @Test fun importingTheSameBackupTwiceAddsNothing() {
        val backup = listOf(PersonalTerm(0, "ViewModel", 1))
        val once = DictionaryState().withImported(backup, emptyList(), 1).first
        assertEquals(once, once.withImported(backup, emptyList(), 2).first)
    }
}
