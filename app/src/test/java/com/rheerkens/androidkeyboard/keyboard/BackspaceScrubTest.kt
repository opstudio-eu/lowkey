package com.rheerkens.androidkeyboard.keyboard

import org.junit.Assert.*
import org.junit.Test

class BackspaceScrubTest {
    private fun keptTexts(s: BackspaceScrub) = (0..s.top).map { s.before.take(s.kept(it)) }

    @Test
    fun stepsBackOneWordAtATimeThenClearsAll() {
        val s = BackspaceScrub("Hello big  world. ", "")
        assertEquals(listOf("Hello big  world. ", "Hello big  ", "Hello ", ""), keptTexts(s))
        assertTrue(s.topClearsAll)
        assertFalse(s.topIsFinal)
    }

    @Test
    fun topErasesOnlyTheTextBeforeTheCursor() {
        val s = BackspaceScrub("one two", " three")
        // No extra level for the text after the cursor: the top is the last word before it.
        assertEquals(listOf("one two", "one ", ""), keptTexts(s))
        assertFalse(s.topClearsAll)
    }

    @Test
    fun nothingBeforeTheCursorLeavesNothingToSlide() {
        assertEquals(0, BackspaceScrub("", "rest").top)
        assertEquals(0, BackspaceScrub("", "").top)
    }

    @Test
    fun onlyTextPastTheSnapshotsStartMakesTheTopFinal() {
        assertTrue(BackspaceScrub("a b", "", truncatedBefore = true).topIsFinal)
        val longAfter = BackspaceScrub("a b", "c", truncatedAfter = true)
        assertFalse(longAfter.topIsFinal)
        assertFalse(longAfter.topClearsAll)
    }
}
