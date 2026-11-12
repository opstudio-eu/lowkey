package com.rheerkens.androidkeyboard.keyboard.ui

import com.rheerkens.androidkeyboard.dictionary.Suggestion
import android.graphics.Bitmap
import com.rheerkens.androidkeyboard.clipboard.ClipEntry
import com.rheerkens.androidkeyboard.clipboard.ClipboardContent
import com.rheerkens.androidkeyboard.keyboard.BackspaceScrub
import com.rheerkens.androidkeyboard.keyboard.FanCommand
import com.rheerkens.androidkeyboard.keyboard.Key
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard

/** What the Enter key does in the current editor. */
enum class EnterKind(val label: String?) {
    NEWLINE(null), GO("Go"), SEND("Send"), SEARCH("Search"), NEXT("Next"), DONE("Done"), PREVIOUS("Prev")
}

/** Transient message shown in the top strip, e.g. a cancelled-dictation note. */
data class StripNote(val text: String, val warn: Boolean = false, val id: Long = System.nanoTime())

data class StripState(
    val slots: List<Suggestion?> = listOf(null, null, null),
    val typingWord: Boolean = false,
    val clipboard: ClipboardContent? = null,
    val imagePasteSupported: Boolean = false,
    /** Word for the "Add "word" to dictionary" prompt, shown after a long-press. */
    val addPrompt: String? = null,
    val note: StripNote? = null,
    val enabled: Boolean = true,
)

/** History is filtered for private editors by the service, independently of word suggestions. */
internal fun StripState.showsClipHistory(hasClips: Boolean): Boolean =
    note == null && addPrompt == null && !typingWord && hasClips

/** A clipboard-history item in the minimized bar; images are dimmed where the editor refuses them. */
data class ClipChip(val entry: ClipEntry, val pasteable: Boolean)

/** Where a Backspace slide into the editor cuts the text. */
sealed interface WordAim {
    /** Asking the editor where its words are. */
    data object Searching : WordAim
    /** Releasing keeps the text as shown: [words] words deleted before the cursor. */
    data class Cut(val words: Int) : WordAim
    /** This editor can't report word positions (Android 13 or older, or the app doesn't support it). */
    data object Unsupported : WordAim
}

/** The current [WordAim] while Backspace aims into the editor, for the key's bubble. */
val LocalWordAim = androidx.compose.runtime.compositionLocalOf<WordAim?> { null }

/** Callbacks from the keyboard UI into the input method. */
interface KeyboardActions {
    fun onCloseEmoji() {}
    fun onChooseEmoji(value: String) {}
    fun onOpenTools() {}
    fun onResizeKeyboard() {}
    fun onSetNumberRow(enabled: Boolean) {}
    fun onSelectPlacementMode(mode: com.rheerkens.androidkeyboard.keyboard.PlacementMode) {}
    fun onSavePlacement(profile: com.rheerkens.androidkeyboard.keyboard.PlacementProfile, placement: com.rheerkens.androidkeyboard.keyboard.KeyboardPlacement) {}
    fun onPlacementBounds(left: Int, top: Int, right: Int, bottom: Int, floating: Boolean) {}
    fun onLayoutBackHandler(handler: (() -> Boolean)?) {}
    /** Back for an open menu above the keys (the emoji key's languages), ahead of the layout's handler. */
    fun onMenuBackHandler(handler: (() -> Boolean)?) {}
    fun onPrepareResize() {}
    fun onKeyDown()
    fun onKey(key: Key)
    fun onBackspace()
    fun onBackspaceHoldStarted()
    fun onBackspaceHoldEnded()
    fun onClearAllText()
    /** Deletes all text before the cursor and keeps the text after it, like the slider's top. */
    fun onDeleteBeforeCursor()
    /** Hold-and-slide Backspace began; null where the editor can't support it. */
    fun onBackspaceScrubStart(): BackspaceScrub?
    /** Show the text at [level] of the slider; returns the level shown, or null once it can't go on. */
    fun onBackspaceScrubTo(level: Int): Int?
    fun onBackspaceScrubEnd()
    /**
     * During a Backspace slide the finger is over the editor, at these screen coordinates: delete
     * from the word under it up to the cursor, restoring anything after that word.
     */
    fun onWordAim(screenX: Float, screenY: Float)
    /** The slide left the editor again: put back everything it deleted (release then cancels). */
    fun onWordAimLeft()
    /** A command picked from the minimized bar's Backspace fan (never [FanCommand.WORDS]). */
    fun onFanCommand(command: FanCommand)
    fun onGlobeLongPress()
    /** A keyboard (language and layout) chosen from the emoji key's hold menu, by [TypingKeyboard.id]. */
    fun onSelectKeyboard(id: String) {}
    /** Long-press on Space opens Android's input-method picker directly. */
    fun onSpaceLongPress()
    /** Settings, scrolled to the keyboard layouts. */
    fun onOpenKeyboardSettings()
    fun onSuggestion(suggestion: Suggestion)
    fun onPasteClipboard(content: ClipboardContent)
    fun onPasteHistory(entry: ClipEntry)
    /** A larger image for the hold-to-preview popup. Blocking: call on an IO dispatcher. */
    fun loadPreview(entry: ClipEntry): Bitmap?
    /** Open an image clip or screenshot in the editor (slide up from its preview). */
    fun onEditClip(entry: ClipEntry)
    fun onSuggestionLongPress(suggestion: Suggestion)
    fun onAddToDictionary(word: String)
    fun onDismissStripPrompt()
    fun onToggleMode()
    /** Collapse to (or expand from) the slim bar. */
    fun onSetMinimized(minimized: Boolean)
    fun onOpenDictionary()
    fun onOpenVoiceSettings()
    fun onOpenModels()
    fun onOpenSetup()
    fun onStop()
    fun onTapToTalk()

    // Model picker (voice mode)
    fun onTogglePicker()
    fun onClosePicker()
    fun onSelectModel(id: String)
    fun onDownloadModel(id: String)
    fun onCancelDownload(id: String)
}
