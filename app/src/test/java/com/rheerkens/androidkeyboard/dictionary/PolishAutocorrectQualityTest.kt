package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Autocorrect and suggestions against the Polish word list the phone downloads (test copy in `src/test/resources/dict/`). */
class PolishAutocorrectQualityTest {
    private val lexicon = TestLanguages.lexicon("pl")
    private val suggester = Suggester { lexicon }

    private fun check(expected: Map<String, String>) {
        val wrong = expected.mapNotNull { (typed, want) ->
            val actual = Corrector.autocorrect(typed, lexicon)
            if (actual == want) null else "$typed -> $actual (want $want)"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun wordsTypedWithoutPolishLettersGetThem() {
        check(
            mapOf(
                "sie" to "się", "prosze" to "proszę", "jezyk" to "język",
                "zolw" to "żółw", "zrodlo" to "źródło", "wlasnie" to "właśnie", "moze" to "może",
                "bedzie" to "będzie", "juz" to "już", "wiec" to "więc", "tez" to "też", "ktory" to "który",
                "zeby" to "żeby", "miedzy" to "między", "pozniej" to "później", "dzien" to "dzień",
                "gdzies" to "gdzieś", "ksiazka" to "książka",
                "zolty" to "żółty", "pieniadze" to "pieniądze",
            ),
        )
    }

    @Test
    fun commonPolishTyposAreCorrected() {
        check(
            mapOf(
                "kótry" to "który", "wsyztko" to "wszystko", "jsetem" to "jestem", "klawaitura" to "klawiatura",
                "telfon" to "telefon", "samohcód" to "samochód", "zawsez" to "zawsze",
            ),
        )
    }

    @Test
    fun formsThatDifferOnlyInAccentsPickARealWord() {
        // `dziekuje` fits both "I thank" and "he thanks"; the word list ranks the latter higher.
        assertTrue(Corrector.autocorrect("dziekuje", lexicon) in setOf("dziękuję", "dziękuje"))
    }

    @Test
    fun accentlessSpellingsInTheWordListAreRestoredOnlyWhenFarRarer() {
        // The AOSP list also holds `juz` and `wiec`, from text typed without Polish letters.
        assertTrue(lexicon.contains("juz") && lexicon.contains("wiec"))
        assertEquals("już", lexicon.accentRestoration("juz"))
        assertEquals("Więc", Corrector.autocorrect("Wiec", lexicon, atSentenceStart = true))
        // Real words whose accented form is not much more common stay: piec (oven), rak, sad.
        listOf("piec", "rak", "sad", "pies").forEach { assertNull(it, Corrector.autocorrect(it, lexicon)) }
        // Words typed with accents are never changed, and the rule is off without a margin.
        assertNull(lexicon.accentRestoration("już"))
        val plain = TestLanguages.language("pl").let { l ->
            TestLanguages.dictFile("pl").inputStream().use { Lexicon.parseGzip(it, l.keys, SpellingRules()) }
        }
        assertNull(Corrector.autocorrect("juz", plain))
    }

    @Test
    fun correctPolishWordsAreLeftAlone() {
        val keep = listOf(
            "się", "jest", "może", "morze", "nie", "kot", "dom", "ale", "że", "już", "który", "żółw",
            "źródło", "dziękuję", "proszę", "zamek", "rok", "mam", "pies", "las",
        )
        val changed = keep.mapNotNull { w -> Corrector.autocorrect(w, lexicon)?.let { "$w -> $it" } }
        assertTrue(changed.joinToString(), changed.isEmpty())
    }

    @Test
    fun englishAndNamesMixedIntoPolishAreKept() {
        listOf("ok", "kotlin", "api", "github", "lol").forEach { assertNull(it, Corrector.autocorrect(it, lexicon)) }
        assertNull(Corrector.autocorrect("Kasia", lexicon, atSentenceStart = false))
    }

    @Test
    fun capitalsAreKeptWhenAddingPolishLetters() {
        assertEquals("Książka", Corrector.autocorrect("Ksiazka", lexicon, atSentenceStart = true))
        // All capitals look like code or an acronym, so they are left alone in every language.
        assertNull(Corrector.autocorrect("ZOLW", lexicon))
    }

    @Test
    fun completionsIgnoreMissingPolishLetters() {
        val dzie = lexicon.completions("dzie", 10).map { it.first }
        assertTrue(dzie.toString(), "dzień" in dzie || "dzięki" in dzie)
        val zol = lexicon.completions("zol", 20).map { it.first }
        assertTrue(zol.toString(), zol.any { it.startsWith("żół") })
        // A prefix typed with Polish letters only matches words spelled that way.
        assertTrue(lexicon.completions("żó", 20).all { it.first.lowercase().startsWith("żó") })
    }

    @Test
    fun suggestionsOfferTheWordWithPolishLetters() {
        val s = suggester.suggest("prosze", emptyList()).map { it.text }
        assertTrue(s.toString(), "proszę" in s)
        assertEquals("proszę", suggester.autocorrection("prosze", emptyList()))
    }

    @Test
    fun polishCorrectionIsFastEnough() {
        val words = listOf("dziekuje", "prosze", "zolw", "wlasnie", "ksiazka", "pieniadze", "porsze", "jsetem", "telfon", "zawsez")
        words.forEach { Corrector.corrections(it, lexicon) } // warm up
        val start = System.nanoTime()
        repeat(3) { words.forEach { Corrector.corrections(it, lexicon) } }
        val perWordMs = (System.nanoTime() - start) / 1e6 / (3 * words.size)
        val limitMs = if (System.getenv("CI") == null) 25.0 else 50.0
        assertTrue("per word $perWordMs ms (limit $limitMs ms)", perWordMs < limitMs)
    }
}
