package com.rheerkens.androidkeyboard.clipboard

import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardHistoryTest {
    private fun push(list: List<String>, entry: String, max: Int = 10) =
        ClipboardHistory.pushNewest(list, entry, max) { it }

    @Test
    fun newestComesFirst() {
        val (kept, evicted) = push(listOf("b", "a"), "c")
        assertEquals(listOf("c", "b", "a"), kept)
        assertEquals(emptyList<String>(), evicted)
    }

    @Test
    fun recopyingMovesAnOlderClipToTheFrontWithoutDuplicating() {
        assertEquals(listOf("a", "c", "b"), push(listOf("c", "b", "a"), "a").first)
    }

    @Test
    fun replacesAnEntryWithTheSameKey() {
        val (kept, _) = ClipboardHistory.pushNewest(listOf("a1", "b1"), "b2", 10) { it.first() }
        assertEquals(listOf("b2", "a1"), kept)
    }

    @Test
    fun oldestFallOffPastTheCap() {
        val (kept, evicted) = push(listOf("c", "b", "a"), "d", max = 3)
        assertEquals(listOf("d", "c", "b"), kept)
        assertEquals(listOf("a"), evicted)
    }

    @Test
    fun clearForgetsEveryClipAndRestoreBringsThemBackBehindNewerOnes() {
        val cache = Files.createTempDirectory("clips").toFile()
        val context = object : ContextWrapper(null) {
            override fun getCacheDir(): File = cache
        }
        val history = ClipboardHistory(context, clock = { 1_000L })
        history.record(ClipboardContent.Text("first", sensitive = false))
        history.record(ClipboardContent.Text("second", sensitive = false))
        assertEquals(2, history.entries.value.size)
        val cleared = history.clear()
        assertEquals(emptyList<ClipEntry>(), history.entries.value)
        history.record(ClipboardContent.Text("third", sensitive = false))
        assertEquals(listOf("third"), history.entries.value.map { (it as ClipEntry.Text).text })
        history.restore(cleared)
        assertEquals(listOf("third", "second", "first"), history.entries.value.map { (it as ClipEntry.Text).text })
        cache.deleteRecursively()
    }

    private val cache: File = Files.createTempDirectory("clips").toFile()
    private val context = object : ContextWrapper(null) {
        override fun getCacheDir(): File = cache
    }
    private var now = 0L
    private fun history(maxAgeMs: Long? = HOUR) = ClipboardHistory(context, clock = { now }, maxAgeMs = { maxAgeMs })
    private fun ClipboardHistory.texts() = entries.value.map { (it as ClipEntry.Text).text }
    private fun ClipboardHistory.shown() = current().map { (it as ClipEntry.Text).text }
    private fun text(value: String) = ClipboardContent.Text(value, sensitive = false)

    @Test
    fun textLongerThanTenPagesIsNotKept() {
        val history = history()
        history.record(text("x".repeat(ClipboardHistory.MAX_TEXT_CHARS)))
        history.record(text("y".repeat(ClipboardHistory.MAX_TEXT_CHARS + 1)))
        assertEquals(listOf(ClipboardHistory.MAX_TEXT_CHARS), history.texts().map { it.length })
    }

    @Test
    fun sensitiveClipsAreNotKept() {
        val history = history()
        history.record(ClipboardContent.Text("hunter2", sensitive = true))
        // A clip copied in a password or private field is treated the same way.
        history.record(text("copied in a password field").asSensitive())
        assertEquals(emptyList<String>(), history.texts())
    }

    @Test
    fun clipsExpireAnHourAfterTheyWereCopiedOrLastPasted() {
        val history = history()
        history.record(text("first"))
        now = 30 * MINUTE
        history.record(text("second"))
        now = 59 * MINUTE
        assertEquals(60 * MINUTE, history.prune())
        now = 60 * MINUTE
        // Hidden from the bar at once, forgotten at the next prune.
        assertEquals(listOf("second"), history.shown())
        assertEquals(listOf("second", "first"), history.texts())
        assertEquals(90 * MINUTE, history.prune())
        assertEquals(listOf("second"), history.texts())
        now = 80 * MINUTE
        history.markUsed(history.entries.value.single())
        now = 100 * MINUTE
        assertEquals(140 * MINUTE, history.prune())
        assertEquals(listOf("second"), history.texts())
        now = 140 * MINUTE
        assertEquals(null, history.prune())
        assertEquals(emptyList<String>(), history.texts())
    }

    @Test
    fun aClipCopiedBeforeTheKeyboardSawItKeepsItsCopyTime() {
        val history = history()
        now = 3 * HOUR
        history.record(ClipboardContent.Text("old", sensitive = false, copiedAtMs = HOUR))
        assertEquals(emptyList<String>(), history.texts())
    }

    @Test
    fun untilRestartKeepsEveryClip() {
        val history = history(maxAgeMs = null)
        history.record(text("first"))
        now = 30 * HOUR
        assertEquals(null, history.prune())
        assertEquals(listOf("first"), history.shown())
    }

    @Test
    fun undoClearDoesNotBringBackExpiredClips() {
        val history = history()
        history.record(text("old"))
        now = 50 * MINUTE
        history.record(text("new"))
        val cleared = history.clear()
        now = 70 * MINUTE
        history.restore(cleared)
        assertEquals(listOf("new"), history.texts())
    }

    @org.junit.After
    fun deleteCache() {
        cache.deleteRecursively()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
