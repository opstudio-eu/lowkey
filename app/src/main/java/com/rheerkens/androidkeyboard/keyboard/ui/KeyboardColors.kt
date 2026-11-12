package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Keyboard palette, derived from OpStudio's brand colours (see `companion.theme.Brand`): dark on light
 * for the light theme, light blue and white on `#101E2B` for the dark one. Text pairs meet WCAG AA
 * (`KeyboardColorsContrastTest`). The layout of the roles follows concept 4.
 */
@Immutable
data class KeyboardColors(
    val background: Color,
    val divider: Color,
    val key: Color,
    val keyMod: Color,
    val keyPressed: Color,
    val keyOn: Color,
    val keyText: Color,
    val spaceText: Color,
    val muted: Color,
    val chipText: Color,
    val chipSaved: Color,
    val chipDivider: Color,
    val enter: Color,
    /** Enter while pressed; [enterInk] stays readable on it. */
    val enterPressed: Color,
    val enterInk: Color,
    val green: Color,
    val amber: Color,
    val toggleVoiceBg: Color,
    val toggleVoiceRing: Color,
    val stop: Color,
    /** Stop while pressed; [stopInk] stays readable on it. */
    val stopPressed: Color,
    val stopInk: Color,
    val busy: Color,
    /** Model pill background in voice mode. */
    val pill: Color,
    /** Model picker list surface and its hairline border. */
    val popup: Color,
    val popupBorder: Color,
    /** The hold-and-slide Backspace's top zone (all text before the cursor). */
    val danger: Color,
    val dangerInk: Color,
)

val DarkKeyboardColors = KeyboardColors(
    background = Color(0xFF101E2B),
    divider = Color(0xFF1D3042),
    key = Color(0xFF22384C),
    keyMod = Color(0xFF182B3C),
    keyPressed = Color(0xFF34506A),
    keyOn = Color(0xFF2B5577),
    keyText = Color(0xFFF2F7FB),
    spaceText = Color(0xFFB6C8D7),
    muted = Color(0xFFB6C8D7),
    chipText = Color(0xFFDCE7F0),
    chipSaved = Color(0xFFFFFFFF),
    chipDivider = Color(0xFF26394B),
    enter = Color(0xFF83C9F4),
    enterPressed = Color(0xFF5FAFE3),
    enterInk = Color(0xFF101E2B),
    green = Color(0xFF5CD193),
    amber = Color(0xFFF2C46E),
    toggleVoiceBg = Color(0xFF1A2D3E),
    toggleVoiceRing = Color(0xFF34506A),
    stop = Color(0xFFEAF2F8),
    stopPressed = Color(0xFFC3D3E0),
    stopInk = Color(0xFF101E2B),
    busy = Color(0xFF27394B),
    pill = Color(0xFF1A2D3E),
    popup = Color(0xFF142636),
    popupBorder = Color(0xFF2A4157),
    danger = Color(0xFFFF8A8C),
    dangerInk = Color(0xFF5C0A0C),
)

val LightKeyboardColors = KeyboardColors(
    background = Color(0xFFD9E3EC),
    divider = Color(0xFFC3D0DC),
    key = Color(0xFFFFFFFF),
    keyMod = Color(0xFFBFCEDB),
    keyPressed = Color(0xFFA6BACB),
    keyOn = Color(0xFFA9D2EE),
    keyText = Color(0xFF101E2B),
    spaceText = Color(0xFF34475A),
    muted = Color(0xFF34475A),
    chipText = Color(0xFF1B2C3B),
    chipSaved = Color(0xFF101E2B),
    chipDivider = Color(0xFFB8C7D4),
    enter = Color(0xFF17659C),
    enterPressed = Color(0xFF0F4E7A),
    enterInk = Color(0xFFFFFFFF),
    green = Color(0xFF166B42),
    amber = Color(0xFF7A5300),
    toggleVoiceBg = Color(0xFFFFFFFF),
    toggleVoiceRing = Color(0xFFA6BACB),
    stop = Color(0xFF101E2B),
    stopPressed = Color(0xFF2A3E52),
    stopInk = Color(0xFFF4F8FB),
    busy = Color(0xFFC9D6E1),
    pill = Color(0xFFC9D6E1),
    popup = Color(0xFFF8FBFD),
    popupBorder = Color(0xFFC5D2DD),
    danger = Color(0xFFC2161D),
    dangerInk = Color(0xFFFFFFFF),
)

val LocalKeyboardColors = staticCompositionLocalOf { DarkKeyboardColors }
