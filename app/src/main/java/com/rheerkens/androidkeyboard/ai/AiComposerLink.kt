package com.rheerkens.androidkeyboard.ai

import android.net.Uri
import android.view.inputmethod.EditorInfo
import com.rheerkens.androidkeyboard.clipboard.ClipEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * The keyboard's side of the image prompt window ([AiPromptActivity]). Both run in this process: the
 * window is a real, focused text box, so the keys, the mic and other voice tools type into it like
 * into any app, while the images are made, kept and inserted by the keyboard.
 */
interface AiComposerHost {
    /** Images being made, oldest first; the window lists them with Stop. */
    val runningImages: StateFlow<List<AiTask>>

    /**
     * Starts an image for [prompt], with the pictures added to it ([promptImages]); null once it runs,
     * otherwise why it couldn't start. [session]: the window's, from its intent; a window the keyboard has
     * since replaced (one left behind the photo picker, or closing late) changes nothing.
     */
    fun submitImagePrompt(prompt: String, session: Int): String?

    fun stopImage(id: Long)

    /** The window closed without Create: [draft] (and its pictures) come back with the next prompt for the same field. */
    fun composerClosed(draft: String, session: Int)

    /** Pictures added to the prompt, sent with it; the window shows them above the box. */
    val promptImages: StateFlow<List<PromptImage>>

    /** Images in the clipboard history and recent screenshots, newest first, offered by the window's + tray. */
    val recentImages: StateFlow<List<ClipEntry>>

    /** Adds [entry] (an image or a screenshot) to the prompt; a note on the keyboard says when it can't. */
    fun addPromptImage(entry: ClipEntry)

    /** How many more pictures fit, counting those still being read. */
    fun promptImageRoom(): Int

    /** Adds photos picked in the system's photo picker, as many as there is room for. */
    fun addPromptPhotos(uris: List<Uri>, session: Int)

    fun removePromptImage(id: Long)

    /**
     * The prompt in the window of [session]: whether its words are all blank ([empty]) and whether Improve
     * is rewriting them ([improving]). The keyboard's Create (Enter) follows the window's Create from it.
     */
    fun promptState(empty: Boolean, improving: Boolean, session: Int)
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
