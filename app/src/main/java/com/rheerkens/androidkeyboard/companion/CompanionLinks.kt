package com.rheerkens.androidkeyboard.companion

import android.content.Context
import android.content.Intent

/** Companion app pages. The first four are bottom-navigation tabs. */
enum class Destination(val title: String, val isTab: Boolean = false) {
    HOME("Home", isTab = true),
    MODELS("Voice", isTab = true),
    DICTIONARY("Dictionary", isTab = true),
    SETTINGS("Settings", isTab = true),
    SETTINGS_VOICE("Dictation settings"),
    SETTINGS_KEYBOARD("Keyboard"),
    SETTINGS_CLIPBOARD("Clipboard & images"),
    SETTINGS_APPEARANCE("Appearance"),
    TRY_IT("Try it"),
    TOUR("Learn the gestures"),
    ABOUT("About & updates"),
    LANGUAGES("Languages & layouts"),
    KEYBOARD_THEME("Keyboard theme"),
    SPEECH_MODELS("Speech models");

    companion object {
        /** Pages that became part of another page, by the name they were saved or linked under. */
        private val merged = mapOf(
            "SETTINGS_KEYBOARDS" to LANGUAGES,
            "SETTINGS_TYPING" to SETTINGS_KEYBOARD,
            "SETTINGS_CUSTOMIZE" to SETTINGS_KEYBOARD,
            "SETTINGS_STORAGE" to SETTINGS_VOICE,
            "SETTINGS_UPDATES" to ABOUT,
            "DIAGNOSTICS" to ABOUT,
        )

        /**
         * The page for a name kept outside the running app: a saved back stack, or a link in a
         * notification made by an older version. Null for a name no version has used.
         */
        fun named(name: String): Destination? = entries.firstOrNull { it.name == name } ?: merged[name]
    }
}

/** A canonical back path makes pages opened from outside the app (the keyboard, a notification) predictable. */
fun destinationPath(destination: Destination): List<Destination> = when (destination) {
    Destination.SETTINGS_VOICE, Destination.SPEECH_MODELS -> listOf(Destination.MODELS, destination)
    Destination.SETTINGS_KEYBOARD, Destination.SETTINGS_CLIPBOARD, Destination.SETTINGS_APPEARANCE,
    Destination.LANGUAGES, Destination.ABOUT, Destination.TRY_IT -> listOf(Destination.SETTINGS, destination)
    Destination.KEYBOARD_THEME -> listOf(Destination.SETTINGS, Destination.SETTINGS_APPEARANCE, destination)
    Destination.TOUR -> listOf(Destination.HOME, destination)
    else -> listOf(destination)
}

/** The tab a page belongs to, which the bottom bar marks while the page shows. */
fun tabOf(destination: Destination): Destination = destinationPath(destination).first()

/**
 * The back stack after a link inside the app opens [destination] from the page on top of [stack]:
 * the page goes on top, so Back returns to the page the link was on, also when it belongs to
 * another tab. A page that is already open below is returned to rather than opened twice.
 */
fun linkedRoute(stack: List<Destination>, destination: Destination): List<Destination> {
    val open = stack.indexOf(destination)
    return if (open >= 0) stack.take(open + 1) else stack + destination
}

/** A saved back stack, read by a version that may have merged some of its pages. */
fun restoredRoute(names: List<String>): List<Destination> =
    names.mapNotNull(Destination::named).fold(emptyList<Destination>()) { stack, page -> linkedRoute(stack, page) }
        .ifEmpty { listOf(Destination.HOME) }

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

    fun destinationOf(intent: Intent?): Destination? = intent?.getStringExtra(EXTRA_DESTINATION)?.let(Destination::named)
}
