package com.rheerkens.androidkeyboard.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionNavigationTest {
    @Test fun legacyShortcutsHaveCanonicalParents() {
        assertEquals(listOf(Destination.MODELS, Destination.SETTINGS_VOICE), destinationPath(Destination.SETTINGS_VOICE))
        assertEquals(listOf(Destination.SETTINGS, Destination.LANGUAGES, Destination.SETTINGS_KEYBOARDS), destinationPath(Destination.SETTINGS_KEYBOARDS))
    }

    @Test fun typingReturnsThroughKeyboardSettings() {
        assertEquals(listOf(Destination.SETTINGS, Destination.SETTINGS_KEYBOARD, Destination.SETTINGS_TYPING), destinationPath(Destination.SETTINGS_TYPING))
    }

    @Test fun keyboardThemeReturnsThroughAppearance() {
        assertEquals(
            listOf(Destination.SETTINGS, Destination.SETTINGS_APPEARANCE, Destination.KEYBOARD_THEME),
            destinationPath(Destination.KEYBOARD_THEME),
        )
    }

    @Test fun everyDetailPageHasASettingsOrVoiceRoot() {
        // A page that falls through to its own root would lose Back and the selected tab.
        for (destination in Destination.entries.filterNot { it.isTab }) {
            val path = destinationPath(destination)
            assertEquals(destination, path.last())
            assertTrue("$destination has no parent", path.size > 1 && path.first().isTab)
        }
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
