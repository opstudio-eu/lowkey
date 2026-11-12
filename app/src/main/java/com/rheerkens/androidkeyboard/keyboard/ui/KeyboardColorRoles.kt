package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.graphics.Color

/** How the theme editor groups [ColorRole]s. */
enum class RoleGroup(val label: String) {
    BACKGROUND("Background and lines"),
    KEYS("Keys"),
    ACCENT("Enter and accent"),
    TEXT("Suggestions and secondary text"),
    POPUPS("Popups, pickers and clipboard"),
    VOICE("Voice"),
    STATUS("Status and warnings"),
}

/**
 * Every colour role of [KeyboardColors], with the editor's group and label. The five roles with a
 * [seed] are the custom theme's seed colours; every other role can be overridden on its own.
 * [keyboardColorsOf] is the only place that maps roles to constructor parameters.
 */
enum class ColorRole(
    val group: RoleGroup,
    val label: String,
    val seed: SeedRole? = null,
    val read: (KeyboardColors) -> Color,
) {
    BACKGROUND(RoleGroup.BACKGROUND, "Background", SeedRole.BACKGROUND, { it.background }),
    DIVIDER(RoleGroup.BACKGROUND, "Top line", read = { it.divider }),
    CHIP_DIVIDER(RoleGroup.BACKGROUND, "Suggestion separators", read = { it.chipDivider }),

    KEY(RoleGroup.KEYS, "Keys", SeedRole.KEYS, { it.key }),
    KEY_MOD(RoleGroup.KEYS, "Function keys", SeedRole.FUNCTION_KEYS, { it.keyMod }),
    KEY_PRESSED(RoleGroup.KEYS, "Pressed keys and buttons", read = { it.keyPressed }),
    KEY_ON(RoleGroup.KEYS, "Active keys (Shift on)", read = { it.keyOn }),
    KEY_TEXT(RoleGroup.KEYS, "Text and icons", SeedRole.TEXT, { it.keyText }),
    SPACE_TEXT(RoleGroup.KEYS, "Space bar label", read = { it.spaceText }),

    ENTER(RoleGroup.ACCENT, "Accent and Enter", SeedRole.ACCENT, { it.enter }),
    ENTER_PRESSED(RoleGroup.ACCENT, "Pressed Enter", read = { it.enterPressed }),
    ENTER_INK(RoleGroup.ACCENT, "Text and icons on the accent", read = { it.enterInk }),

    CHIP_TEXT(RoleGroup.TEXT, "Suggestions", read = { it.chipText }),
    CHIP_SAVED(RoleGroup.TEXT, "Saved and best suggestion", read = { it.chipSaved }),
    MUTED(RoleGroup.TEXT, "Secondary text, hints and small icons", read = { it.muted }),

    POPUP(RoleGroup.POPUPS, "Popups, pickers and clipboard previews", read = { it.popup }),
    POPUP_BORDER(RoleGroup.POPUPS, "Popup borders and separators", read = { it.popupBorder }),

    PILL(RoleGroup.VOICE, "Model button", read = { it.pill }),
    BUSY(RoleGroup.VOICE, "Busy button and progress track", read = { it.busy }),
    TOGGLE_VOICE_BG(RoleGroup.VOICE, "Keyboard button", read = { it.toggleVoiceBg }),
    TOGGLE_VOICE_RING(RoleGroup.VOICE, "Keyboard button border", read = { it.toggleVoiceRing }),
    STOP(RoleGroup.VOICE, "Stop button", read = { it.stop }),
    STOP_PRESSED(RoleGroup.VOICE, "Pressed Stop button", read = { it.stopPressed }),
    STOP_INK(RoleGroup.VOICE, "Stop icon and text", read = { it.stopInk }),

    GREEN(RoleGroup.STATUS, "Ready: level meter, model dot and checks", read = { it.green }),
    AMBER(RoleGroup.STATUS, "Warnings and missing model", read = { it.amber }),
    DANGER(RoleGroup.STATUS, "Delete-all zone and Clear clipboard", read = { it.danger }),
    DANGER_INK(RoleGroup.STATUS, "Delete-all label", read = { it.dangerInk }),
    ;

    /** Stable name in saved preferences. */
    val key: String get() = name.lowercase()

    companion object {
        /** Roles the advanced editor can override: all but the seeds. */
        val overridable: List<ColorRole> = entries.filter { it.seed == null }

        fun ofKey(key: String): ColorRole? = entries.firstOrNull { it.key == key }

        fun forSeed(seed: SeedRole): ColorRole = entries.first { it.seed == seed }
    }
}

/**
 * A palette from one colour per role. Every constructor parameter is named and none has a default,
 * so a new [KeyboardColors] field does not compile until it has a [ColorRole].
 */
fun keyboardColorsOf(color: (ColorRole) -> Color) = KeyboardColors(
    background = color(ColorRole.BACKGROUND),
    divider = color(ColorRole.DIVIDER),
    key = color(ColorRole.KEY),
    keyMod = color(ColorRole.KEY_MOD),
    keyPressed = color(ColorRole.KEY_PRESSED),
    keyOn = color(ColorRole.KEY_ON),
    keyText = color(ColorRole.KEY_TEXT),
    spaceText = color(ColorRole.SPACE_TEXT),
    muted = color(ColorRole.MUTED),
    chipText = color(ColorRole.CHIP_TEXT),
    chipSaved = color(ColorRole.CHIP_SAVED),
    chipDivider = color(ColorRole.CHIP_DIVIDER),
    enter = color(ColorRole.ENTER),
    enterPressed = color(ColorRole.ENTER_PRESSED),
    enterInk = color(ColorRole.ENTER_INK),
    green = color(ColorRole.GREEN),
    amber = color(ColorRole.AMBER),
    toggleVoiceBg = color(ColorRole.TOGGLE_VOICE_BG),
    toggleVoiceRing = color(ColorRole.TOGGLE_VOICE_RING),
    stop = color(ColorRole.STOP),
    stopPressed = color(ColorRole.STOP_PRESSED),
    stopInk = color(ColorRole.STOP_INK),
    busy = color(ColorRole.BUSY),
    pill = color(ColorRole.PILL),
    popup = color(ColorRole.POPUP),
    popupBorder = color(ColorRole.POPUP_BORDER),
    danger = color(ColorRole.DANGER),
    dangerInk = color(ColorRole.DANGER_INK),
)

/** [this] palette with each role in [overrides] replaced and every other role unchanged. */
fun KeyboardColors.withOverrides(overrides: Map<ColorRole, Color>): KeyboardColors =
    if (overrides.isEmpty()) this else keyboardColorsOf { overrides[it] ?: it.read(this) }

operator fun KeyboardColors.get(role: ColorRole): Color = role.read(this)
