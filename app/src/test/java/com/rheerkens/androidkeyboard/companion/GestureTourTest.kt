package com.rheerkens.androidkeyboard.companion

import com.rheerkens.androidkeyboard.keyboard.KeyboardGesture.CursorMoved
import com.rheerkens.androidkeyboard.keyboard.KeyboardGesture.WordsDeleted
import com.rheerkens.androidkeyboard.voice.VoiceSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTourTest {
    @Test fun theCursorStepWantsASlideSidewaysAndOneUpOrDown() {
        var p = TourProgress()
        assertEquals(listOf(false, false), p.goals.map { it.done })
        p = p.on(CursorMoved(-2, 0, selecting = false))
        assertFalse("two characters is a twitch", p.goals[0].done)
        p = p.on(CursorMoved(-1, 0, selecting = false))
        assertEquals(listOf(true, false), p.goals.map { it.done })
        assertFalse(p.done)
        p = p.on(CursorMoved(0, -1, selecting = false))
        assertTrue(p.done)
    }

    @Test fun onlyWhatTheStepAsksForCounts() {
        // Selecting isn't moving, and deleting words isn't either.
        val cursor = TourProgress().on(CursorMoved(-5, 1, selecting = true)).on(WordsDeleted(2)).onDictated(4)
        assertEquals(TourProgress(), cursor)
        val select = TourProgress(TourStep.SELECT).on(CursorMoved(-5, 0, selecting = false))
        assertFalse(select.done)
        assertTrue(select.on(CursorMoved(-2, 0, selecting = true)).done)
    }

    @Test fun deletingAndDictatingFinishTheirSteps() {
        assertFalse(TourProgress(TourStep.DELETE).on(WordsDeleted(0)).done)
        assertTrue(TourProgress(TourStep.DELETE).on(WordsDeleted(1)).done)
        assertFalse(TourProgress(TourStep.DICTATE).onDictated(0).done)
        assertTrue(TourProgress(TourStep.DICTATE).onDictated(3).done)
    }

    @Test fun stepsFollowEachOtherAndStartAfresh() {
        val done = TourProgress().on(CursorMoved(4, 0, false)).on(CursorMoved(0, 1, false))
        assertEquals(TourProgress(TourStep.SELECT), done.next())
        assertEquals(TourStep.DELETE, TourProgress(TourStep.SELECT).next()?.step)
        assertEquals(TourStep.DICTATE, TourProgress(TourStep.DELETE).next()?.step)
        assertNull(TourProgress(TourStep.DICTATE).next())
    }

    @Test fun everyStepButDictationGivesTextToPracticeOn() {
        for (step in TourStep.entries) {
            assertEquals(step.name, step != TourStep.DICTATE, step.practice.isNotEmpty())
        }
        // The cursor step needs lines to move between.
        assertTrue(TourStep.CURSOR.practice.lines().size >= 3)
    }

    private fun session(last: String, words: Int) = VoiceSessionRecord(1, 0, listOf("listening", "finalizing", last), words, null)

    @Test fun onlyInsertedDictationsCount() {
        assertEquals(5, session("committed", 5).insertedWords())
        assertEquals(5, session("committed (1 rule)", 5).insertedWords())
        assertEquals(0, session("cancelled (switched to keys)", 5).insertedWords())
        assertEquals(0, session("cancelled (cursor moved, words left in field)", 5).insertedWords())
        assertEquals(0, session("no speech", 0).insertedWords())
    }

    @Test fun theTourOpensByItselfOnceAfterSetup() {
        assertTrue(offersTour(cameFromSetup = true, KeyboardSetupState.READY, tourSeen = false))
        // Still setting up, already seen, or the keyboard was on before the app opened.
        assertFalse(offersTour(cameFromSetup = true, KeyboardSetupState.NOT_SELECTED, tourSeen = false))
        assertFalse(offersTour(cameFromSetup = true, KeyboardSetupState.READY, tourSeen = true))
        assertFalse(offersTour(cameFromSetup = false, KeyboardSetupState.READY, tourSeen = false))
    }
}
