package com.rheerkens.androidkeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class KeyboardSettingsTest {
    private val defaults = Settings()
    private val nlAzerty = TypingKeyboard(TypingLayout.NL_NL, KeyArrangement.AZERTY)

    @Test
    fun idsRoundTripAndBareLanguagesMeanQwerty() {
        for (keyboard in TypingKeyboard.ALL) assertEquals(keyboard, TypingKeyboard.parse(keyboard.id))
        assertEquals(TypingKeyboard(TypingLayout.NL_NL), TypingKeyboard.parse("NL_NL"))
        assertNull(TypingKeyboard.parse("FR_FR:AZERTY"))
        assertNull(TypingKeyboard.parse("EN_US:BEPO"))
    }

    @Test
    fun languageOnlyPreferencesMigrateToQwerty() {
        val (enabled, current) = SettingsStore.loadKeyboards(setOf("NL_NL"), "NL_NL", defaults)
        assertEquals(setOf(TypingKeyboard(TypingLayout.NL_NL)), enabled)
        assertEquals(TypingKeyboard(TypingLayout.NL_NL), current)
    }

    @Test
    fun storedKeyboardsLoadAndCurrentIsAlwaysEnabled() {
        val ids = setOf("EN_US:QWERTY", "NL_NL:AZERTY", "XX:QWERTY")
        assertEquals(setOf(TypingKeyboard.DEFAULT, nlAzerty) to nlAzerty, SettingsStore.loadKeyboards(ids, "NL_NL:AZERTY", defaults))
        // A current keyboard that isn't enabled falls back to the default, then to the first enabled.
        assertEquals(TypingKeyboard.DEFAULT, SettingsStore.loadKeyboards(ids, "EN_US:DVORAK", defaults).second)
        assertEquals(nlAzerty, SettingsStore.loadKeyboards(setOf("NL_NL:AZERTY"), null, defaults).second)
        // Nothing usable: the defaults.
        assertEquals(defaults.enabledKeyboards to defaults.currentKeyboard, SettingsStore.loadKeyboards(setOf("??"), "??", defaults))
        assertEquals(defaults.enabledKeyboards to defaults.currentKeyboard, SettingsStore.loadKeyboards(null, null, defaults))
    }

    @Test
    fun toggleKeepsOneKeyboardAndMovesTheCurrentOne() {
        val withAzerty = defaults.toggleKeyboard(nlAzerty).copy(currentKeyboard = nlAzerty)
        assertEquals(3, withAzerty.enabledKeyboards.size)
        val off = withAzerty.toggleKeyboard(nlAzerty)
        assertEquals(TypingKeyboard.DEFAULT, off.currentKeyboard)
        val only = Settings(enabledKeyboards = setOf(nlAzerty), currentKeyboard = nlAzerty)
        assertSame(only, only.toggleKeyboard(nlAzerty))
    }
}
