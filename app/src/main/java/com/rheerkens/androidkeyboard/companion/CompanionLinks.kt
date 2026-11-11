package com.rheerkens.androidkeyboard.companion

import android.content.Context
import android.content.Intent

/** Companion app pages. The first four are bottom-navigation tabs. */
enum class Destination(val title: String, val isTab: Boolean = false) {
    HOME("Lowkey Keyboard", isTab = true),
    MODELS("Speech models", isTab = true),
    DICTIONARY("Dictionary", isTab = true),
    SETTINGS("Settings", isTab = true),
    /** Deep link: Settings scrolled to the Voice group. */
    SETTINGS_VOICE("Settings"),
    /** Deep link: Settings scrolled to the keyboard layouts. */
    SETTINGS_KEYBOARDS("Settings"),
    TRY_IT("Try it"),
    DIAGNOSTICS("Diagnostics"),
    ABOUT("About and licenses"),
    LANGUAGES("Keyboard languages"),
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
