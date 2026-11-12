package com.rheerkens.androidkeyboard.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionNavigationTest {
    @Test fun legacyShortcutsHaveCanonicalParents() {
        assertEquals(listOf(Destination.MODELS, Destination.SETTINGS_VOICE), destinationPath(Destination.SETTINGS_VOICE))
        assertEquals(listOf(Destination.SETTINGS, Destination.LANGUAGES, Destination.SETTINGS_KEYBOARDS), destinationPath(Destination.SETTINGS_KEYBOARDS))
    }

    @Test fun typingReturnsThroughKeyboardSettings() {
        assertEquals(listOf(Destination.SETTINGS, Destination.SETTINGS_KEYBOARD, Destination.SETTINGS_TYPING), destinationPath(Destination.SETTINGS_TYPING))
    }

    @Test fun helpPagesReturnToTheirSettingsEntryPoint() {
        assertEquals(listOf(Destination.SETTINGS, Destination.TRY_IT), destinationPath(Destination.TRY_IT))
        assertEquals(listOf(Destination.SETTINGS, Destination.DIAGNOSTICS), destinationPath(Destination.DIAGNOSTICS))
    }

    @Test fun rootTabsHaveNoSyntheticBackEntry() {
        for (tab in listOf(Destination.HOME, Destination.MODELS, Destination.DICTIONARY, Destination.SETTINGS)) {
            assertEquals(listOf(tab), destinationPath(tab))
        }
    }

    @Test fun typingReadinessDoesNotRequireSpeech() {
        assertEquals(KeyboardSetupState.NOT_ENABLED, keyboardSetupState(false, false))
        assertEquals(KeyboardSetupState.NOT_ENABLED, keyboardSetupState(false, true))
        assertEquals(KeyboardSetupState.NOT_SELECTED, keyboardSetupState(true, false))
        assertEquals(KeyboardSetupState.READY, keyboardSetupState(true, true))
    }
}
