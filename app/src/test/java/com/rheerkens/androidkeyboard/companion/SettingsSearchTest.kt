package com.rheerkens.androidkeyboard.companion

import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.updates.acceptsReleaseUpdates
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun titles(query: String) = searchSettings(query).map { it.title }

    @Test fun blankQueryShowsNothing() {
        assertEquals(emptyList<String>(), titles(""))
        assertEquals(emptyList<String>(), titles("  - "))
    }

    @Test fun findsByTitleIgnoringCaseAccentsAndPunctuation() {
        assertEquals("Number row", titles("number")[0])
        assertEquals("Auto-capitalize", titles("AUTO CAP")[0])
        assertEquals("Auto-capitalize", titles("autocapitalize")[0])
        assertEquals("Theme", titles("thème")[0])
    }

    @Test fun findsBySynonyms() {
        assertEquals("Theme", titles("dark mode")[0])
        assertEquals("Vibrate on key press", titles("haptic")[0])
        assertEquals("Keep model loaded", titles("ram")[0])
        assertTrue("Keyboard layouts" in titles("qwerty"))
    }

    @Test fun everyWordMustMatch() {
        assertEquals(listOf("Vibrate on key press"), titles("key vib"))
        assertEquals(emptyList<String>(), titles("vibrate zebra"))
    }

    @Test fun titleMatchesRankAboveKeywordMatches() {
        val results = titles("spelling")
        assertEquals("Match the spelling of my words", results[0])
        assertTrue("Autocorrect" in results)
    }

    @Test fun locationNamesThePageOrItsParent() {
        val number = settingsIndex.first { it.title == "Number row" }
        assertEquals("Settings › Keyboard", number.location)
        val typing = settingsIndex.first { it.title == "Typing" }
        assertEquals("Settings › Keyboard", typing.location)
        val dictation = settingsIndex.first { it.title == "Stop after silence" }
        assertEquals("Voice › Dictation", dictation.location)
        assertEquals("Voice", settingsIndex.first { it.title == "Voice typing" }.location)
        assertEquals("Dictionary › Speech rules", settingsIndex.first { it.title == "Use speech rules" }.location)
    }

    @Test fun resultsOpenOverTheSettingsTabSoBackReturnsToThem() {
        assertEquals(listOf(Destination.SETTINGS, Destination.SETTINGS_VOICE), searchResultRoute(Destination.SETTINGS_VOICE))
        assertEquals(listOf(Destination.SETTINGS, Destination.DICTIONARY), searchResultRoute(Destination.DICTIONARY))
        assertEquals(listOf(Destination.SETTINGS, Destination.SETTINGS_TYPING), searchResultRoute(Destination.SETTINGS_TYPING))
    }

    @Test fun titlesAreUnique() {
        val duplicates = settingsIndex.groupBy { it.title }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate titles: $duplicates", duplicates.isEmpty())
    }

    private val screens = File("src/main/java/com/rheerkens/androidkeyboard/companion/screens")

    /** The screen file that draws a destination's rows. */
    private fun screenOf(destination: Destination): String = File(screens, when (destination) {
        Destination.SETTINGS_UPDATES -> "UpdateCard.kt"
        Destination.DICTIONARY -> "DictionaryScreen.kt"
        Destination.DIAGNOSTICS -> "DiagnosticsScreen.kt"
        Destination.KEYBOARD_THEME -> "KeyboardThemeScreen.kt"
        Destination.LANGUAGES -> "LanguagesScreen.kt"
        else -> "SettingsScreen.kt"
    }).readText()

    private val rowCall = Regex("""(SwitchRow|ChoiceDialogRow|ActionRow|LinkRow)\(\s*(?:title\s*=\s*)?"([^"]+)"""")

    @Test fun everyHighlightedTitleIsARowOnItsOwnScreen() {
        // A renamed or moved row would otherwise stay findable under its old name and never highlight.
        val missing = settingsIndex.filter { it.highlight }.filterNot { entry ->
            val source = screenOf(entry.destination)
            rowCall.findAll(source).any { it.groupValues[2] == entry.title } ||
                "settingHighlight(\"${entry.title}\")" in source
        }.map { "${it.title} on ${it.destination}" }
        assertTrue("Not a row on its screen: $missing", missing.isEmpty())
    }

    @Test fun everyPageResultIsNamedInTheApp() {
        val source = File("src/main/java/com/rheerkens/androidkeyboard/companion").walk()
            .filter { it.extension == "kt" && it.name != "SettingsSearch.kt" }.joinToString("\n") { it.readText() }
        val missing = settingsIndex.filterNot { it.highlight }
            .filterNot { it.title == it.destination.title || "\"${it.title}\"" in source }.map { it.title }
        assertTrue("Not found in the companion screens: $missing", missing.isEmpty())
    }

    @Test fun everySwitchAndChoiceIsIndexed() {
        val titles = settingsIndex.map { it.title }.toSet()
        val unindexed = screens.listFiles()!!.filter { it.extension == "kt" }
            .flatMap { file -> rowCall.findAll(file.readText()).filter { it.groupValues[1] != "LinkRow" }.map { it.groupValues[2] } }
            .filterNot { it in titles }
            .filterNot { it in variantOnly() }
        assertTrue("Settings missing from the search index: $unindexed", unindexed.isEmpty())
    }

    /** Rows only some builds show, and that only those builds index. */
    private fun variantOnly() = buildSet {
        // Only debug builds offer the bundled test clips.
        if (!BuildConfig.DEBUG) add("Dictation audio")
        // UI Preview builds install updates by hand.
        if (!acceptsReleaseUpdates(BuildConfig.APPLICATION_ID)) add("Check for updates automatically")
    }

    @Test fun variantOnlyRowsFollowTheBuild() {
        val titles = settingsIndex.map { it.title }
        assertEquals(BuildConfig.DEBUG, "Dictation audio" in titles)
        assertEquals(acceptsReleaseUpdates(BuildConfig.APPLICATION_ID), "Check for updates automatically" in titles)
    }

    @Test fun dictionaryResultsNameARealTab() {
        for (entry in settingsIndex.filter { it.destination == Destination.DICTIONARY && it.highlight }) {
            assertTrue(entry.title, entry.section in com.rheerkens.androidkeyboard.companion.screens.DICTIONARY_TABS)
        }
    }

    @Test fun keyboardThemeControlsAreFindable() {
        assertEquals("Colors", titles("accent")[0])
        assertEquals("Advanced colors", titles("space bar label")[0])
        assertEquals("Restore default colors", titles("restore")[0])
        assertEquals("Settings › Appearance › Keyboard theme › Custom", settingsIndex.first { it.title == "Contrast" }.location)
    }
}
