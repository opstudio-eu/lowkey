package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class PhraseRuleApplierTest {
    private val seed = listOf(
        PhraseRule(1, "three code", "T3 Code"),
        PhraseRule(2, "t3 code", "T3 Code"),
        PhraseRule(3, "view model", "ViewModel"),
        PhraseRule(4, "a d b", "adb"),
    )

    @Test
    fun specPhrases() {
        assertEquals(
            PhraseRuleApplier.Result("refactor the ViewModel so the repository is injected", 1),
            PhraseRuleApplier.apply("refactor the view model so the repository is injected", seed),
        )
        assertEquals(
            PhraseRuleApplier.Result("run the T3 Code lint task and show me the errors", 1),
            PhraseRuleApplier.apply("run the t3 code lint task and show me the errors", seed),
        )
    }

    @Test
    fun caseInsensitiveAndWhitespaceTolerant() {
        val r = PhraseRuleApplier.apply("Use A  D B and Three Code", seed)
        assertEquals("Use adb and T3 Code", r.text)
        assertEquals(2, r.appliedCount)
    }

    @Test
    fun respectsWordBoundaries() {
        assertEquals("abview models", PhraseRuleApplier.apply("abview models", seed).text)
        assertEquals("the view modeler", PhraseRuleApplier.apply("the view modeler", seed).text)
    }

    @Test
    fun disabledRulesAreSkipped() {
        val rules = seed.map { if (it.id == 3L) it.copy(enabled = false) else it }
        assertEquals("the view model", PhraseRuleApplier.apply("the view model", rules).text)
    }

    @Test
    fun appliesOncePerMatchWithoutChaining() {
        val rules = listOf(PhraseRule(1, "alpha", "beta"), PhraseRule(2, "beta", "gamma"))
        val r = PhraseRuleApplier.apply("alpha beta", rules)
        assertEquals("beta gamma", r.text)
        assertEquals(2, r.appliedCount)
    }

    @Test
    fun longerPhraseWins() {
        val rules = listOf(PhraseRule(1, "code", "CODE"), PhraseRule(2, "t3 code", "T3 Code"))
        assertEquals("T3 Code and CODE", PhraseRuleApplier.apply("t3 code and code", rules).text)
    }

    @Test
    fun noRulesReturnsInput() {
        assertEquals(PhraseRuleApplier.Result("hello", 0), PhraseRuleApplier.apply("hello", emptyList()))
    }
}
