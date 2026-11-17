package com.rheerkens.androidkeyboard.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionNavigationTest {
    @Test fun pagesOpenedFromTheKeyboardHaveCanonicalParents() {
        assertEquals(listOf(Destination.MODELS, Destination.SETTINGS_VOICE), destinationPath(Destination.SETTINGS_VOICE))
        assertEquals(listOf(Destination.SETTINGS, Destination.LANGUAGES), destinationPath(Destination.LANGUAGES))
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
        assertEquals(listOf(Destination.SETTINGS, Destination.ABOUT), destinationPath(Destination.ABOUT))
    }

    @Test fun theTourReturnsToHome() {
        assertEquals(listOf(Destination.HOME, Destination.TOUR), destinationPath(Destination.TOUR))
        assertEquals(Destination.HOME, tabOf(Destination.TOUR))
    }

    @Test fun rootTabsHaveNoSyntheticBackEntry() {
        for (tab in listOf(Destination.HOME, Destination.MODELS, Destination.DICTIONARY, Destination.SETTINGS)) {
            assertEquals(listOf(tab), destinationPath(tab))
        }
    }

    @Test fun aLinkKeepsThePageItWasOnForBack() {
        // My words on the Keyboard page opens the Dictionary tab; Back returns to Keyboard, not out of the app.
        val keyboard = listOf(Destination.SETTINGS, Destination.SETTINGS_KEYBOARD)
        assertEquals(keyboard + Destination.DICTIONARY, linkedRoute(keyboard, Destination.DICTIONARY))
        // From Home, Back returns to Home instead of to Settings.
        assertEquals(listOf(Destination.HOME, Destination.LANGUAGES), linkedRoute(listOf(Destination.HOME), Destination.LANGUAGES))
        val dictation = listOf(Destination.MODELS, Destination.SETTINGS_VOICE)
        assertEquals(dictation + Destination.SPEECH_MODELS, linkedRoute(dictation, Destination.SPEECH_MODELS))
    }

    @Test fun aLinkToAPageBelowReturnsToIt() {
        // No loop of pages that link to each other.
        val stack = listOf(Destination.MODELS, Destination.SPEECH_MODELS, Destination.SETTINGS_VOICE)
        assertEquals(listOf(Destination.MODELS, Destination.SPEECH_MODELS), linkedRoute(stack, Destination.SPEECH_MODELS))
        assertEquals(listOf(Destination.MODELS), linkedRoute(stack, Destination.MODELS))
    }

    @Test fun thePageShowsItsOwnTabInTheBottomBar() {
        assertEquals(Destination.DICTIONARY, tabOf(Destination.DICTIONARY))
        assertEquals(Destination.MODELS, tabOf(Destination.SETTINGS_VOICE))
        assertEquals(Destination.SETTINGS, tabOf(Destination.KEYBOARD_THEME))
    }

    @Test fun pagesThatWereMergedOpenThePageTheyAreOnNow() {
        // A back stack saved by an older version, or a link in one of its notifications.
        assertEquals(Destination.SETTINGS_KEYBOARD, Destination.named("SETTINGS_TYPING"))
        assertEquals(Destination.SETTINGS_KEYBOARD, Destination.named("SETTINGS_CUSTOMIZE"))
        assertEquals(Destination.LANGUAGES, Destination.named("SETTINGS_KEYBOARDS"))
        assertEquals(Destination.SETTINGS_VOICE, Destination.named("SETTINGS_STORAGE"))
        assertEquals(Destination.ABOUT, Destination.named("SETTINGS_UPDATES"))
        assertEquals(Destination.ABOUT, Destination.named("DIAGNOSTICS"))
        assertEquals(Destination.HOME, Destination.named("HOME"))
        assertNull(Destination.named("NO_SUCH_PAGE"))
    }

    @Test fun aSavedBackStackSurvivesMergedPages() {
        assertEquals(
            listOf(Destination.SETTINGS, Destination.SETTINGS_KEYBOARD),
            restoredRoute(listOf("SETTINGS", "SETTINGS_KEYBOARD", "SETTINGS_TYPING")),
        )
        assertEquals(
            listOf(Destination.SETTINGS, Destination.LANGUAGES),
            restoredRoute(listOf("SETTINGS", "LANGUAGES", "SETTINGS_KEYBOARDS")),
        )
        assertEquals(listOf(Destination.HOME), restoredRoute(listOf("NO_SUCH_PAGE")))
    }

    @Test fun typingReadinessDoesNotRequireSpeech() {
        assertEquals(KeyboardSetupState.NOT_ENABLED, keyboardSetupState(false, false))
        assertEquals(KeyboardSetupState.NOT_ENABLED, keyboardSetupState(false, true))
        assertEquals(KeyboardSetupState.NOT_SELECTED, keyboardSetupState(true, false))
        assertEquals(KeyboardSetupState.READY, keyboardSetupState(true, true))
    }
}
