package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.companion.screens.downloadSize
import com.rheerkens.androidkeyboard.companion.screens.toggleLanguage
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.models.speechLanguageCode
import com.rheerkens.androidkeyboard.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LanguageSettingsTest {
    private val usable = listOf("en_US", "nl", "pl")

    @Test
    fun togglingKeepsOneUsableLanguageAndMovesTheCurrentOne() {
        val s = Settings(enabledLanguages = setOf("en_US", "pl"), currentLanguage = "pl")
        assertEquals(setOf("en_US", "pl", "nl"), toggleLanguage(s, "nl", usable).enabledLanguages)
        val withoutPolish = toggleLanguage(s, "pl", usable)
        assertEquals(setOf("en_US"), withoutPolish.enabledLanguages)
        assertEquals("en_US", withoutPolish.currentLanguage)
        // The last usable language can't be unchecked, even if a not-yet-downloaded one is enabled.
        val last = Settings(enabledLanguages = setOf("pl", "sv"), currentLanguage = "pl")
        assertSame(last, toggleLanguage(last, "pl", usable))
    }

    @Test
    fun voiceLanguageCanFollowTheKeyboard() {
        val polish = TestLanguages.language("pl")
        assertEquals("pl", speechLanguageCode(Settings.FOLLOW_KEYBOARD, polish))
        assertEquals("nl", speechLanguageCode("nl", polish))
        assertEquals("no", speechLanguageCode(Settings.FOLLOW_KEYBOARD, TestLanguages.language("nb")))
        assertEquals("auto", speechLanguageCode(Settings.FOLLOW_KEYBOARD, polish.copy(speech = null)))
        val base = ModelCatalog.byId("whisper-base")!!
        assertEquals("pl", base.resolveLanguage(speechLanguageCode(Settings.FOLLOW_KEYBOARD, polish))?.code)
        assertEquals("hr", base.resolveLanguage(speechLanguageCode(Settings.FOLLOW_KEYBOARD, TestLanguages.language("hr")))?.code)
        // An English-only model ignores the keyboard language.
        assertEquals(null, ModelCatalog.byId("whisper-base.en")!!.resolveLanguage("pl"))
    }

    @Test
    fun downloadSizeCountsTheBase64Transfer() {
        assertEquals("1.5 MB download", downloadSize(TestLanguages.language("sv")))
        assertEquals("0.4 MB download", downloadSize(TestLanguages.language("sl")))
    }
}
