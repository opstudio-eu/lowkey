package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.companion.theme.AA_NON_TEXT
import com.rheerkens.androidkeyboard.companion.theme.AA_TEXT
import com.rheerkens.androidkeyboard.companion.theme.contrast

/** One foreground drawn on one surface of the keyboard, with its contrast and the minimum it needs. */
data class ContrastCheck(
    val foreground: String,
    val surface: String,
    val ratio: Double,
    val minimum: Double,
) {
    val passes: Boolean get() = ratio >= minimum

    /** How far below the minimum the pair is; 0 when it passes. */
    val deficit: Double get() = if (passes) 0.0 else minimum - ratio

    /** "Key text on function keys: 2.3:1, needs 4.5:1". */
    val description: String get() = "$foreground on $surface: ${ratioLabel(ratio)}, needs ${ratioLabel(minimum)}"
}

/** "4.5:1", with one decimal, rounded down so a failing pair never reads as its minimum. */
fun ratioLabel(ratio: Double): String {
    val tenths = kotlin.math.floor(ratio * 10 + 1e-9).toInt()
    return "${tenths / 10}.${tenths % 10}:1"
}

/**
 * The text and icon pairs the keys, strip, voice row, model picker, slider, fan and chooser draw:
 * text needs [AA_TEXT], icons and indicators [AA_NON_TEXT]. Pressed states keep their ink: Enter and
 * Stop switch to their own pressed fills, other keys, the Talk button, model picker rows and footer
 * actions to `keyPressed`.
 */
fun keyboardContrastChecks(c: KeyboardColors): List<ContrastCheck> {
    val checks = mutableListOf<ContrastCheck>()
    fun pair(fg: String, f: Color, bg: String, b: Color, min: Double = AA_TEXT) {
        checks += ContrastCheck(fg, bg, contrast(f, b), min)
    }
    val keyText = "Key text"
    val muted = "Secondary text"
    for ((bg, b) in listOf(
        "keys" to c.key, "function keys" to c.keyMod, "pressed keys" to c.keyPressed, "active keys" to c.keyOn,
        "the model button" to c.pill, "popups" to c.popup, "the keyboard button" to c.toggleVoiceBg,
    )) pair(keyText, c.keyText, bg, b)
    for ((bg, b) in listOf(
        "the background" to c.background, "keys" to c.key, "function keys" to c.keyMod, "the model button" to c.pill,
        "popups" to c.popup, "the busy button" to c.busy, "pressed keys" to c.keyPressed,
    )) pair(muted, c.muted, bg, b)
    pair("Space bar label", c.spaceText, "keys", c.key)
    pair("Space bar label", c.spaceText, "pressed keys", c.keyPressed)
    pair("Suggestions", c.chipText, "the background", c.background)
    pair("Suggestions", c.chipText, "popups", c.popup)
    pair("Suggestions", c.chipText, "pressed keys", c.keyPressed)
    pair("Saved words", c.chipSaved, "the background", c.background)
    pair("Labels", c.tagText, "the background", c.background)
    pair("Warnings", c.amber, "the background", c.background)
    pair("Warning dot", c.amber, "the model button", c.pill, AA_NON_TEXT)
    pair("Enter icon", c.enterInk, "the Enter key", c.enter)
    pair("Enter icon", c.enterInk, "the pressed Enter key", c.enterPressed)
    pair("Stop icon", c.stopInk, "the Stop button", c.stop)
    pair("Stop icon", c.stopInk, "the pressed Stop button", c.stopPressed)
    pair("Notes", c.noteText, "notes", c.noteBg)
    pair("Delete-all label", c.dangerInk, "the delete-all zone", c.danger)
    // Icons and indicators: the level meter, the model dot, the danger zone and the Enter key.
    pair("Level meter", c.green, "the background", c.background, AA_NON_TEXT)
    pair("Model dot", c.green, "the model button", c.pill, AA_NON_TEXT)
    pair("Delete-all color", c.danger, "popups", c.popup, AA_NON_TEXT)
    pair("Accent", c.enter, "the background", c.background, AA_NON_TEXT)
    // The keyboard chooser and model picker mark the current item with an accent check on the popup.
    pair("Accent", c.enter, "popups", c.popup, AA_NON_TEXT)
    return checks
}

/** The pairs of [c] below their minimum. */
fun keyboardContrastFailures(c: KeyboardColors): List<ContrastCheck> = keyboardContrastChecks(c).filterNot { it.passes }
