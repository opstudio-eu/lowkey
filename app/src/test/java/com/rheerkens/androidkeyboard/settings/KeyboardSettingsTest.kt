package com.rheerkens.androidkeyboard.settings

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSettingsTest {
    private val catalog = TestLanguages.catalog
    private val defaults = Settings()

    @Test
    fun idsRoundTripAndBareLanguagesMeanTheirOwnLayout() {
        for (keyboard in TypingKeyboard.all(catalog)) assertEquals(keyboard, TypingKeyboard.parse(keyboard.id, catalog))
        assertEquals(TypingKeyboard(catalog["nl"]!!), TypingKeyboard.parse("nl", catalog))
        assertNull(TypingKeyboard.parse("xx:AZERTY", catalog))
        assertNull(TypingKeyboard.parse("en_US:BEPO", catalog))
    }

    @Test
    fun aLanguagesOwnLayoutIsNeverOfferedTwice() {
        // English is QWERTY, so `en_US:QWERTY` is just English; German's own QWERTZ has Ü, Ö and Ä keys.
        assertEquals("en_US", TypingKeyboard.parse("en_US:QWERTY", catalog)?.id)
        assertEquals("de", TypingKeyboard.parse("de:QWERTZ", catalog)?.id)
        val german = TypingKeyboard.options(catalog["de"]!!).map { it.id }
        assertEquals(listOf("de", "de:QWERTY", "de:AZERTY", "de:DVORAK", "de:COLEMAK"), german)
        // Russian is not in the Latin alphabet: only its own layout.
        assertEquals(listOf("ru"), TypingKeyboard.options(catalog["ru"]!!).map { it.id })
        assertTrue(TypingKeyboard.all(catalog).map { it.id }.let { it.size == it.toSet().size })
    }

    @Test
    fun storedKeyboardsOfEarlierVersionsMigrate() {
        // 0.5.3 and older: language names only. 0.5.5: language and arrangement.
        assertEquals("nl", SettingsStore.keyboardId("NL_NL"))
        assertEquals("en_US", SettingsStore.keyboardId("EN_US:QWERTY"))
        assertEquals("nl:AZERTY", SettingsStore.keyboardId("NL_NL:AZERTY"))
        assertEquals("pl:DVORAK", SettingsStore.keyboardId("pl:DVORAK"))
        val (enabled, current) = SettingsStore.loadKeyboards(setOf("EN_US:QWERTY", "NL_NL:AZERTY"), "NL_NL:AZERTY", defaults)
        assertEquals(setOf("en_US", "nl:AZERTY"), enabled)
        assertEquals("nl:AZERTY", current)
        assertEquals("nl" to "nl", SettingsStore.loadKeyboards(setOf("NL_NL"), "NL_NL", defaults).let { it.first.single() to it.second })
    }

    @Test
    fun theCurrentKeyboardIsAlwaysAnEnabledOne() {
        val ids = setOf("en_US", "nl:AZERTY")
        // A current keyboard that isn't enabled falls back to the default, then to an enabled one.
        assertEquals("en_US", SettingsStore.loadKeyboards(ids, "en_US:DVORAK", defaults).second)
        assertEquals("nl:AZERTY", SettingsStore.loadKeyboards(setOf("nl:AZERTY"), null, defaults).second)
        // Nothing stored: the defaults (English, built in).
        assertEquals(setOf("en_US") to "en_US", SettingsStore.loadKeyboards(null, null, defaults))
        assertEquals(setOf("en_US") to "en_US", SettingsStore.loadKeyboards(emptySet(), null, defaults))
    }

    @Test
    fun toggleKeepsOneUsableKeyboardAndMovesTheCurrentOne() {
        val usable = listOf("en_US", "en_US:AZERTY", "nl", "pl")
        val withAzerty = defaults.toggleKeyboard("en_US:AZERTY", usable).copy(currentKeyboard = "en_US:AZERTY")
        assertEquals(setOf("en_US", "en_US:AZERTY"), withAzerty.enabledKeyboards)
        val off = withAzerty.toggleKeyboard("en_US:AZERTY", usable)
        assertEquals("en_US", off.currentKeyboard)
        assertFalse("en_US:AZERTY" in off.enabledKeyboards)
        // The last usable keyboard stays on, even when one whose list isn't downloaded is enabled.
        val only = Settings(enabledKeyboards = setOf("pl", "sv"), currentKeyboard = "pl")
        assertSame(only, only.toggleKeyboard("pl", usable))
    }
}
