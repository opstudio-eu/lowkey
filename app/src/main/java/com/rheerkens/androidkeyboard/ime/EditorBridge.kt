package com.rheerkens.androidkeyboard.ime

import android.os.SystemClock
import android.content.ClipDescription
import android.net.Uri
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import com.rheerkens.androidkeyboard.keyboard.BackspaceScrub
import com.rheerkens.androidkeyboard.keyboard.ui.EnterKind

/**
 * Thin wrapper over the host editor's [InputConnection]. All text changes from the keyboard
 * and from dictation go through here.
 */
class EditorBridge(private val connection: () -> InputConnection?) {
    var editorInfo: EditorInfo? = null

    private val ic: InputConnection? get() = connection()

    /** Bound to both the connection and field metadata, so delayed actions cannot cross fields. */
    class Target internal constructor(internal val connection: InputConnection, internal val key: String)

    fun captureTarget(): Target? {
        val c = ic ?: return null
        return Target(c, editorKey() ?: return null)
    }

    fun isCurrent(target: Target): Boolean = ic === target.connection && editorKey() == target.key

    fun hasText(): Boolean {
        val c = ic ?: return false
        return !c.getSelectedText(0).isNullOrEmpty() || !c.getTextBeforeCursor(1, 0).isNullOrEmpty() ||
            !c.getTextAfterCursor(1, 0).isNullOrEmpty()
    }

    fun hasSelection(): Boolean = !ic?.getSelectedText(0).isNullOrEmpty()

    /** Select all through the editor, not a possibly truncated surrounding-text snapshot. */
    fun clearAll(target: Target): Boolean {
        if (!isCurrent(target)) return false
        val c = target.connection
        c.beginBatchEdit()
        return try {
            c.finishComposingText()
            if (!c.performContextMenuAction(android.R.id.selectAll)) false else c.commitText("", 1)
        } finally {
            c.endBatchEdit()
        }
    }

    /**
     * Deletes all text before the cursor, however long, and keeps the text after it. Refuses with a
     * selection, where "before the cursor" is ambiguous.
     */
    fun deleteAllBeforeCursor(target: Target): Boolean {
        if (!isCurrent(target)) return false
        val c = target.connection
        if (!c.getSelectedText(0).isNullOrEmpty()) return false
        c.finishComposingText()
        return deleteEverythingBefore(c)
    }

    /**
     * Deletes the text before the cursor in [MAX_SCRUB]-sized pieces, each the exact length the
     * editor reports: some editors turn a delete count into that many key presses, so never ask
     * for more than is there. Not in one batch edit, where some editors' reads would be stale.
     */
    private fun deleteEverythingBefore(c: InputConnection): Boolean {
        repeat(MAX_DELETE_CHUNKS) {
            val n = c.getTextBeforeCursor(MAX_SCRUB, 0)?.length ?: return false
            if (n == 0) return true
            if (!c.deleteSurroundingText(n, 0)) return false
            if (n < MAX_SCRUB) return true
        }
        return true
    }

    /** Text around the cursor for a hold-and-slide Backspace; null with a selection. */
    fun scrubSnapshot(target: Target): BackspaceScrub? {
        if (!isCurrent(target)) return null
        val c = target.connection
        if (!c.getSelectedText(0).isNullOrEmpty()) return null
        val before = c.getTextBeforeCursor(MAX_SCRUB, 0) ?: return null
        val after = c.getTextAfterCursor(MAX_SCRUB, 0) ?: return null
        return BackspaceScrub(
            before.toString(), after.toString(),
            truncatedBefore = before.length >= MAX_SCRUB, truncatedAfter = after.length >= MAX_SCRUB,
        )
    }

    /**
     * Moves a hold-and-slide Backspace from keeping [kept] characters of [scrub]'s text before the
     * cursor to [level], deleting or restoring words before the cursor. The text after the cursor
     * is never touched. Returns false, without editing, if the cursor or the text beside it is not
     * where the slider left it (moved, or an edit the editor rejected), or if the editor rejects an
     * edit now.
     */
    fun scrubTo(target: Target, scrub: BackspaceScrub, kept: Int, level: Int): Boolean =
        scrubToKept(target, scrub, kept, scrub.kept(level), pastSnapshot = level >= scrub.top && scrub.topIsFinal)

    /**
     * Like [scrubTo], to keeping [toKept] characters of the snapshot before the cursor. Only with
     * [pastSnapshot] does it also delete the text before the snapshot, which can't be put back.
     */
    fun scrubToKept(target: Target, scrub: BackspaceScrub, kept: Int, toKept: Int, pastSnapshot: Boolean = false): Boolean {
        if (!isCurrent(target)) return false
        val c = target.connection
        if (!c.getSelectedText(0).isNullOrEmpty()) return false
        val before = c.getTextBeforeCursor(SCRUB_CHECK, 0)?.toString() ?: return false
        val after = c.getTextAfterCursor(SCRUB_CHECK, 0)?.toString() ?: return false
        if (!matchesEdge(before, scrub.before.substring(0, kept), atStart = false, scrub.truncatedBefore)) return false
        if (!matchesEdge(after, scrub.after, atStart = true, scrub.truncatedAfter)) return false
        // The slider's top on text longer than the snapshot: this reaches text it can't put back.
        if (pastSnapshot) return deleteEverythingBefore(c)
        c.beginBatchEdit()
        return try {
            var ok = true
            if (toKept < kept) ok = c.deleteSurroundingText(kept - toKept, 0)
            if (ok && toKept > kept) ok = c.commitText(scrub.before.substring(kept, toKept), 1)
            ok
        } finally {
            c.endBatchEdit()
        }
    }

    /**
     * Whether [actual], read next to the cursor (up to [SCRUB_CHECK] chars), is the edge of
     * [expected]; exactly it where the editor returned less than asked and nothing lies beyond.
     */
    private fun matchesEdge(actual: String, expected: String, atStart: Boolean, truncated: Boolean): Boolean {
        val edge = if (atStart) expected.take(SCRUB_CHECK) else expected.takeLast(SCRUB_CHECK)
        if (actual.length < SCRUB_CHECK && !truncated) return actual == edge
        return if (atStart) actual.startsWith(edge) else actual.endsWith(edge)
    }

    fun commitText(text: String) {
        ic?.commitText(text, 1)
    }

    /** Paste literally at the selection; no autocorrection, casing, or added spaces. */
    fun pasteText(target: Target, text: String): Boolean {
        if (!isCurrent(target)) return false
        return target.connection.commitText(text, 1)
    }

    fun acceptsImage(mimeType: String): Boolean =
        editorInfo?.contentMimeTypes?.any { ClipDescription.compareMimeTypes(mimeType, it) } == true

    /** Give the receiving editor temporary read access to the clipboard image. */
    fun pasteImage(target: Target, uri: Uri, mimeType: String): Boolean {
        if (!isCurrent(target) || !acceptsImage(mimeType)) return false
        target.connection.finishComposingText()
        return target.connection.commitContent(
            InputContentInfo(uri, ClipDescription("Clipboard image", arrayOf(mimeType)), null),
            InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
    }

    /** Returns false if the editor is gone (its connection no longer accepts edits). */
    fun setComposingText(text: String): Boolean = ic?.setComposingText(text, 1) ?: false

    fun finishComposingText() {
        ic?.finishComposingText()
    }

    /**
     * Removes the composing region's text without touching committed text.
     * Returns false if the editor is gone and the composing text stayed.
     */
    fun clearComposingText(): Boolean {
        val c = ic ?: return false
        c.beginBatchEdit()
        val ok = c.setComposingText("", 1)
        c.finishComposingText()
        c.endBatchEdit()
        return ok
    }

    /**
     * Identifies the focused editor for a dictation session: if this changes, the session's
     * insertion point is gone. Fields inside one view (for example Compose) share an ID, so the
     * key also includes the input type, action, and hint/label.
     */
    fun editorKey(): String? = editorInfo?.let(::keyOf)

    /** Deletes the selection, a matching complete emoji sequence, or one code point. */
    fun deleteBackward(sequence: String? = null) {
        val c = ic ?: return
        val selected = c.getSelectedText(0)
        when {
            !selected.isNullOrEmpty() -> c.commitText("", 1)
            sequence != null && sequence.isNotEmpty() &&
                c.getTextBeforeCursor(sequence.length, 0)?.toString()?.endsWith(sequence) == true ->
                c.deleteSurroundingText(sequence.length, 0)
            c.getTextBeforeCursor(1, 0).isNullOrEmpty() -> sendKey(KeyEvent.KEYCODE_DEL)
            else -> c.deleteSurroundingTextInCodePoints(1, 0)
        }
    }

    /** The editor's current selection, asked synchronously; null if the editor won't say. */
    fun currentSelection(): Pair<Int, Int>? {
        val extracted = ic?.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (extracted.selectionStart < 0) return null
        return (extracted.startOffset + extracted.selectionStart) to (extracted.startOffset + extracted.selectionEnd)
    }

    fun textBeforeCursor(n: Int): String = ic?.getTextBeforeCursor(n, 0)?.toString().orEmpty()

    /** The partial word directly before the cursor (letters, digits, `_` and `'`). */
    fun currentWordBeforeCursor(): String {
        val before = textBeforeCursor(MAX_WORD)
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return before.substring(i)
    }

    /** The whitespace-delimited token before the cursor (e.g. `https://teh` or `user@teh`). */
    fun currentTokenBeforeCursor(): String = textBeforeCursor(MAX_TOKEN).takeLastWhile { !it.isWhitespace() }

    /** True if the [wordLength] characters before the cursor start a sentence (or the field). */
    fun wordStartsSentence(wordLength: Int): Boolean {
        val raw = textBeforeCursor(wordLength + SENTENCE_CONTEXT).dropLast(wordLength)
        val before = raw.trimEnd(' ', '\t', '"', '\'', '(')
        // Empty context is the field start only if the editor returned less than asked for;
        // a long run of spaces is not a sentence start.
        if (before.isEmpty()) return raw.length < SENTENCE_CONTEXT
        return before.last() in ".!?\n"
    }

    /** Replaces the [count] characters before the cursor with [replacement]. */
    fun replaceBeforeCursor(count: Int, replacement: String) {
        val c = ic ?: return
        c.beginBatchEdit()
        c.deleteSurroundingText(count, 0)
        c.commitText(replacement, 1)
        c.endBatchEdit()
    }

    /** Replaces the partial word before the cursor with [replacement]. */
    fun replaceCurrentWord(replacement: String) {
        val c = ic ?: return
        val word = currentWordBeforeCursor()
        c.beginBatchEdit()
        if (word.isNotEmpty()) c.deleteSurroundingText(word.length, 0)
        c.commitText(replacement, 1)
        c.endBatchEdit()
    }

    /** True when the editor asks for a capital letter at the cursor (sentence start). */
    fun capsModeAtCursor(): Boolean {
        val info = editorInfo ?: return false
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return (ic?.getCursorCapsMode(info.inputType) ?: 0) != 0
    }

    val isPasswordField: Boolean
        get() {
            val type = editorInfo?.inputType ?: return false
            val variation = type and InputType.TYPE_MASK_VARIATION
            return when (type and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_TEXT ->
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else -> false
            }
        }

    /**
     * Password fields and fields that ask keyboards not to learn (incognito / private).
     * Voice input is never offered there.
     */
    val isSensitiveField: Boolean
        get() = isPasswordField ||
            (editorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0

    private val isMultiLine: Boolean
        get() = (editorInfo?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

    fun enterKind(): EnterKind {
        val info = editorInfo ?: return EnterKind.NEWLINE
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterKind.NEWLINE
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterKind.GO
            EditorInfo.IME_ACTION_SEND -> EnterKind.SEND
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.SEARCH
            EditorInfo.IME_ACTION_NEXT -> EnterKind.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterKind.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> EnterKind.PREVIOUS
            else -> EnterKind.NEWLINE
        }
    }

    /**
     * The action the editor declares, for the Backspace fan, or null when it declares none. Unlike
     * [enterKind] this ignores IME_FLAG_NO_ENTER_ACTION: multi-line chat fields often set that flag
     * (Enter types a new line there) while still declaring Send, which is what the fan offers.
     */
    fun fanAction(): EnterKind? {
        val info = editorInfo ?: return null
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterKind.GO
            EditorInfo.IME_ACTION_SEND -> EnterKind.SEND
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.SEARCH
            EditorInfo.IME_ACTION_NEXT -> EnterKind.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterKind.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> EnterKind.PREVIOUS
            else -> null
        }
    }

    /** The fan's New line works only in fields that take more than one line. */
    fun fanNewLine(): Boolean = isMultiLine

    /** Runs the editor's declared action ([fanAction]), or sends a plain Enter key where it declares none. */
    fun performFanAction() {
        val c = ic ?: return
        val info = editorInfo
        if (fanAction() != null && info != null) c.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        else sendKey(KeyEvent.KEYCODE_ENTER)
    }

    /** Runs the editor's IME action, or inserts a newline. */
    fun performEnter() {
        val c = ic ?: return
        val info = editorInfo
        val kind = enterKind()
        if (kind != EnterKind.NEWLINE && info != null) {
            c.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        } else if (isMultiLine) {
            c.commitText("\n", 1)
        } else {
            sendKey(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun sendKey(code: Int) {
        val c = ic ?: return
        val now = SystemClock.uptimeMillis()
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE))
    }

    companion object {
        private const val MAX_WORD = 48
        private const val MAX_TOKEN = 96
        private const val SENTENCE_CONTEXT = 64
        /** Hold-and-slide Backspace reaches this far on each side of the cursor. */
        private const val MAX_SCRUB = 10_000
        /** How much text on each side of the cursor confirms it hasn't moved during the slide. */
        private const val SCRUB_CHECK = 64
        /** Deleting everything before the cursor stops after this many [MAX_SCRUB] pieces (a million characters). */
        private const val MAX_DELETE_CHUNKS = 100
        fun isWordChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_' || ch == '\''

        /** Identifies a field across connections, e.g. the chat box an image editor returns to. */
        fun keyOf(info: EditorInfo): String =
            listOf(info.packageName, info.fieldId, info.fieldName, info.inputType, info.imeOptions, info.hintText, info.label)
                .joinToString("|")
    }
}
