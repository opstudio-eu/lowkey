package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.companion.screens.downloadSize
import com.rheerkens.androidkeyboard.companion.screens.moreLanguagesSummary
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.models.speechLanguageCode
import com.rheerkens.androidkeyboard.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSettingsTest {
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

    @Test
    fun moreLanguagesNamesOnlyWhatIsNotOnThePhone() {
        val all = TestLanguages.catalog.languages.filter { !it.bundled }.map { it.englishName }
        assertEquals("Download English (UK), Dutch, Polish and 17 more, or delete languages", moreLanguagesSummary(all))
        assertEquals("Download German or French, or delete languages", moreLanguagesSummary(listOf("German", "French")))
        assertEquals("Download German, or delete languages", moreLanguagesSummary(listOf("German")))
        assertEquals("Every language is on the phone; delete the ones you don't use", moreLanguagesSummary(emptyList()))
    }
}
