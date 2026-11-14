package com.rheerkens.androidkeyboard.companion

import com.rheerkens.androidkeyboard.companion.screens.VoiceSetupProgress
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.ModelStoreState
import org.junit.Assert.*
import org.junit.Test

class VoiceSetupProgressTest {
    private val tiny = ModelCatalog.byId("whisper-tiny")!!
    private val base = ModelCatalog.byId("whisper-base")!!

    @Test fun consideringAnotherModelDoesNotUndoWorkingVoice() {
        val state = ModelStoreState(installed = setOf(tiny.id), activeId = tiny.id)
        val progress = VoiceSetupProgress(true, state, base)
        assertTrue(progress.ready)
        assertEquals(3, progress.step)
        assertEquals(tiny, progress.activeModel)
        assertTrue(progress.changingModel)
    }

    @Test fun finishingAnotherDownloadMakesVoiceReadyWithoutChangingThePicker() {
        val downloading = ModelStoreState(downloads = mapOf(tiny.id to .8f))
        assertEquals(2, VoiceSetupProgress(true, downloading, base).step)
        val finished = ModelStoreState(installed = setOf(tiny.id), activeId = tiny.id)
        assertEquals(3, VoiceSetupProgress(true, finished, base).step)
    }

    @Test fun revokingTheMicrophoneKeepsTheInstalledModelStepDone() {
        val state = ModelStoreState(installed = setOf(tiny.id), activeId = tiny.id)
        val progress = VoiceSetupProgress(false, state, base)
        assertEquals(1, progress.step)
        assertTrue(progress.modelReady)
        assertFalse(progress.ready)
    }

    @Test fun missingActiveFilesNeverCountAsReady() {
        val progress = VoiceSetupProgress(true, ModelStoreState(activeId = tiny.id), tiny)
        assertEquals(2, progress.step)
        assertFalse(progress.ready)
        assertNull(progress.activeModel)
    }
}
