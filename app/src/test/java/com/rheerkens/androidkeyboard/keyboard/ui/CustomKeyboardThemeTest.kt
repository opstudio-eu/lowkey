package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.companion.theme.AA_TEXT
import com.rheerkens.androidkeyboard.companion.theme.relativeLuminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class CustomKeyboardThemeTest {
    private fun hex(s: String) = parseHexColor(s)!!

    private val forest = KeyboardSeed(
        background = hex("0F2A1D"), keys = hex("1E4632"), functionKeys = hex("163826"),
        text = hex("F1FAF4"), accent = hex("F2B33D"),
    )

    /** A colour per role that no other role has. */
    private fun unique(role: ColorRole) = Color(0xFF000000 or (0x010203L * (role.ordinal + 1)))

    private val image = "bg-0123456789abcdef0123456789abcdef.jpg"

    @Test
    fun everyRoleReadsBackTheFieldItWrote() {
        val palette = keyboardColorsOf(::unique)
        for (role in ColorRole.entries) assertEquals(role.name, unique(role), palette[role])
    }

    @Test
    fun everyPaletteFieldHasExactlyOneRole() {
        val fields = KeyboardColors::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(fields.map { it.name }.toString(), fields.size, ColorRole.entries.size)
        assertEquals(ColorRole.entries.size, ColorRole.entries.map { it.key }.toSet().size)
        for (role in ColorRole.entries) assertEquals(role, ColorRole.ofKey(role.key))
    }

    @Test
    fun seedRolesAreTheFiveSeeds() {
        val colors = deriveKeyboardColors(forest)
        assertEquals(SeedRole.entries.size, ColorRole.entries.count { it.seed != null })
        for (s in SeedRole.entries) assertEquals(forest[s], colors[ColorRole.forSeed(s)])
        assertTrue(ColorRole.overridable.none { it.seed != null })
    }

    @Test
    fun anOverrideChangesOnlyItsRole() {
        val derived = deriveKeyboardColors(forest)
        for (role in ColorRole.overridable) {
            val color = hex("ABCDEF").takeIf { it != derived[role] } ?: hex("123456")
            val colors = CustomKeyboardTheme(forest).with(role, color).effectiveColors()
            for (other in ColorRole.entries) {
                if (other == role) assertEquals(color, colors[other])
                else assertEquals("${role.name} changed ${other.name}", derived[other], colors[other])
            }
        }
    }

    @Test
    fun suggestionTextMatchingTheKeysIsReportedForClipboardChips() {
        // Clipboard chips draw suggestion text on the key colour; found on a device where an
        // override equal to the keys made copied clips invisible without any warning.
        val colors = CustomKeyboardTheme(forest).with(ColorRole.CHIP_TEXT, forest.keys).effectiveColors()
        val failure = keyboardContrastFailures(colors)
            .single { it.foregroundRole == ColorRole.CHIP_TEXT && it.surfaceRole == ColorRole.KEY }
        assertEquals("Clipboard text on clipboard chips: 1.0:1, needs 4.5:1", failure.description)
        assertTrue(keyboardContrastFailures(deriveKeyboardColors(forest)).none { it.surfaceRole == ColorRole.KEY })
    }

    @Test
    fun resetReturnsARoleToItsDerivedColour() {
        val custom = CustomKeyboardTheme(forest).with(ColorRole.MUTED, hex("FF0000")).with(ColorRole.AMBER, hex("00FF00"))
        val reset = custom.with(ColorRole.MUTED, null)
        assertEquals(mapOf(ColorRole.AMBER to hex("00FF00")), reset.overrides)
        assertEquals(deriveKeyboardColors(forest).muted, reset.effectiveColors().muted)
        assertEquals(CustomKeyboardTheme(forest), reset.with(ColorRole.AMBER, null))
    }

    @Test
    fun formerlyFixedStatusColoursCanBeOverridden() {
        val c = CustomKeyboardTheme(forest)
            .with(ColorRole.DANGER, hex("7A1FA2")).with(ColorRole.GREEN, hex("00AAFF")).with(ColorRole.AMBER, hex("FFEE00"))
            .effectiveColors()
        assertEquals(hex("7A1FA2"), c.danger)
        assertEquals(hex("00AAFF"), c.green)
        assertEquals(hex("FFEE00"), c.amber)
    }

    @Test
    fun seedRolesChangeTheSeedNotAnOverride() {
        val t = CustomKeyboardTheme(forest).with(ColorRole.KEY_TEXT, hex("FFFFFF"))
        assertEquals(hex("FFFFFF"), t.seed.text)
        assertTrue(t.overrides.isEmpty())
        // Derived roles follow the new seed.
        assertEquals(deriveKeyboardColors(forest.with(SeedRole.TEXT, hex("FFFFFF"))), t.effectiveColors())
        assertThrows(IllegalArgumentException::class.java) {
            CustomKeyboardTheme(forest, overrides = mapOf(ColorRole.BACKGROUND to hex("000000")))
        }
    }

    @Test
    fun invalidThemesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomKeyboardTheme(forest, overrides = mapOf(ColorRole.MUTED to Color(0x80FFFFFF)))
        }
        assertThrows(IllegalArgumentException::class.java) { CustomKeyboardTheme(forest, dim = MAX_DIM + 1) }
        assertThrows(IllegalArgumentException::class.java) { CustomKeyboardTheme(forest, dim = MIN_DIM - 1) }
        assertThrows(IllegalArgumentException::class.java) { CustomKeyboardTheme(forest, image = "../prefs.xml") }
        assertThrows(IllegalArgumentException::class.java) { CustomKeyboardTheme(forest, image = "bg-0123.jpg") }
        assertEquals(image, CustomKeyboardTheme(forest, image = image).image)
    }

    @Test
    fun resolutionUsesOverridesOnlyWhileEnabled() {
        val custom = CustomKeyboardTheme(forest).with(ColorRole.MUTED, hex("FF8800"))
        assertEquals(hex("FF8800"), resolveKeyboardColors(KeyboardTheme(true, custom), appDark = true).muted)
        assertEquals(DarkKeyboardColors, resolveKeyboardColors(KeyboardTheme(false, custom), appDark = true))
    }

    // Contrast of the effective palette.

    @Test
    fun anOverrideThatHurtsContrastIsReportedAndNotSecretlyFixed() {
        val bad = CustomKeyboardTheme(forest).with(ColorRole.CHIP_TEXT, forest.background)
        val failures = bad.contrastFailures(null)
        assertTrue(failures.any { it.foregroundRole == ColorRole.CHIP_TEXT && it.surfaceRole == ColorRole.BACKGROUND })
        val fix = fixContrast(bad, null)
        assertEquals(bad.overrides, fix.theme.overrides)
        assertFalse(fix.resolvesAll)
        assertEquals(fix.theme.contrastFailures(null), fix.remaining)
        assertTrue(fix.remaining.any { it.foregroundRole == ColorRole.CHIP_TEXT })
    }

    @Test
    fun fixClaimsSuccessOnlyWhenTheEffectiveThemePasses() {
        val murky = CustomKeyboardTheme(forest.with(SeedRole.TEXT, hex("2E5A44")))
            .with(ColorRole.DANGER, hex("FF8A8C"))
        val fix = fixContrast(murky, null)
        assertEquals(fix.remaining.isEmpty(), fix.resolvesAll)
        assertEquals(fix.theme.contrastFailures(null), fix.remaining)
        assertEquals(murky.overrides, fix.theme.overrides)
    }

    @Test
    fun overriddenRolesAreMarkedInTheEditor() {
        val bad = CustomKeyboardTheme(forest).with(ColorRole.CHIP_TEXT, forest.background)
        val line = com.rheerkens.androidkeyboard.companion.screens.failureLine(
            bad.contrastFailures(null).first { it.foregroundRole == ColorRole.CHIP_TEXT },
            bad.overrides.keys,
        )
        assertTrue(line, line.endsWith("(advanced color)"))
    }

    // Background image.

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun worstContrastUsesTheNearerEndOfTheRange() {
        assertEquals(1.0, worstContrast(hex("808080"), LuminanceRange(0.0, 1.0)), 1e-9)
        val dark = LuminanceRange(0.0, 0.05)
        assertEquals((1.0 + 0.05) / (0.05 + 0.05), worstContrast(Color.White, dark), 1e-9)
        val light = LuminanceRange(0.8, 1.0)
        assertEquals((0.8 + 0.05) / 0.05, worstContrast(Color.Black, light), 1e-9)
    }

    @Test
    fun theImageRangeIsNotAnAverage() {
        // Half black, half white: the average is mid grey, but text must read on both halves.
        val sample = ImageSample(intArrayOf(black, white, black, white))
        val range = sample.range(Color.Black, dim = 0)
        assertEquals(0.0, range.min, 1e-9)
        assertEquals(1.0, range.max, 1e-9)
    }

    @Test
    fun dimmingMovesTheImageTowardTheBackground() {
        val sample = ImageSample(intArrayOf(white))
        val none = sample.range(Color.Black, dim = 0)
        val half = sample.range(Color.Black, dim = 50)
        val most = sample.range(Color.Black, dim = MAX_DIM)
        assertTrue(none.max > half.max && half.max > most.max)
        assertEquals(relativeLuminance(Color(128, 128, 128)), half.max, 1e-9)
        // Out-of-range dimming is clamped, never beyond the maximum.
        assertEquals(most, sample.range(Color.Black, dim = 500))
    }

    @Test
    fun transparentImagePartsShowTheBackground() {
        val range = ImageSample(intArrayOf(0x00FFFFFF)).range(Color.Black, dim = 0)
        assertEquals(0.0, range.max, 1e-9)
    }

    @Test
    fun anImageCanFailPairsTheSolidBackgroundPasses() {
        val withImage = CustomKeyboardTheme(forest, image = image, dim = 0)
        val sample = ImageSample(intArrayOf(black, white))
        assertTrue(CustomKeyboardTheme(forest).contrastFailures(null).isEmpty())
        // Without a loaded image (missing file), the keyboard draws the colour, and so do the checks.
        assertTrue(withImage.contrastFailures(null).isEmpty())
        val failures = withImage.contrastFailures(sample)
        assertTrue(failures.isNotEmpty())
        assertTrue(failures.all { it.overImage && it.surface == "the background image" })
        // Keys and popups stay solid.
        assertTrue(keyboardContrastChecks(withImage.effectiveColors(), withImage.imageRange(sample))
            .filter { it.surfaceRole != ColorRole.BACKGROUND }.none { it.overImage })
    }

    @Test
    fun theDimmingSuggestionIsTheSmallestThatWorks() {
        val sample = ImageSample(intArrayOf(white, hex("C0C0C0").toArgbInt()))
        val theme = CustomKeyboardTheme(forest, image = image, dim = 10)
        val dim = dimmingForImage(theme, sample)
        assertNotNull(dim)
        assertTrue(theme.copy(dim = dim!!).contrastFailures(sample).none { it.overImage })
        assertTrue(theme.copy(dim = dim - 1).contrastFailures(sample).any { it.overImage })
        assertNull(dimmingForImage(theme.copy(dim = dim), sample))
        assertNull(dimmingForImage(CustomKeyboardTheme(forest), sample))
    }

    @Test
    fun noDimmingSuggestionWhenEvenTheMaximumFails() {
        // Background equal to the text: nothing over it can read, however dimmed.
        val flat = forest.with(SeedRole.BACKGROUND, forest.text)
        val theme = CustomKeyboardTheme(flat, image = image, dim = 0)
        assertNull(dimmingForImage(theme, ImageSample(intArrayOf(white))))
    }

    @Test
    fun fixWithAnImageDoesNotClaimSuccessWhileTextOverItFails() {
        val theme = CustomKeyboardTheme(forest, image = image, dim = 0)
        val sample = ImageSample(intArrayOf(black, white))
        val fix = fixContrast(theme, sample)
        assertEquals(fix.theme.contrastFailures(sample), fix.remaining)
        assertEquals(fix.remaining.isEmpty(), fix.resolvesAll)
        assertEquals(image, fix.theme.image)
        assertEquals(0, fix.theme.dim)
    }

    @Test
    fun navigationBarIconsFollowTheRenderedBackground() {
        val lightBg = hex("EFE6D8")
        val darkImage = LuminanceRange(0.0, 0.05)
        // Before Android 15 the bar is painted in the background colour, as before.
        assertFalse(navigationBarLightIcons(lightBg, darkImage, edgeToEdge = false))
        assertTrue(navigationBarLightIcons(forest.background, null, edgeToEdge = false))
        // From Android 15 the image shows through: a dark image gets light icons.
        assertTrue(navigationBarLightIcons(lightBg, darkImage, edgeToEdge = true))
        assertFalse(navigationBarLightIcons(forest.background, LuminanceRange(0.7, 1.0), edgeToEdge = true))
        // Without an image the colour decides.
        assertFalse(navigationBarLightIcons(lightBg, null, edgeToEdge = true))
    }

    @Test
    fun floatingKeyboardLeavesTheNavigationBarToTheApp() {
        val darkImage = LuminanceRange(0.0, 0.05)
        val lightBg = hex("EFE6D8")
        // Docked: the bar takes the keyboard colour, and from Android 15 the image under it decides.
        assertEquals(NavigationBarStyle(lightIcons = true, color = lightBg),
            keyboardNavigationBarStyle(lightBg, darkImage, floating = false, edgeToEdge = true))
        assertEquals(NavigationBarStyle(lightIcons = false, color = lightBg),
            keyboardNavigationBarStyle(lightBg, darkImage, floating = false, edgeToEdge = false))
        // Floating: transparent, and the image is not under the bar, so only the colour guides the icons.
        assertEquals(NavigationBarStyle(lightIcons = false, color = Color.Transparent),
            keyboardNavigationBarStyle(lightBg, darkImage, floating = true, edgeToEdge = true))
        assertEquals(NavigationBarStyle(lightIcons = true, color = Color.Transparent),
            keyboardNavigationBarStyle(forest.background, null, floating = true, edgeToEdge = false))
    }

    @Test
    fun standardPalettesStillPassWithTheNewChecks() {
        for (c in listOf(DarkKeyboardColors, LightKeyboardColors)) {
            assertTrue(keyboardContrastChecks(c).isNotEmpty())
            assertTrue(keyboardContrastFailures(c).joinToString { it.description }, keyboardContrastFailures(c).isEmpty())
            assertTrue(contrastOk(c.chipText, c.keyMod))
        }
    }

    private fun contrastOk(a: Color, b: Color) = com.rheerkens.androidkeyboard.companion.theme.contrast(a, b) >= AA_TEXT

    private fun Color.toArgbInt(): Int = (0xFF shl 24) or (red8() shl 16) or (green8() shl 8) or blue8()

    @Test
    fun clampDimKeepsTheRange() {
        assertEquals(MIN_DIM, clampDim(-10))
        assertEquals(MAX_DIM, clampDim(1000))
        assertEquals(DEFAULT_DIM, clampDim(DEFAULT_DIM))
        assertNotEquals(0, DEFAULT_DIM)
    }
}
