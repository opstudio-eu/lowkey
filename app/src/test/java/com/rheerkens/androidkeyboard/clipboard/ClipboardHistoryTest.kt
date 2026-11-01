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
}
