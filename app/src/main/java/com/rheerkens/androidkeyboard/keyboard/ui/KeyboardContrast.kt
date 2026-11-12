package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.companion.theme.AA_NON_TEXT
import com.rheerkens.androidkeyboard.companion.theme.AA_TEXT
import com.rheerkens.androidkeyboard.companion.theme.contrast
import com.rheerkens.androidkeyboard.companion.theme.relativeLuminance

/**
 * One foreground drawn on one surface of the keyboard, with its contrast and the minimum it needs.
 * [overImage] checks were measured against the darkest or lightest point of the dimmed background
 * image, whichever is worse, instead of a solid colour.
 */
data class ContrastCheck(
    val foreground: String,
    val surface: String,
    val ratio: Double,
    val minimum: Double,
    val foregroundRole: ColorRole,
    val surfaceRole: ColorRole,
    val overImage: Boolean = false,
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

/** The lowest and highest WCAG relative luminance found in a background image, after dimming. */
data class LuminanceRange(val min: Double, val max: Double) {
    init {
        require(min in 0.0..1.0 && max in 0.0..1.0 && min <= max) { "invalid luminance range $min..$max" }
    }
}

/**
 * The worst contrast [ink] has anywhere on an image whose luminance spans [range]: 1:1 when the
 * ink's own luminance falls inside it, else its ratio with the nearer end.
 */
fun worstContrast(ink: Color, range: LuminanceRange): Double {
    val l = relativeLuminance(ink)
    return when {
        l > range.max -> (l + 0.05) / (range.max + 0.05)
        l < range.min -> (range.min + 0.05) / (l + 0.05)
        else -> 1.0
    }
}

/**
 * The text and icon pairs the keys, strip, voice row, model picker, slider, fan and chooser draw:
 * text needs [AA_TEXT], icons and indicators [AA_NON_TEXT]. Pressed states keep their ink: Enter and
 * Stop switch to their own pressed fills, other keys, the Talk button, model picker rows and footer
 * actions to `keyPressed`.
 *
 * With [image], pairs drawn straight on the keyboard background are checked against the image's
 * worst point instead of the background colour. Keys, buttons and popups stay solid.
 */
fun keyboardContrastChecks(c: KeyboardColors, image: LuminanceRange? = null): List<ContrastCheck> {
    val checks = mutableListOf<ContrastCheck>()
    fun pair(fgLabel: String, fg: ColorRole, bgLabel: String, bg: ColorRole, min: Double = AA_TEXT) {
        val f = c[fg]
        checks += if (bg == ColorRole.BACKGROUND && image != null) {
            ContrastCheck(fgLabel, "the background image", worstContrast(f, image), min, fg, bg, overImage = true)
        } else {
            ContrastCheck(fgLabel, bgLabel, contrast(f, c[bg]), min, fg, bg)
        }
    }
    val keyText = "Key text"
    val muted = "Secondary text"
    for ((bg, role) in listOf(
        "keys" to ColorRole.KEY, "function keys" to ColorRole.KEY_MOD, "pressed keys" to ColorRole.KEY_PRESSED,
        "active keys" to ColorRole.KEY_ON, "the model button" to ColorRole.PILL, "popups" to ColorRole.POPUP,
        "the keyboard button" to ColorRole.TOGGLE_VOICE_BG,
    )) pair(keyText, ColorRole.KEY_TEXT, bg, role)
    for ((bg, role) in listOf(
        "the background" to ColorRole.BACKGROUND, "keys" to ColorRole.KEY, "function keys" to ColorRole.KEY_MOD,
        "the model button" to ColorRole.PILL, "popups" to ColorRole.POPUP, "the busy button" to ColorRole.BUSY,
        "pressed keys" to ColorRole.KEY_PRESSED,
    )) pair(muted, ColorRole.MUTED, bg, role)
    pair("Space bar label", ColorRole.SPACE_TEXT, "keys", ColorRole.KEY)
    pair("Space bar label", ColorRole.SPACE_TEXT, "pressed keys", ColorRole.KEY_PRESSED)
    pair("Suggestions", ColorRole.CHIP_TEXT, "the background", ColorRole.BACKGROUND)
    pair("Suggestions", ColorRole.CHIP_TEXT, "popups", ColorRole.POPUP)
    pair("Suggestions", ColorRole.CHIP_TEXT, "pressed keys", ColorRole.KEY_PRESSED)
    // Clipboard chips (the strip's clipboard button and the minimized bar's copied clips) draw
    // suggestion text on the key colour, and on the function key colour while pressed.
    pair("Clipboard text", ColorRole.CHIP_TEXT, "clipboard chips", ColorRole.KEY)
    // A pressed suggestion or clipboard chip and the dictionary prompt are filled with the function key colour.
    pair("Suggestions", ColorRole.CHIP_TEXT, "pressed suggestions", ColorRole.KEY_MOD)
    pair("Saved words", ColorRole.CHIP_SAVED, "pressed suggestions", ColorRole.KEY_MOD)
    pair("Saved words", ColorRole.CHIP_SAVED, "the background", ColorRole.BACKGROUND)
    pair("Warnings", ColorRole.AMBER, "the background", ColorRole.BACKGROUND)
    pair("Warning dot", ColorRole.AMBER, "the model button", ColorRole.PILL, AA_NON_TEXT)
    pair("Enter icon", ColorRole.ENTER_INK, "the Enter key", ColorRole.ENTER)
    pair("Enter icon", ColorRole.ENTER_INK, "the pressed Enter key", ColorRole.ENTER_PRESSED)
    pair("Stop icon", ColorRole.STOP_INK, "the Stop button", ColorRole.STOP)
    pair("Stop icon", ColorRole.STOP_INK, "the pressed Stop button", ColorRole.STOP_PRESSED)
    pair("Delete-all label", ColorRole.DANGER_INK, "the delete-all zone", ColorRole.DANGER)
    // Icons and indicators: strip icons, the level meter, the model dot, the danger zone and the accent.
    pair("Strip icons", ColorRole.KEY_TEXT, "the background", ColorRole.BACKGROUND, AA_NON_TEXT)
    // The emoji panel also draws small text (ABC and Skin tones) directly on this background.
    pair("Emoji labels", ColorRole.KEY_TEXT, "the background", ColorRole.BACKGROUND)
    pair("Level meter", ColorRole.GREEN, "the background", ColorRole.BACKGROUND, AA_NON_TEXT)
    pair("Model dot", ColorRole.GREEN, "the model button", ColorRole.PILL, AA_NON_TEXT)
    pair("Delete-all color", ColorRole.DANGER, "popups", ColorRole.POPUP, AA_NON_TEXT)
    pair("Accent", ColorRole.ENTER, "the background", ColorRole.BACKGROUND, AA_NON_TEXT)
    // The keyboard chooser and model picker mark the current item with an accent check on the popup.
    pair("Accent", ColorRole.ENTER, "popups", ColorRole.POPUP, AA_NON_TEXT)
    return checks
}

/** The pairs of [c] below their minimum. */
fun keyboardContrastFailures(c: KeyboardColors, image: LuminanceRange? = null): List<ContrastCheck> =
    keyboardContrastChecks(c, image).filterNot { it.passes }
