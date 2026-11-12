package com.rheerkens.androidkeyboard.keyboard.ui

import com.rheerkens.androidkeyboard.companion.theme.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardColorsContrastTest {
    @Test
    fun lightKeyboardMeetsWcagAa() = assertReadable("light", LightKeyboardColors)

    @Test
    fun darkKeyboardMeetsWcagAa() = assertReadable("dark", DarkKeyboardColors)

    @Test
    fun keyboardsUseTheBrandColours() {
        assertEquals(Brand.Dark, DarkKeyboardColors.background)
        assertEquals(Brand.LightBlue, DarkKeyboardColors.enter)
        assertEquals(Brand.Blue, LightKeyboardColors.enter)
        assertEquals(Brand.Dark, LightKeyboardColors.keyText)
    }

    @Test
    fun ratioLabelsRoundDown() {
        assertEquals("4.4:1", ratioLabel(4.49))
        assertEquals("4.5:1", ratioLabel(4.5))
        assertEquals("21.0:1", ratioLabel(21.0))
    }

    private fun assertReadable(name: String, c: KeyboardColors) {
        val failures = keyboardContrastFailures(c)
        assertTrue("$name keyboard:\n" + failures.joinToString("\n") { it.description }, failures.isEmpty())
    }
}
