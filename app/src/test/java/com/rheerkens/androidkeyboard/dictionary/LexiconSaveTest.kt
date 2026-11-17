package com.rheerkens.androidkeyboard.dictionary

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A saved word list ([Lexicon.write]) loads back with every answer unchanged. */
class LexiconSaveTest {
    private fun save(lexicon: Lexicon, tag: String = "v1|test"): ByteArray =
        ByteArrayOutputStream().also { lexicon.write(it, tag) }.toByteArray()

    private fun load(bytes: ByteArray, language: KeyboardLanguage, tag: String = "v1|test") =
        Lexicon.read(ByteArrayInputStream(bytes), tag, language.folding, language.rules)

    @Test fun shippedListsAnswerTheSameAfterSavingAndLoading() {
        for (id in listOf("en_US", "nl", "pl")) {
            val language = TestLanguages.language(id)
            val original = TestLanguages.lexicon(id)
            val bytes = save(original)
            val loaded = load(bytes, language)!!
            assertEquals(id, original.size, loaded.size)
            val words = ArrayList<String>()
            original.forEachCandidate(1..30, accept = { true }) { word, _, _ -> words += word }
            val sample = words.filterIndexed { i, _ -> i % 50 == 0 } + listOf("THE", "polish", "zolw", "dziekuje", "ideeen", "xyzzy")
            for (w in sample) {
                assertEquals("$id $w", original.frequency(w), loaded.frequency(w))
                assertEquals("$id $w", original.spelling(w), loaded.spelling(w))
                assertEquals("$id $w", original.containsExact(w), loaded.containsExact(w))
                assertEquals("$id $w", original.accentRestoration(w), loaded.accentRestoration(w))
                assertEquals("$id $w", original.completions(w.take(3), 6), loaded.completions(w.take(3), 6))
            }
            for (typo in listOf("teh", "recieve", "mischien", "dziekuje", "keybaord")) {
                assertEquals("$id $typo", Corrector.corrections(typo, original, 5), Corrector.corrections(typo, loaded, 5))
            }
            assertEquals(id, original.whitelist, loaded.whitelist)
            // Saving the loaded list gives the same bytes: nothing is lost on the way.
            assertTrue(id, bytes.contentEquals(save(loaded)))
        }
    }

    @Test fun aCopySavedFromAnotherSourceIsNotUsed() {
        val language = TestLanguages.language("en_US")
        val bytes = save(Lexicon.fromWords(listOf("hello", "world")), tag = "old-install|en_US|bundled")
        assertNull(load(bytes, language, tag = "new-install|en_US|bundled"))
        assertNull(load("not a word list".toByteArray(), language))
    }

    @Test fun aDamagedCopyFailsLoudlySoItIsRebuilt() {
        val language = TestLanguages.language("en_US")
        val bytes = save(Lexicon.fromWords(listOf("hello", "world", "Kotlin")))
        assertThrows(Exception::class.java) { load(bytes.copyOf(bytes.size / 2), language) }
    }
}
