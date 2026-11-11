package com.rheerkens.androidkeyboard.companion.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.R
import com.rheerkens.androidkeyboard.settings.Settings
import com.rheerkens.androidkeyboard.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun systemIsTheDefault() {
        assertEquals(ThemeMode.SYSTEM, Settings().theme)
        assertEquals(listOf("System", "Light", "Dark"), ThemeMode.entries.map { it.label })
    }

    @Test
    fun systemFollowsAndroidWhileLightAndDarkOverrideIt() {
        assertTrue(resolveDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(resolveDark(ThemeMode.SYSTEM, systemDark = false))
        for (system in listOf(true, false)) {
            assertFalse(resolveDark(ThemeMode.LIGHT, system))
            assertTrue(resolveDark(ThemeMode.DARK, system))
        }
    }

    @Test
    fun logoVariantMatchesTheBackground() {
        assertEquals(R.drawable.opstudio_logo_blue, opStudioLogo(dark = false))
        assertEquals(R.drawable.opstudio_logo_light_blue, opStudioLogo(dark = true))
        assertNotEquals(opStudioLogo(dark = false), opStudioLogo(dark = true))
    }

    @Test
    fun schemesUseTheBrandColours() {
        assertTrue(DarkScheme.isDark)
        assertFalse(LightScheme.isDark)
        assertEquals(Brand.Blue, LightScheme.primary)
        assertEquals(Brand.Light, LightScheme.background)
        assertEquals(Brand.Dark, LightScheme.onBackground)
        assertEquals(Brand.LightBlue, DarkScheme.primary)
        assertEquals(Brand.Dark, DarkScheme.background)
        assertEquals(Brand.Dark, DarkScheme.onPrimary)
    }

    @Test
    fun lightSchemeTextMeetsWcagAa() = assertReadable("light", LightScheme)

    @Test
    fun darkSchemeTextMeetsWcagAa() = assertReadable("dark", DarkScheme)

    /** Every text colour on every surface the companion screens put it on. */
    private fun assertReadable(name: String, s: ColorScheme) {
        val surfaces = mapOf(
            "background" to s.background,
            "surface" to s.surface,
            "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest,
            "surfaceVariant" to s.surfaceVariant,
        )
        val textOnSurfaces = mapOf(
            "onSurface" to s.onSurface,
            "onSurfaceVariant" to s.onSurfaceVariant,
            "primary" to s.primary,
            "secondary" to s.secondary,
            "tertiary" to s.tertiary,
            "error" to s.error,
        )
        val failures = mutableListOf<String>()
        fun check(fg: String, f: Color, bg: String, b: Color, min: Double) {
            val ratio = contrast(f, b)
            if (ratio < min) failures += "$fg on $bg: ${"%.2f".format(ratio)} < $min"
        }
        for ((fg, f) in textOnSurfaces) for ((bg, b) in surfaces) check(fg, f, bg, b, AA_TEXT)
        check("onBackground", s.onBackground, "background", s.background, AA_TEXT)
        check("onPrimary", s.onPrimary, "primary", s.primary, AA_TEXT)
        check("onPrimaryContainer", s.onPrimaryContainer, "primaryContainer", s.primaryContainer, AA_TEXT)
        check("onSecondary", s.onSecondary, "secondary", s.secondary, AA_TEXT)
        check("onSecondaryContainer", s.onSecondaryContainer, "secondaryContainer", s.secondaryContainer, AA_TEXT)
        check("onTertiary", s.onTertiary, "tertiary", s.tertiary, AA_TEXT)
        check("onTertiaryContainer", s.onTertiaryContainer, "tertiaryContainer", s.tertiaryContainer, AA_TEXT)
        check("onError", s.onError, "error", s.error, AA_TEXT)
        check("onErrorContainer", s.onErrorContainer, "errorContainer", s.errorContainer, AA_TEXT)
        check("inverseOnSurface", s.inverseOnSurface, "inverseSurface", s.inverseSurface, AA_TEXT)
        // Switch tracks, text field borders and other non-text indicators.
        check("outline", s.outline, "surface", s.surface, AA_NON_TEXT)
        check("outline", s.outline, "surfaceContainer", s.surfaceContainer, AA_NON_TEXT)
        assertTrue("$name scheme:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
