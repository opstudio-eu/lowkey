package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.companion.theme.AA_NON_TEXT
import com.rheerkens.androidkeyboard.companion.theme.AA_TEXT
import com.rheerkens.androidkeyboard.companion.theme.Brand
import com.rheerkens.androidkeyboard.companion.theme.contrast
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

    /** The text and icon pairs the keys, strip, voice row, model picker, slider and fan draw. */
    private fun assertReadable(name: String, c: KeyboardColors) {
        val failures = mutableListOf<String>()
        fun text(fg: String, f: Color, bg: String, b: Color, min: Double = AA_TEXT) {
            val ratio = contrast(f, b)
            if (ratio < min) failures += "$fg on $bg: ${"%.2f".format(ratio)} < $min"
        }
        for ((bg, b) in listOf("key" to c.key, "keyMod" to c.keyMod, "keyPressed" to c.keyPressed, "keyOn" to c.keyOn, "pill" to c.pill, "popup" to c.popup, "toggleVoiceBg" to c.toggleVoiceBg)) {
            text("keyText", c.keyText, bg, b)
        }
        for ((bg, b) in listOf("background" to c.background, "key" to c.key, "keyMod" to c.keyMod, "pill" to c.pill, "popup" to c.popup, "busy" to c.busy)) {
            text("muted", c.muted, bg, b)
        }
        text("spaceText", c.spaceText, "key", c.key)
        text("chipText", c.chipText, "background", c.background)
        text("chipText", c.chipText, "popup", c.popup)
        text("chipSaved", c.chipSaved, "background", c.background)
        text("tagText", c.tagText, "background", c.background)
        text("amber", c.amber, "background", c.background)
        text("amber", c.amber, "pill", c.pill, AA_NON_TEXT)
        text("enterInk", c.enterInk, "enter", c.enter)
        text("stopInk", c.stopInk, "stop", c.stop)
        // Pressed states keep their ink: Enter and Stop switch to their own pressed fills, other
        // keys, the Talk button, model picker rows and footer actions to keyPressed.
        text("enterInk", c.enterInk, "enterPressed", c.enterPressed)
        text("stopInk", c.stopInk, "stopPressed", c.stopPressed)
        text("spaceText", c.spaceText, "keyPressed", c.keyPressed)
        text("muted", c.muted, "keyPressed", c.keyPressed)
        text("chipText", c.chipText, "keyPressed", c.keyPressed)
        text("noteText", c.noteText, "noteBg", c.noteBg)
        text("dangerInk", c.dangerInk, "danger", c.danger)
        // Icons and indicators: the level meter, the model dot, the danger zone and the Enter key.
        text("green", c.green, "background", c.background, AA_NON_TEXT)
        text("green", c.green, "pill", c.pill, AA_NON_TEXT)
        text("danger", c.danger, "popup", c.popup, AA_NON_TEXT)
        text("enter", c.enter, "background", c.background, AA_NON_TEXT)
        assertTrue("$name keyboard:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
