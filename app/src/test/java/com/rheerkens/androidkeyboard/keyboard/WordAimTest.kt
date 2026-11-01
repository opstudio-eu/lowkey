package com.rheerkens.androidkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordAimTest {
    // "The quick fox" on line 1 (y 100–130), "jumps over" on line 2 (y 130–160).
    private val the = WordBox(0, 3, 10f, 100f, 40f, 130f)
    private val quick = WordBox(4, 9, 50f, 100f, 100f, 130f)
    private val fox = WordBox(10, 13, 110f, 100f, 140f, 130f)
    private val jumps = WordBox(14, 19, 10f, 130f, 60f, 160f)
    private val over = WordBox(20, 24, 70f, 130f, 110f, 160f)
    private val words = listOf(the, quick, fox, jumps, over)

    @Test
    fun cutsAtTheStartOfTheWordUnderTheFinger() {
        assertEquals(4, pickCut(words, 52f, 115f, 14f))
        assertEquals(4, pickCut(words, 98f, 101f, 14f))
        assertEquals(14, pickCut(words, 30f, 145f, 14f))
    }

    @Test
    fun aGapBetweenWordsPicksTheCloserOne() {
        assertEquals(0, pickCut(words, 43f, 115f, 14f))
        assertEquals(4, pickCut(words, 48f, 115f, 14f))
    }

    @Test
    fun pastTheEndOfALineKeepsThatLine() {
        assertEquals(14, pickCut(words, 200f, 115f, 14f))
        // Just past the end stays on the last word.
        assertEquals(10, pickCut(words, 150f, 115f, 14f))
    }

    @Test
    fun pastTheLastWordDeletesNothing() {
        assertEquals(CUT_NOTHING, pickCut(words, 200f, 145f, 14f))
    }

    @Test
    fun farFromAnyLinePicksNothing() {
        assertNull(pickCut(words, 30f, 40f, 14f))
        assertNull(pickCut(emptyList(), 30f, 115f, 14f))
    }

    @Test
    fun slightlyAboveALinePicksFromIt() {
        assertEquals(4, pickCut(words, 70f, 92f, 14f))
    }

    @Test
    fun keptCountsFromTheSnapshotStart() {
        // Cursor at 24, all 24 characters in the snapshot.
        assertEquals(4, keptForCut(4, 24, 24))
        assertEquals(24, keptForCut(CUT_NOTHING, 24, 24))
        assertEquals(24, keptForCut(30, 24, 24))
        // Only the last 10 characters were read: a cut before them deletes all 10.
        assertEquals(0, keptForCut(4, 24, 10))
        assertEquals(6, keptForCut(20, 24, 10))
    }

    @Test
    fun countsTheWordsBeingDeleted() {
        assertEquals(2, wordsFrom("The quick fox", 4))
        assertEquals(0, wordsFrom("The quick fox", 13))
        assertEquals(1, wordsFrom("The quick fox ", 9))
        assertEquals(3, wordsFrom("The quick fox", 0))
    }
}
