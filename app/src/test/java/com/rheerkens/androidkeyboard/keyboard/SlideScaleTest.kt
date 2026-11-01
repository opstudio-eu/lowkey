package com.rheerkens.androidkeyboard.keyboard

import org.junit.Assert.*
import org.junit.Test

class SlideScaleTest {
    private fun words(n: Int) = BackspaceScrub((1..n).joinToString(" ") { "w$it" }, "")

    /** Band widths (dp) of the word levels 1 until top. */
    private fun bands(scale: SlideScale) = (1 until scale.top).map { scale.startOf(it + 1) - scale.startOf(it) }

    @Test
    fun deadZoneDeletesNothing() {
        val scale = SlideScale(words(200).top, 320f)
        assertEquals(0, scale.levelAt(0f))
        assertEquals(0, scale.levelAt(SlideScale.DEAD - 0.1f))
        assertEquals(1, scale.levelAt(SlideScale.DEAD))
    }

    @Test
    fun longTextStillGivesTheFirstWordAFullBand() {
        for (n in listOf(20, 200, 2000)) {
            val scale = SlideScale(words(n).top, 320f)
            assertEquals("first word, $n words", SlideScale.FIRST, scale.startOf(2) - scale.startOf(1), 0.01f)
        }
    }

    @Test
    fun shortTextSpreadsWiderBands() {
        val scale = SlideScale(words(5).top, 320f)
        val b = bands(scale)
        assertEquals(4, b.size)
        assertTrue("first band ${b[0]}", b[0] > SlideScale.FIRST)
        // Every band is at least as wide as the one above it.
        for (i in 1 until b.size) assertTrue(b[i] <= b[i - 1] + 0.01f)
        assertEquals(scale.allStart, scale.startOf(4) + b[3], 0.01f)
    }

    @Test
    fun bandsNeverWidenGoingUpAndEndAtClearAll() {
        for (n in listOf(2, 3, 4, 5, 6, 7, 8, 12, 40, 200, 2000)) for (travel in listOf(60f, 120f, 150f, 180f, 240f, 320f)) {
            val scale = SlideScale(words(n).top, travel)
            val b = bands(scale)
            assertTrue(b.all { it >= 0f })
            for (i in 1 until b.size) assertTrue("$n words, $travel dp, band $i", b[i] <= b[i - 1] + 0.01f)
            assertEquals(scale.allStart, scale.startOf(scale.top), 0.01f)
            if (b.isNotEmpty()) assertEquals(scale.allStart, scale.startOf(scale.top - 1) + b.last(), 0.05f)
            if (travel >= 120f) assertEquals(travel - SlideScale.ALL, scale.allStart, 0.01f)
        }
    }

    @Test
    fun levelsRiseMonotonicallyAndTheTopClearsAll() {
        val scale = SlideScale(words(300).top, 320f)
        var last = 0
        for (i in 0..640) {
            val level = scale.levelAt(i / 2f)
            assertTrue(level >= last)
            last = level
        }
        assertEquals(scale.top, scale.levelAt(scale.allStart))
        assertEquals(scale.top, scale.levelAt(320f))
        assertTrue(scale.levelAt(scale.allStart - 0.01f) < scale.top)
        for (level in 0..scale.top) assertEquals(level, scale.levelAt(scale.startOf(level) + 1e-3f).coerceAtLeast(level))
    }

    @Test
    fun longTextSpeedsUpTowardTheTop() {
        val scale = SlideScale(words(300).top, 320f)
        // Halfway up, a handful of words; most of the text goes in the top part.
        assertTrue(scale.levelAt(160f) < 40)
        assertTrue(scale.levelAt(scale.allStart - 10f) > 150)
    }

    @Test
    fun shortTravelShrinksTheFirstBandButKeepsItWidest() {
        val scale = SlideScale(words(200).top, 90f)
        val b = bands(scale)
        val span = scale.allStart - scale.dead
        assertEquals(SlideScale.FIRST_SHARE * span, b[0], 0.05f)
        assertTrue(b.all { it <= b[0] + 0.01f })
    }

    @Test
    fun hysteresisHoldsTheCurrentWordNearABoundary() {
        val scale = SlideScale(words(200).top, 320f)
        val boundary = scale.startOf(2)
        // Just past the boundary the first word stays; further on the second goes.
        assertEquals(1, scale.levelAt(boundary + 1f, 1))
        assertEquals(2, scale.levelAt(boundary + SlideScale.HYSTERESIS + 0.1f, 1))
        // And back down: the second word stays until the finger is clearly below it.
        assertEquals(2, scale.levelAt(boundary - 1f, 2))
        assertEquals(1, scale.levelAt(boundary - SlideScale.HYSTERESIS - 0.1f, 2))
        // A jump far past applies at once.
        assertEquals(scale.top, scale.levelAt(310f, 1))
        // No current level yet: no hysteresis.
        assertEquals(2, scale.levelAt(boundary + 1f, -1))
    }

    @Test
    fun singleLevelAndEmpty() {
        assertEquals(0, SlideScale(0, 320f).levelAt(300f))
        val one = SlideScale(1, 320f)
        assertEquals(0, one.levelAt(100f))
        assertEquals(1, one.levelAt(300f))
    }

    @Test
    fun fiveWordsOnAShortSliderDoNotSlowDownAtTheTop() {
        // Review counterexample: 5 words at 180 dp gave 35.2, 23.5, 20.5, then 52.8 dp.
        val b = bands(SlideScale(words(5).top, 180f))
        assertEquals(4, b.size)
        assertTrue("$b", b.last() <= b.first())
    }

    @Test
    fun startsStayInOrderForEveryCountAndTravel() {
        // Review counterexample: 3897 levels at 50 dp put the last word just past clear-all.
        for (top in listOf(0, 1, 2, 3, 5, 9, 40, 400, 3897, 5001)) for (travel in 1..320 step 7) {
            val scale = SlideScale(top, travel.toFloat())
            for (l in 1..top) assertTrue("$top levels, $travel dp, level $l", scale.startOf(l) >= scale.startOf(l - 1))
        }
    }

    @Test
    fun aShortRailFromTheFanStillReachesTheFirstWordAndClearAll() {
        // Entering the slider high up the fan can leave very little travel.
        for (travel in listOf(2f, 10f, 30f, 60f)) {
            val scale = SlideScale(words(50).top, travel)
            assertEquals(0, scale.levelAt(0f, 0))
            assertEquals("$travel dp", 1, scale.levelAt((scale.startOf(1) + scale.startOf(2)) / 2, 0))
            assertEquals(scale.top, scale.levelAt(travel, 1))
        }
    }

    @Test
    fun bothKeyboardsEndAtTheSameHeight() {
        // The minimized bar's key sits at the bottom: the full 4 cm.
        assertEquals(256f, SlideScale.travelFor(48f, null), 0.01f)
        // The full keyboard's Backspace (two 48 dp rows and 4 dp padding up) loses its extra height.
        val full = SlideScale.travelFor(100f, null)
        assertEquals(204f, full, 0.01f)
        assertEquals(48f + 256f, 100f + full, 0.01f)
        // The room on screen still caps it, and it never vanishes.
        assertEquals(150f, SlideScale.travelFor(48f, 150f), 0.01f)
        assertEquals(1f, SlideScale.travelFor(1000f, null), 0.01f)
    }

    @Test
    fun theFirstWordGoesSoonAndThePaceChangesGradually() {
        // Review of 0.4.35: about 7 mm of slide before the first word, then 48, 32 and 28 dp bands
        // followed by much narrower ones. Now each band is a little narrower than the one before.
        for (n in listOf(15, 30, 80, 300, 2000)) for (travel in listOf(232f, 284f)) {
            val scale = SlideScale(words(n).top, travel)
            assertEquals(SlideScale.DEAD, scale.startOf(1), 0.01f)
            val b = bands(scale)
            assertTrue("$n words, $travel dp: ${b.take(4)}", b[0] <= 2 * SlideScale.FIRST)
            // Never a sudden change of pace: next band at least half the one below it (most of the
            // time far more), and over the first ten words gradually narrower.
            for (i in 1 until b.size) assertTrue("$n words, $travel dp, band $i: ${b[i - 1]} -> ${b[i]}", b[i] >= 0.5f * b[i - 1])
            if (n <= 80) for (i in 1 until minOf(10, b.size)) assertTrue("$n words, band $i", b[i] >= 0.7f * b[i - 1])
        }
    }
}
