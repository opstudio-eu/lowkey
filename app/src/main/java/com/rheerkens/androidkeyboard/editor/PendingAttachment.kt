package com.rheerkens.androidkeyboard.editor

/** An edited image and its caption, waiting to be inserted into the field the editor came from. */
data class Attachment(
    val imageUri: String,
    val mimeType: String,
    val caption: String,
    val editorKey: String,
    val createdAt: Long,
)

/**
 * Hand-off between the image editor (Attach) and the keyboard. The keyboard records which field
 * opened the editor; Attach offers the result; when that same field gets input again, the keyboard
 * takes it once and inserts the caption and the image. Same process, memory only.
 */
object PendingAttachment {
    /** Editor key of the field the editor was opened from (see EditorBridge.keyOf). */
    @Volatile
    var sourceEditor: String? = null

    @Volatile
    private var pending: Attachment? = null

    fun offer(attachment: Attachment) {
        pending = attachment
    }

    /** The attachment for [editorKey], once; null for other fields or after [TTL_MS]. */
    @Synchronized
    fun take(editorKey: String?, now: Long): Attachment? {
        val a = pending ?: return null
        if (now - a.createdAt > TTL_MS) {
            pending = null
            return null
        }
        if (editorKey == null || a.editorKey != editorKey) return null
        pending = null
        return a
    }

    const val TTL_MS = 5 * 60_000L
}
