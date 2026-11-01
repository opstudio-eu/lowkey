package com.rheerkens.androidkeyboard.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/** A chip in the minimized bar: a remembered clip, or a recent screenshot from the media library. */
sealed interface ClipEntry {
    /** Stable list key, unique across clips and screenshots. */
    val key: String
    /** When it was copied or taken; chips are ordered newest first. */
    val timeMs: Long

    data class Text(val id: Long, val text: String, override val timeMs: Long) : ClipEntry {
        override val key get() = "c$id"
    }

    /** Points at the keyboard's own copy, so it outlives the system clip. */
    data class Image(
        val id: Long,
        val uri: Uri,
        val mimeType: String,
        val thumbnail: Bitmap?,
        val sourceUri: Uri,
        val file: File,
        override val timeMs: Long,
    ) : ClipEntry {
        override val key get() = "c$id"
    }

    /** Not copied until pasted; see [ClipboardHistory.recordScreenshot]. */
    data class Screenshot(
        val mediaId: Long,
        val mediaUri: Uri,
        val mimeType: String,
        val thumbnail: Bitmap?,
        override val timeMs: Long,
    ) : ClipEntry {
        override val key get() = "s$mediaId"
    }
}

/**
 * The last clips copied while Lowkey Keyboard is the active keyboard, newest first, for the minimized
 * bar. Kept in memory for the process lifetime only. The system revokes access to a clip's image
 * when the next clip replaces it, so image bytes are copied into the app cache, served through
 * this app's FileProvider, and deleted on eviction and at start-up. Sensitive clips are not kept.
 */
class ClipboardHistory(
    private val context: Context,
    private val max: Int = MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dir = File(context.cacheDir, DIR).apply {
        deleteRecursively()
        mkdirs()
    }
    private val _entries = MutableStateFlow<List<ClipEntry>>(emptyList())
    val entries: StateFlow<List<ClipEntry>> = _entries.asStateFlow()
    private var nextId = 0L

    /** Call on an IO dispatcher: copying an image reads it from the source app's provider. */
    @Synchronized
    fun record(content: ClipboardContent) {
        if (content.sensitive) return
        // Order by when it was copied, not when the keyboard first saw it (e.g. after a restart).
        val now = content.copiedAtMs.takeIf { it in 1..clock() } ?: clock()
        val current = _entries.value
        val entry = when (content) {
            is ClipboardContent.Text -> {
                if (content.text.isBlank()) return
                current.filterIsInstance<ClipEntry.Text>().firstOrNull { it.text == content.text }?.copy(timeMs = now)
                    ?: ClipEntry.Text(nextId++, content.text, now)
            }
            is ClipboardContent.Image ->
                current.filterIsInstance<ClipEntry.Image>().firstOrNull { it.sourceUri == content.uri }?.copy(timeMs = now)
                    ?: copyImage(content.uri, content.mimeType, content.thumbnail, now)
                    ?: return
        }
        push(entry)
    }

    /**
     * Copies a screenshot into the history so the editor gets a read grant on this app's copy
     * rather than on the media library. Keeps the screenshot's time, so its chip doesn't jump.
     * Call on an IO dispatcher.
     */
    @Synchronized
    fun recordScreenshot(shot: ClipEntry.Screenshot): ClipEntry.Image? {
        val entry = _entries.value.filterIsInstance<ClipEntry.Image>().firstOrNull { it.sourceUri == shot.mediaUri }
            ?: copyImage(shot.mediaUri, shot.mimeType, shot.thumbnail, shot.timeMs)
            ?: return null
        push(entry)
        return entry
    }

    /**
     * Forgets every clip and returns them, so [restore] can bring them back. Their cached images
     * stay until [discard].
     */
    @Synchronized
    fun clear(): List<ClipEntry> {
        val old = _entries.value
        _entries.value = emptyList()
        return old
    }

    /** Brings back clips from [clear], behind anything copied since; past the cap the oldest go. */
    @Synchronized
    fun restore(cleared: List<ClipEntry>) {
        val all = _entries.value + cleared
        val kept = all.distinctBy { it.key }.take(max)
        _entries.value = kept
        deleteImages(all.filter { e -> kept.none { it.key == e.key } })
    }

    /** Deletes the cached images of cleared clips that won't come back. */
    @Synchronized
    fun discard(cleared: List<ClipEntry>) {
        val current = _entries.value
        deleteImages(cleared.filter { e -> current.none { it.key == e.key } })
    }

    private fun deleteImages(entries: List<ClipEntry>) {
        entries.forEach { (it as? ClipEntry.Image)?.file?.delete() }
    }

    private fun push(entry: ClipEntry) {
        val (kept, evicted) = pushNewest(_entries.value, entry, max) { it.key }
        _entries.value = kept
        evicted.forEach { (it as? ClipEntry.Image)?.file?.delete() }
    }

    private fun copyImage(source: Uri, mimeType: String, thumbnail: Bitmap?, timeMs: Long): ClipEntry.Image? {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "img"
        val id = nextId++
        val file = File(dir, "$id.$ext")
        return try {
            val input = context.contentResolver.openInputStream(source) ?: return null
            input.use { from ->
                file.outputStream().use { to ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = from.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_IMAGE_BYTES) throw IOException("Clipboard image too large")
                        to.write(buffer, 0, n)
                    }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
            ClipEntry.Image(id, uri, mimeType, thumbnail, source, file, timeMs)
        } catch (_: IOException) {
            file.delete()
            null
        } catch (_: SecurityException) {
            file.delete()
            null
        } catch (_: IllegalArgumentException) {
            file.delete()
            null
        }
    }

    companion object {
        const val MAX_ENTRIES = 10
        const val AUTHORITY_SUFFIX = ".clipboard"
        private const val DIR = "clipboard"
        private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024

        /**
         * Moves [entry] to the front, replacing any entry with the same key (re-copying an older
         * clip brings it back up), and caps the list. Returns the kept and the dropped entries.
         */
        fun <E> pushNewest(list: List<E>, entry: E, max: Int, key: (E) -> Any): Pair<List<E>, List<E>> {
            val all = listOf(entry) + list.filter { key(it) != key(entry) }
            return all.take(max) to all.drop(max)
        }

        /**
         * Chips for the bar: clips and screenshots newest first. A screenshot already pasted (and so
         * copied into the history) shows once, as its clip.
         */
        fun merge(history: List<ClipEntry>, screenshots: List<ClipEntry.Screenshot>, max: Int): List<ClipEntry> {
            val copied = history.mapNotNullTo(HashSet()) { (it as? ClipEntry.Image)?.sourceUri }
            return (history + screenshots.filter { it.mediaUri !in copied })
                .sortedByDescending { it.timeMs }
                .take(max)
        }
    }
}
