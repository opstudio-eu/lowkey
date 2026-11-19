package com.rheerkens.androidkeyboard.companion

import com.rheerkens.androidkeyboard.keyboard.KeyboardGesture
import com.rheerkens.androidkeyboard.voice.VoiceSessionRecord
import kotlin.math.abs

/**
 * The steps of the gesture tour (Home › Learn the gestures): each teaches one thing the keyboard
 * can do that nothing on it shows, by having the user do it in a practice field.
 */
enum class TourStep(
    val title: String,
    val intro: String,
    /** What the practice field starts with, the cursor at its end. */
    val practice: String,
) {
    CURSOR(
        "Move the cursor",
        "Put a finger on the red dot at G's bottom-right corner (on most layouts) and slide, up, down or sideways. The letters fade: the keyboard is now a trackpad for the cursor.",
        "Slide from the red dot\nto move through these lines\nwithout lifting your finger.",
    ),
    SELECT(
        "Select text",
        "Tap Shift once, then slide from the red dot. What the cursor passes is selected. " +
            "Cut and Copy are behind the three dots beside the microphone.",
        "Select a part of this sentence.",
    ),
    DELETE(
        "Delete whole words",
        "Hold Backspace and slide up. Each step removes one more word. Slide back down to bring words back, then let go.",
        "Delete the last words of this sentence sentence sentence.",
    ),
    DICTATE(
        "Talk instead of typing",
        "Tap the microphone and say a sentence, then tap the checkmark. Lowkey writes it out on your phone, without the internet.",
        "",
    ),
}

/** One thing the current step asks for, and whether it has been done. */
data class TourGoal(val text: String, val done: Boolean)

/**
 * Where the tour is and what the user has done in the current step. The keyboard reports what
 * happens on it ([on], [onDictated]); the step is done when all its [goals] are.
 */
data class TourProgress(
    val step: TourStep = TourStep.CURSOR,
    /** Characters and lines the cursor moved by sliding, in this step. */
    val chars: Int = 0,
    val lines: Int = 0,
    /** Characters and lines slid while selecting. */
    val selected: Int = 0,
    val wordsDeleted: Int = 0,
    val dictatedWords: Int = 0,
) {
    val goals: List<TourGoal>
        get() = when (step) {
            TourStep.CURSOR -> listOf(
                TourGoal("Slide left or right to move through the text", chars >= SIDEWAYS),
                TourGoal("Slide up or down to change line", lines >= 1),
            )
            TourStep.SELECT -> listOf(TourGoal("Tap Shift, then slide to select a few letters", selected >= SELECTED))
            TourStep.DELETE -> listOf(TourGoal("Hold Backspace and slide up to delete a word", wordsDeleted >= 1))
            TourStep.DICTATE -> listOf(TourGoal("Dictate a sentence", dictatedWords >= 1))
        }

    val done: Boolean get() = goals.all { it.done }

    /** The keyboard did [gesture]; only what the current step asks for counts. */
    fun on(gesture: KeyboardGesture): TourProgress = when (gesture) {
        is KeyboardGesture.CursorMoved -> when {
            step == TourStep.CURSOR && !gesture.selecting -> copy(chars = chars + abs(gesture.chars), lines = lines + abs(gesture.lines))
            step == TourStep.SELECT && gesture.selecting -> copy(selected = selected + abs(gesture.chars) + abs(gesture.lines))
            else -> this
        }
        is KeyboardGesture.WordsDeleted -> if (step == TourStep.DELETE) copy(wordsDeleted = wordsDeleted + gesture.words) else this
    }

    /** A dictation inserted [words] words. */
    fun onDictated(words: Int): TourProgress = if (step == TourStep.DICTATE) copy(dictatedWords = dictatedWords + words) else this

    /** The next step, started afresh; null after the last one. */
    fun next(): TourProgress? = TourStep.entries.getOrNull(step.ordinal + 1)?.let { TourProgress(it) }

    companion object {
        /** A sideways slide counts once it is clearly more than a twitch. */
        const val SIDEWAYS = 3
        const val SELECTED = 2
    }
}

/** The words a finished dictation put in the field: none when it was cancelled or heard no speech. */
fun VoiceSessionRecord.insertedWords(): Int = if (transitions.lastOrNull()?.startsWith("committed") == true) words else 0

/**
 * Whether to open the tour by itself: once, right after the keyboard was set up in the app
 * ([cameFromSetup]). Someone who already had the keyboard on finds it on Home instead.
 */
fun offersTour(cameFromSetup: Boolean, setup: KeyboardSetupState, tourSeen: Boolean): Boolean =
    cameFromSetup && setup == KeyboardSetupState.READY && !tourSeen
