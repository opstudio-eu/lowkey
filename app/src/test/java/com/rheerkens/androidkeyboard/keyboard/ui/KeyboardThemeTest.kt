package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.companion.theme.AA_TEXT
import com.rheerkens.androidkeyboard.companion.theme.bestInk
import com.rheerkens.androidkeyboard.companion.theme.contrast
import com.rheerkens.androidkeyboard.companion.theme.prefersLightInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeTest {
    private fun hex(s: String) = parseHexColor(s)!!

    /** Deep green keyboard: well-chosen, readable custom colours. */
    private val forest = KeyboardSeed(
        background = hex("0F2A1D"), keys = hex("1E4632"), functionKeys = hex("163826"),
        text = hex("F1FAF4"), accent = hex("F2B33D"),
    )

    /** Light sand keyboard. */
    private val sand = KeyboardSeed(
        background = hex("EFE6D8"), keys = hex("FFFDF8"), functionKeys = hex("DCCFBC"),
        text = hex("2A2118"), accent = hex("8C2F39"),
    )

    @Test
    fun standardSeedsGiveTheStandardPalettes() {
        assertEquals(DarkKeyboardColors, deriveKeyboardColors(KeyboardSeed.of(DarkKeyboardColors)))
        assertEquals(LightKeyboardColors, deriveKeyboardColors(KeyboardSeed.of(LightKeyboardColors)))
    }

    @Test
    fun seedColoursAreUsedAsGiven() {
        val c = deriveKeyboardColors(forest)
        assertEquals(forest.background, c.background)
        assertEquals(forest.keys, c.key)
        assertEquals(forest.functionKeys, c.keyMod)
        assertEquals(forest.text, c.keyText)
        assertEquals(forest.accent, c.enter)
    }

    @Test
    fun readableSeedsPassEveryCheck() {
        for ((name, seed) in listOf("forest" to forest, "sand" to sand)) {
            val failures = keyboardContrastFailures(deriveKeyboardColors(seed))
            assertTrue("$name:\n" + failures.joinToString("\n") { it.description }, failures.isEmpty())
        }
    }

    @Test
    fun pressedAndActiveStatesDifferFromTheirKeys() {
        for (seed in listOf(forest, sand)) {
            val c = deriveKeyboardColors(seed)
            assertNotEquals(c.key, c.keyPressed)
            assertNotEquals(c.keyMod, c.keyOn)
            assertNotEquals(c.enter, c.enterPressed)
            assertNotEquals(c.stop, c.stopPressed)
            assertTrue(contrast(c.keyText, c.keyPressed) >= AA_TEXT)
        }
    }

    @Test
    fun accentInkIsBlackOrWhiteByContrast() {
        // Mid orange: luminance below 0.5, yet black reads better than white.
        val orange = hex("E07A1F")
        assertTrue(contrast(Color.Black, orange) > contrast(Color.White, orange))
        val c = deriveKeyboardColors(forest.with(SeedRole.ACCENT, orange))
        assertEquals(Color.Black, c.enterInk)
        val navy = deriveKeyboardColors(sand.with(SeedRole.ACCENT, hex("1B2A6B")))
        assertEquals(Color.White, navy.enterInk)
    }

    @Test
    fun lightInkIsChosenByContrastNotByHalfLuminance() {
        // Luminance about 0.21: brighter than the 0.18 crossover, so black wins.
        assertFalse(prefersLightInk(hex("808080")))
        assertEquals(Color.Black, bestInk(hex("808080")))
        assertTrue(prefersLightInk(hex("5A5A5A")))
    }

    @Test
    fun statusColoursStayReadableOnTheBackground() {
        val light = deriveKeyboardColors(sand)
        val dark = deriveKeyboardColors(forest)
        assertEquals(LightKeyboardColors.amber, light.amber)
        assertEquals(DarkKeyboardColors.amber, dark.amber)
    }

    @Test
    fun lowContrastSeedsReportTheirPairs() {
        val murky = forest.with(SeedRole.TEXT, hex("2E5A44"))
        val failures = keyboardContrastFailures(deriveKeyboardColors(murky))
        assertTrue(failures.any { it.foreground == "Key text" && it.surface == "keys" })
        assertTrue(failures.all { it.ratio < it.minimum })
    }

    @Test
    fun fixResolvesLowContrastText() {
        val murky = forest.with(SeedRole.TEXT, hex("2E5A44"))
        val fix = fixContrast(murky)
        assertTrue(fix.remaining.joinToString("\n") { it.description }, fix.remaining.isEmpty())
        assertTrue(fix.resolvesAll)
        assertTrue(keyboardContrastFailures(deriveKeyboardColors(fix.seed)).isEmpty())
        // Only what needed changing moved.
        assertEquals(murky.background, fix.seed.background)
    }

    @Test
    fun fixLeavesReadablePalettesAlone() {
        val fix = fixContrast(forest)
        assertEquals(forest, fix.seed)
        assertTrue(fix.remaining.isEmpty())
    }

    @Test
    fun fixNeverMakesThingsWorse() {
        val seeds = listOf(
            KeyboardSeed(hex("777777"), hex("7A7A7A"), hex("757575"), hex("787878"), hex("797979")),
            KeyboardSeed(hex("FFFFFF"), hex("FFFFFF"), hex("000000"), hex("808080"), hex("FFFF00")),
            forest.with(SeedRole.ACCENT, forest.background),
        )
        for (seed in seeds) {
            val before = keyboardContrastFailures(deriveKeyboardColors(seed))
            val fix = fixContrast(seed)
            assertTrue(fix.remaining.size <= before.size)
            assertEquals(keyboardContrastFailures(deriveKeyboardColors(fix.seed)), fix.remaining)
            assertEquals(fix.remaining.isEmpty(), fix.resolvesAll)
        }
    }

    @Test
    fun hexParsingIsStrictAndOpaque() {
        assertEquals(Color(0xFF1A2B3C), parseHexColor("1A2B3C"))
        assertEquals(Color(0xFF1A2B3C), parseHexColor("#1a2b3c"))
        assertEquals(Color(0xFF1A2B3C), parseHexColor(" 1a2b3c "))
        assertNull(parseHexColor("1A2B3"))
        assertNull(parseHexColor("1A2B3C4D"))
        assertNull(parseHexColor("GGGGGG"))
        assertNull(parseHexColor(""))
        assertEquals("1A2B3C", Color(0xFF1A2B3C).toHex())
    }

    @Test
    fun seedEncodingRoundTrips() {
        assertEquals(forest, KeyboardSeed.decode(forest.encode()))
        assertNull(KeyboardSeed.decode("0F2A1D,1E4632"))
        assertNull(KeyboardSeed.decode("0F2A1D,1E4632,163826,F1FAF4,nothex"))
        assertNull(KeyboardSeed.decode(null))
    }

    @Test
    fun customThemeWinsOnlyWhileEnabled() {
        val saved = KeyboardTheme(enabled = true, seed = forest)
        assertEquals(deriveKeyboardColors(forest), resolveKeyboardColors(saved, appDark = false))
        val off = saved.copy(enabled = false)
        assertEquals(LightKeyboardColors, resolveKeyboardColors(off, appDark = false))
        assertEquals(DarkKeyboardColors, resolveKeyboardColors(off, appDark = true))
        assertEquals(forest, off.seed)
        assertEquals(DarkKeyboardColors, resolveKeyboardColors(KeyboardTheme(enabled = true, seed = null), appDark = true))
    }
}
