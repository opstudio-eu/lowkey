package com.rheerkens.androidkeyboard.dictionary

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CorrectionCancellationTest {
    @Test fun cancelledSearchStopsInsideTheCandidateScan() {
        val lexicon = Lexicon.fromWords((0 until 2000).map { "word$it" })
        var checkpoints = 0
        try {
            Corrector.corrections("wrod123", lexicon, checkActive = {
                checkpoints++
                if (checkpoints == 3) throw CancellationException()
            })
            fail("Obsolete work ran to completion")
        } catch (_: CancellationException) {
            assertEquals(3, checkpoints)
        }
    }

    @Test fun bothSuggesterOperationsRespectCancellation() {
        val suggester = Suggester(listOf("word", "world"))
        val cancelled = { throw CancellationException() }
        for (operation in listOf<() -> Unit>(
            { suggester.suggest("wrod", emptyList(), checkActive = cancelled) },
            { suggester.autocorrection("wrod", emptyList(), checkActive = cancelled) },
        )) {
            try { operation(); fail("Ignored cancellation") } catch (_: CancellationException) { }
        }
    }
}
