package com.rheerkens.androidkeyboard.companion

import android.content.Context
import android.content.Intent

/** Companion app pages. The first four are bottom-navigation tabs. */
enum class Destination(val title: String, val isTab: Boolean = false) {
    HOME("Home", isTab = true),
    MODELS("Voice", isTab = true),
    DICTIONARY("Dictionary", isTab = true),
    SETTINGS("Settings", isTab = true),
    /** Existing IME shortcuts keep their names but now open real detail pages. */
    SETTINGS_VOICE("Dictation"),
    SETTINGS_KEYBOARDS("Keyboard layouts"),
    SETTINGS_KEYBOARD("Keyboard"),
    SETTINGS_TYPING("Typing"),
    SETTINGS_CUSTOMIZE("Key feedback"),
    SETTINGS_CLIPBOARD("Clipboard & images"),
    SETTINGS_APPEARANCE("Appearance"),
    SETTINGS_STORAGE("Storage & performance"),
    SETTINGS_UPDATES("Updates"),
    TRY_IT("Try it"),
    DIAGNOSTICS("Diagnostics"),
    ABOUT("About and licenses"),
    LANGUAGES("Keyboard languages"),
    KEYBOARD_THEME("Keyboard theme"),
}

/** A canonical back path also makes externally opened settings predictable. */
fun destinationPath(destination: Destination): List<Destination> = when (destination) {
    Destination.SETTINGS_VOICE, Destination.SETTINGS_STORAGE -> listOf(Destination.MODELS, destination)
    Destination.SETTINGS_KEYBOARD, Destination.SETTINGS_CLIPBOARD,
    Destination.SETTINGS_APPEARANCE, Destination.SETTINGS_UPDATES,
    Destination.LANGUAGES, Destination.ABOUT -> listOf(Destination.SETTINGS, destination)
    Destination.SETTINGS_TYPING, Destination.SETTINGS_CUSTOMIZE ->
        listOf(Destination.SETTINGS, Destination.SETTINGS_KEYBOARD, destination)
    Destination.SETTINGS_KEYBOARDS -> listOf(Destination.SETTINGS, Destination.LANGUAGES, destination)
    Destination.KEYBOARD_THEME -> listOf(Destination.SETTINGS, Destination.SETTINGS_APPEARANCE, destination)
    Destination.DIAGNOSTICS, Destination.TRY_IT -> listOf(Destination.SETTINGS, destination)
    else -> listOf(destination)
}

enum class KeyboardSetupState { NOT_ENABLED, NOT_SELECTED, READY }

fun keyboardSetupState(enabled: Boolean, selected: Boolean): KeyboardSetupState = when {
    !enabled -> KeyboardSetupState.NOT_ENABLED
    !selected -> KeyboardSetupState.NOT_SELECTED
    else -> KeyboardSetupState.READY
}

/** Intents the keyboard uses to open companion pages. */
object CompanionLinks {
    const val EXTRA_DESTINATION = "com.rheerkens.androidkeyboard.DESTINATION"

    fun intent(context: Context, destination: Destination): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_DESTINATION, destination.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun destinationOf(intent: Intent?): Destination? =
        intent?.getStringExtra(EXTRA_DESTINATION)?.let { name -> Destination.entries.firstOrNull { it.name == name } }
}
