package com.rheerkens.androidkeyboard.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveLanguageTest {
    private fun model(id: String) = ModelCatalog.byId(id)!!

    @Test
    fun modelsWithoutAChoiceResolveToNull() {
        assertNull(model("whisper-tiny.en").resolveLanguage("auto"))
    }

    @Test
    fun largeWhisperModelsHaveTheRightLanguagePolicy() {
        // Distil-Large v3.5 uses the multilingual tokenizer but is English only.
        val distil = model("whisper-distil-large-v3.5")
        assertTrue((distil.engine as EngineSpec.SherpaWhisper).englishOnly)
        assertNull(distil.resolveLanguage("nl"))
        val turbo = model("whisper-turbo")
        assertFalse((turbo.engine as EngineSpec.SherpaWhisper).englishOnly)
        assertEquals("nl", turbo.resolveLanguage("nl")?.code)
        assertTrue((model("whisper-small.en").engine as EngineSpec.SherpaWhisper).englishOnly)
    }

    @Test
    fun preferredLanguageIsUsedWhenOffered() {
        assertEquals("nl", model("whisper-base").resolveLanguage("nl")?.code)
        assertEquals("auto", model("whisper-base").resolveLanguage("auto")?.code)
    }

    @Test
    fun unsupportedPreferenceFallsBackToEnglishThenFirst() {
        assertEquals("en", model("whisper-base").resolveLanguage("xx")?.code)
        val noEnglish = model("whisper-base").copy(languages = listOf(SpeechLanguage("de", "German"), SpeechLanguage("fr", "French")))
        assertEquals("de", noEnglish.resolveLanguage("xx")?.code)
    }

    @Test
    fun anyWhisperLanguageWorksForKeyboardLanguagesOutsideTheModelList() {
        // Greek is not in the models' short list, but a keyboard language may dictate in it.
        val greek = model("whisper-base").resolveLanguage("el")
        assertEquals("el", greek?.code)
        assertEquals("Greek", greek?.label)
        assertNull(model("whisper-base.en").resolveLanguage("el"))
    }
}
