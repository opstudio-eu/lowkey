package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Autocorrect behaviour against the shipped English word list (`assets/dict/en_US.tsv.gz`). */
class AutocorrectQualityTest {
    private val lexicon = File("src/main/assets/dict/en_US.dict").inputStream().use(Lexicon::parseGzip)
        .withExtraWords(BuiltInWords.ENGLISH + BuiltInWords.TECH, 120)

    @Test
    fun commonTyposAreCorrected() {
        val expected = mapOf(
            "teh" to "the", "Teh" to "The", "recieve" to "receive", "becuase" to "because",
            "definately" to "definitely", "dont" to "don't", "im" to "I'm", "thier" to "their",
            "adress" to "address", "hte" to "the", "taht" to "that", "thsi" to "this", "woudl" to "would",
            "cna" to "can", "jsut" to "just", "tommorow" to "tomorrow", "goign" to "going",
            "fucntion" to "function", "retrun" to "return", "bern" to "been",
        )
        val wrong = expected.filter { (typed, want) -> Corrector.autocorrect(typed, lexicon) != want }
            .map { (typed, want) -> "$typed -> ${Corrector.autocorrect(typed, lexicon)} (want $want)" }
        assertTrue(wrong.joinToString(), wrong.isEmpty())
    }

    @Test
    fun everydayTypingAndReportedTyposAreCorrected() {
        val expected = mapOf(
            "autoxomplete" to "autocomplete", "autocoreecrs" to "autocorrects",
            "keybaord" to "keyboard", "tyoing" to "typing", "messgae" to "message",
            "peopel" to "people", "whne" to "when", "wprks" to "works",
            "suggesrions" to "suggestions", "correcrions" to "corrections",
            "behavse" to "behaves", "versoin" to "version", "alwasy" to "always",
            "somethign" to "something", "evrything" to "everything", "shoudl" to "should",
            "probaly" to "probably", "realy" to "really", "neigbor" to "neighbor",
            "togther" to "together", "beleive" to "believe",
            "dictionry" to "dictionary", "clipbaord" to "clipboard",
            "omputer" to "computer", "acomputer" to "computer",
        )
        val wrong = expected.mapNotNull { (typed, want) ->
            val actual = Corrector.autocorrect(typed, lexicon)
            if (actual == want) null else "$typed -> $actual (want $want)"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun dutchTyposStillUseTheDutchDictionary() {
        val dutch = File("src/test/resources/dict/nl.dict").inputStream().use(Lexicon::parseGzip)
            .withExtraWords(BuiltInWords.ENGLISH + BuiltInWords.TECH, 120)
        val expected = mapOf(
            "wroden" to "worden", "hebbne" to "hebben", "mischien" to "misschien",
            "toetsenbrod" to "toetsenbord", "bednakt" to "bedankt", "mrogen" to "morgen",
        )
        expected.forEach { (typed, want) -> assertEquals(typed, want, Corrector.autocorrect(typed, dutch)) }
        listOf("worden", "hebben", "misschien", "toetsenbord", "bedankt", "morgen").forEach {
            assertNull(it, Corrector.autocorrect(it, dutch))
        }
    }

    @Test
    fun knownCodeAndChatWordsAreLeftAlone() {
        val keep = listOf(
            "their", "form", "from", "kotlin", "gradle", "coroutine", "repo", "github", "ok", "lol", "api",
            "async", "json", "refactor", "viewmodel", "android", "yea", "tailscale", "onnx", "lets", "ill",
        )
        val changed = keep.mapNotNull { w -> Corrector.autocorrect(w, lexicon)?.let { "$w -> $it" } }
        assertTrue(changed.joinToString(), changed.isEmpty())
    }

    @Test
    fun namesAndExactDictionarySpellingsAreKept() {
        assertNull(Corrector.autocorrect("Mira", lexicon, atSentenceStart = false))
        assertNull(Corrector.autocorrect("Bern", lexicon, atSentenceStart = true))
        assertNull(Corrector.autocorrect("ill", lexicon))
        assertEquals("The", Corrector.autocorrect("Teh", lexicon, atSentenceStart = true))
        assertNull(Corrector.autocorrect("Teh", lexicon, atSentenceStart = false))
    }

    @Test
    fun codeLikeTokensAreNeverCorrected() {
        listOf("ViewModel", "snake_case", "v2", "iOS", "camelCase", "x86").forEach {
            assertNull(it, Corrector.autocorrect(it, lexicon))
        }
    }

    @Test
    fun completionsAreRankedAndDistinct() {
        val kot = lexicon.completions("kot", 3).map { it.first }
        assertEquals("kotlin", kot.first())
        assertEquals(kot.size, kot.distinct().size)
    }

    @Test
    fun correctionIsFastEnough() {
        val words = listOf("teh", "recieve", "becuase", "definately", "wierd", "thier", "adress", "goign", "fucntion", "retrun")
        words.forEach { Corrector.corrections(it, lexicon) } // warm up
        val start = System.nanoTime()
        repeat(3) { words.forEach { Corrector.corrections(it, lexicon) } }
        val perWordMs = (System.nanoTime() - start) / 1e6 / (3 * words.size)
        // Shared CI runners are slower and noisier than a dev machine; keep 2x headroom there.
        val limitMs = if (System.getenv("CI") == null) 25.0 else 50.0
        assertTrue("per word $perWordMs ms (limit $limitMs ms)", perWordMs < limitMs)
    }
}
