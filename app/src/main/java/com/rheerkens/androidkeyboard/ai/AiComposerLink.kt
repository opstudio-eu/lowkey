package com.rheerkens.androidkeyboard.ai

import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * The keyboard's side of the image prompt window ([AiPromptActivity]). Both run in this process: the
 * window is a real, focused text box, so the keys, the mic and other voice tools type into it like
 * into any app, while the images are made, kept and inserted by the keyboard.
 */
interface AiComposerHost {
    /** Images being made, oldest first; the window lists them with Stop. */
    val runningImages: StateFlow<List<AiTask>>

    /** Starts an image for [prompt]; null once it runs, otherwise why it couldn't start. */
    fun submitImagePrompt(prompt: String): String?

    fun stopImage(id: Long)

    /** The window closed without Create: [draft] comes back with the next prompt for the same field. */
    fun composerClosed(draft: String)
}

object AiComposerLink {
    /** The keyboard service while it runs; the window closes without one. */
    @Volatile
    var host: AiComposerHost? = null

    /** In the prompt box's `privateImeOptions`, so the keyboard knows it isn't an app's field. */
    const val PROMPT_FIELD = "lowkey.aiPrompt"

    /** [info] is the prompt window's own box (this app, [packageName], and marked so). */
    fun isPromptField(info: EditorInfo?, packageName: String): Boolean =
        info != null && info.packageName == packageName && info.privateImeOptions?.contains(PROMPT_FIELD) == true
}
