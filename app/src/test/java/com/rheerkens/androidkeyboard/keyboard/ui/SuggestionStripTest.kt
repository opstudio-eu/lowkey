package com.rheerkens.androidkeyboard.keyboard.ui

import com.rheerkens.androidkeyboard.clipboard.ClipboardContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionStripTest {
    @Test
    fun idleHistoryDoesNotDependOnWordSuggestions() {
        assertTrue(StripState().showsClipHistory(hasClips = true))
        assertTrue(StripState(enabled = false).showsClipHistory(hasClips = true))
    }

    @Test
    fun typingKeepsTheStripForWordSuggestions() {
        for (enabled in listOf(false, true)) {
            assertFalse(StripState(enabled = enabled, typingWord = true).showsClipHistory(hasClips = true))
        }
    }

    @Test
    fun sensitiveSystemClipDoesNotHideExistingHistory() {
        // Sensitive clips are not recorded; the history can still contain recent screenshots.
        val strip = StripState(clipboard = ClipboardContent.Text("Copied text", sensitive = true))
        assertTrue(strip.showsClipHistory(hasClips = true))
    }

    @Test
    fun notesAndDictionaryPromptsKeepPrecedence() {
        assertFalse(StripState(note = StripNote("Saved")).showsClipHistory(hasClips = true))
        assertFalse(StripState(addPrompt = "example").showsClipHistory(hasClips = true))
    }

    @Test
    fun emptyHistoryFallsBackToTheCurrentClip() {
        assertFalse(StripState().showsClipHistory(hasClips = false))
        // The service also empties history and disables suggestions for private editors.
        assertFalse(StripState(enabled = false).showsClipHistory(hasClips = false))
    }
}
