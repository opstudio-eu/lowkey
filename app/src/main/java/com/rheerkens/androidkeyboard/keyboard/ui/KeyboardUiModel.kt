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
    NEWLINE(null), GO("Go"), SEND("Send"), SEARCH("Search"), NEXT("Next"), DONE("Done"), PREVIOUS("Prev"),
    /** While the keys type an image prompt: Enter creates the image. */
    CREATE("Create"),
    /** The prompt is empty: Create shows, dimmed, and does nothing yet. */
    CREATE_EMPTY("Create"),
    // The service isn't set up: Enter opens the setting that is missing, like the composer's button.
    ADD_KEY("Add key"), ADD_SERVER("Add server"), FIX_SERVER("Fix server"), FIX_KEY("Fix key"), FIX_TOKEN("Fix token");

    val dimmed: Boolean get() = this == CREATE_EMPTY
    /** Opens a setting the AI needs: the key shows a glyph, its label is what TalkBack says. */
    val opensSetup: Boolean get() = setupIsServer || this == ADD_KEY || this == FIX_KEY || this == FIX_TOKEN
    /** A setup key about the server's address (a server glyph); the others are about a key (a key glyph). */
    val setupIsServer: Boolean get() = this == ADD_SERVER || this == FIX_SERVER
}

/** The keyboard's AI (Settings › Experimental): ✨, the composer above the strip, and its status. */
data class AiUi(
    /** ✨ shows at the strip's right end: turned on, and not in a password, private or number field. */
    val available: Boolean = false,
    /** The action whose prompt the keys type into, instead of the editor; null when the composer is closed. */
    val promptAction: com.rheerkens.androidkeyboard.ai.AiAction? = null,
    val prompt: String = "",
    /** Where typing goes in [prompt]. */
    val cursor: Int = 0,
    /** Dictated words still being heard, shown after [prompt] until they are final. */
    val provisional: String = "",
    /** The defaults chip's choices are open where the keys were. */
    val optionsOpen: Boolean = false,
    /** Actions running in the background; ✨ shows how many, and the composer can stop each. */
    val tasks: List<com.rheerkens.androidkeyboard.ai.AiTask> = emptyList(),
    /** The image that just finished, offered with Insert and Preview until used or dismissed. */
    val ready: AiReady? = null,
    /** An action that failed, offered with Try again in the ready card's place. */
    val failure: AiFailure? = null,
    /** A note for the composer (a failure, the limit), which covers the strip where notes show. */
    val note: StripNote? = null,
    /** A dictation into the prompt is under way: Create waits for it. */
    val dictating: Boolean = false,
    // The defaults the chip shows.
    val service: com.rheerkens.androidkeyboard.ai.AiService = com.rheerkens.androidkeyboard.ai.AiService.OPENAI,
    val imageModel: String = com.rheerkens.androidkeyboard.ai.ImageModel.FLARE.id,
    val imageQuality: com.rheerkens.androidkeyboard.ai.ImageQuality = com.rheerkens.androidkeyboard.ai.ImageQuality.LOW,
    /** The model sent to an own server, empty for the server's default. */
    val serverModel: String = "",
    /** What the chosen service still needs (an API key, a server address), or null when it is set up. */
    val setup: com.rheerkens.androidkeyboard.ai.AiSetup? = null,
    /** The own server's host, for the options panel. */
    val serverHost: String = "",
) {
    val promptOpen: Boolean get() = promptAction != null
}

/** A failed action: what was asked, and why it failed, in a few words. */
data class AiFailure(
    val prompt: String,
    val message: String,
    /** The key or token was refused: the card offers this (such as "Edit key") instead of Try again. */
    val fixLabel: String? = null,
    /** The settings row [fixLabel] opens: the credential this request was sent with. */
    val fixSetting: String? = null,
    val id: Long = System.nanoTime(),
) {
    val cardId: String get() = "failure-$id"
}

/** A finished image: [pasteable] in this field; [onClipboard] when it is also the system clip. */
data class AiReady(val entry: ClipEntry.Image, val pasteable: Boolean, val onClipboard: Boolean)

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

/** The editor's own editing commands, offered under Keyboard tools. */
enum class EditAction(val label: String) {
    SELECT_ALL("Select all"),
    CUT("Cut"),
    COPY("Copy"),
    PASTE("Paste"),
    UNDO("Undo"),
}

/** Callbacks from the keyboard UI into the input method. */
interface KeyboardActions {
    /**
     * Moves the cursor [chars] characters right and [lines] lines down (negative: left, up), from
     * a slide that began on the pointing stick (the red dot by G).
     */
    fun onMoveCursor(chars: Int, lines: Int) {}
    /** A slide from the pointing stick (the red dot by G) began to move the cursor ([active]) or ended. */
    fun onCursorSlide(active: Boolean) {}
    fun onEditAction(action: EditAction) {}
    /** ✨ on the strip opens the composer; the ✕ in its place closes it (the draft stays). */
    fun onAiButton() {}
    /** The defaults chip: shows (or hides) the model and quality choices where the keys are. */
    fun onToggleAiOptions() {}
    /** Closes those choices (keyboard tools open in their place). */
    fun onCloseAiOptions() {}
    /** Stops an action running in the background. */
    fun onStopAiTask(id: Long) {}
    /** The service, model or quality picked under the chip, remembered for the next images. */
    fun onSelectAiService(service: com.rheerkens.androidkeyboard.ai.AiService) {}
    fun onSelectImageModel(id: String) {}
    /** The AI settings page (key, server address, token); with [setting], that row's editor opens. */
    fun onOpenAiSettings(setting: String? = null) {}
    /** How far the AI card above the keyboard reaches over the app, in pixels, so the app can move up. */
    fun onAiOverlayHeight(px: Int) {}
    fun onSelectImageQuality(quality: com.rheerkens.androidkeyboard.ai.ImageQuality) {}
    /** Sends the prompt (Create, or Enter); it runs in the background and the composer closes. */
    fun onAiPromptSubmit() {}
    /** Insert on the ready status. */
    /** [key]: the image the card showed, so a press held while a newer one replaced it does nothing. */
    fun onInsertAiResult(key: String) {}
    /** Dismisses the ready (or failed) card; an image stays in the clipboard row. */
    /** [card]: the ready image's key, or the failure's [AiFailure.cardId]; a stale card's ✕ does nothing. */
    fun onDismissAiResult(card: String) {}
    /** The ready card is being looked at ([holding], a preview is open): it doesn't time out meanwhile. */
    fun onAiCardHold(holding: Boolean) {}
    /** A tap on the prompt: its cursor goes to this character index. */
    fun onAiPromptCursor(index: Int) {}
    /** Try again on a failed action: the composer with its prompt; or, for a refused key, its editor in Settings. */
    fun onRetryAiFailure(id: Long) {}
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
    /**
     * A key released on the keys while the prompt was open (as this panel was drawn). If the prompt has
     * closed since (another finger tapped ✕), it was meant for the prompt and must not reach the app.
     */
    fun onPromptKey(key: Key) = onKey(key)
    /** A clip tapped in the prompt's strip; like [onPromptKey], dropped once the prompt has closed. */
    fun onPromptPasteHistory(entry: ClipEntry) = onPasteHistory(entry)
    /** The pointing stick over the prompt's keys; dropped once the prompt has closed. */
    fun onPromptMoveCursor(chars: Int, lines: Int) = onMoveCursor(chars, lines)
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
    /** The app's page for languages and their layouts. */
    fun onOpenKeyboardSettings()
    /** The app's Settings tab. */
    fun onOpenSettings() {}
    fun onSuggestion(suggestion: Suggestion)
    fun onPasteClipboard(content: ClipboardContent)
    fun onPasteHistory(entry: ClipEntry)
    /** Clear clips in the full keyboard's strip: what the minimized bar's fan does with Clear clips. */
    fun onClearClipHistory() = onFanCommand(FanCommand.CLEAR_CLIPBOARD)
    /** Undo clear in the full keyboard's strip, while the last clear can still be undone. */
    fun onUndoClearClipHistory() = onFanCommand(FanCommand.UNDO_CLEAR)
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
    /** Opens model selection from either keyboard form, without auto-starting capture. */
    fun onOpenVoicePicker() {}
    fun onDismissExpandHint() {}

    // Model picker (voice mode)
    fun onTogglePicker()
    fun onClosePicker()
    fun onSelectModel(id: String)
    fun onDownloadModel(id: String)
    fun onCancelDownload(id: String)
}
