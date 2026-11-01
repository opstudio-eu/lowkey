package com.rheerkens.androidkeyboard.ime

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class EditorScrubTest {
    /** A plain-text editor with a cursor; [rejectEdits] makes edits fail, [ignoreEdits] makes them silently do nothing. */
    private class Editor(var text: String, var cursor: Int = text.length) {
        var rejectEdits = false
        var ignoreEdits = false
        /** Every count deleteSurroundingText was asked to delete before the cursor. */
        val deleteRequests = mutableListOf<Int>()
        private var selectedAll = false
        val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, method, args ->
            when (method.name) {
                "beginBatchEdit", "endBatchEdit", "finishComposingText" -> true
                "getTextBeforeCursor" -> text.substring(maxOf(0, cursor - args!![0] as Int), cursor)
                "getTextAfterCursor" -> text.substring(cursor, minOf(text.length, cursor + args!![0] as Int))
                "getSelectedText" -> null
                "performContextMenuAction" -> { selectedAll = true; true }
                "deleteSurroundingText" -> edit {
                    // Asking for more than there is deletes what there is (InputConnection's contract).
                    deleteRequests += args!![0] as Int
                    val before = minOf(args[0] as Int, cursor)
                    val after = minOf(args[1] as Int, text.length - cursor)
                    text = text.removeRange(cursor, cursor + after).removeRange(cursor - before, cursor)
                    cursor -= before
                }
                "commitText" -> edit {
                    val inserted = args!![0].toString()
                    if (selectedAll) { text = ""; cursor = 0; selectedAll = false }
                    text = text.substring(0, cursor) + inserted + text.substring(cursor)
                    if (args[1] as Int > 0) cursor += inserted.length
                }
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            }
        } as InputConnection

        private fun edit(change: () -> Unit): Boolean {
            if (rejectEdits) return false
            if (!ignoreEdits) change()
            return true
        }

        override fun toString() = text.substring(0, cursor) + "|" + text.substring(cursor)
    }

    private fun bridgeFor(editor: Editor) = EditorBridge { editor.connection }.apply {
        editorInfo = EditorInfo().apply { packageName = "test.editor"; fieldId = 1 }
    }

    /** Steps through [levels] like the service does, returning the editor after each. */
    private fun slide(editor: Editor, vararg levels: Int): List<String> {
        val bridge = bridgeFor(editor)
        val target = bridge.captureTarget()!!
        val scrub = bridge.scrubSnapshot(target)!!
        var kept = scrub.before.length
        return levels.map { level ->
            assertTrue(bridge.scrubTo(target, scrub, kept, level))
            kept = scrub.kept(level)
            editor.toString()
        }
    }

    @Test
    fun erasesWordsAndBringsThemBackAroundTheCursor() {
        val editor = Editor("one two three four", cursor = 13)
        assertEquals(
            listOf("one two | four", "one | four", "| four", "one | four", "one two three| four"),
            slide(editor, 1, 2, 3, 2, 0),
        )
    }

    @Test
    fun stopsWithoutEditingWhenTheCursorMoved() {
        val editor = Editor("one two three")
        val bridge = bridgeFor(editor)
        val target = bridge.captureTarget()!!
        val scrub = bridge.scrubSnapshot(target)!!
        assertTrue(bridge.scrubTo(target, scrub, scrub.before.length, 1))
        editor.cursor = 0 // Moved with a physical keyboard mid-slide.
        assertFalse(bridge.scrubTo(target, scrub, scrub.kept(1), 0))
        assertEquals("|one two ", editor.toString())
    }

    @Test
    fun rejectedOrIgnoredEditsStopTheSlide() {
        val rejecting = Editor("one two three").apply { rejectEdits = true }
        val bridge = bridgeFor(rejecting)
        val target = bridge.captureTarget()!!
        val scrub = bridge.scrubSnapshot(target)!!
        assertFalse(bridge.scrubTo(target, scrub, scrub.before.length, 1))

        val ignoring = Editor("one two three").apply { ignoreEdits = true }
        val bridge2 = bridgeFor(ignoring)
        val target2 = bridge2.captureTarget()!!
        val scrub2 = bridge2.scrubSnapshot(target2)!!
        assertTrue(bridge2.scrubTo(target2, scrub2, scrub2.before.length, 1))
        // The next step sees that "three" is still there and restores nothing twice.
        ignoring.ignoreEdits = false
        assertFalse(bridge2.scrubTo(target2, scrub2, scrub2.kept(1), 0))
        assertEquals("one two three|", ignoring.toString())
    }

    @Test
    fun topKeepsTheTextAfterTheCursor() {
        val editor = Editor("Dear Sam, thanks for the call. Best, Roy", cursor = 30)
        val scrub = bridgeFor(editor).let { it.scrubSnapshot(it.captureTarget()!!)!! }
        assertEquals(listOf("| Best, Roy", "Dear Sam, thanks for the call.| Best, Roy"), slide(editor, scrub.top, 0))
    }

    @Test
    fun erasesTextLongerThanTheSnapshotBeforeTheCursorButKeepsTheRest() {
        val editor = Editor("word ".repeat(2_010) + "tail", cursor = 10_050) // 10,050 characters before the cursor.
        val bridge = bridgeFor(editor)
        val target = bridge.captureTarget()!!
        val scrub = bridge.scrubSnapshot(target)!!
        assertTrue(scrub.topIsFinal)
        assertTrue(bridge.scrubTo(target, scrub, scrub.before.length, 3))
        assertTrue(bridge.scrubTo(target, scrub, scrub.kept(3), scrub.top))
        assertEquals("|tail", editor.toString())
        assertTrue(editor.deleteRequests.all { it <= 10_000 })
    }

    @Test
    fun longTextAfterTheCursorStaysReversible() {
        val editor = Editor("one two" + " x".repeat(5_001), cursor = 7)
        val scrub = bridgeFor(editor).let { it.scrubSnapshot(it.captureTarget()!!)!! }
        assertFalse(scrub.topIsFinal)
        val states = slide(editor, scrub.top, 0)
        assertTrue(states[0].startsWith("| x x"))
        assertEquals("one two|" + " x".repeat(5_001), states[1])
    }

    @Test
    fun deleteAllBeforeCursorKeepsTheRestAndNeverAsksForMoreThanIsThere() {
        val editor = Editor("word ".repeat(3_000) + "tail", cursor = 15_000)
        val bridge = bridgeFor(editor)
        assertTrue(bridge.deleteAllBeforeCursor(bridge.captureTarget()!!))
        assertEquals("|tail", editor.toString())
        // Some editors turn the count into key presses: exact pieces, never a huge number.
        assertEquals(listOf(10_000, 5_000), editor.deleteRequests)
        val short = Editor("one two| three".replace("|", ""), cursor = 7)
        val bridge2 = bridgeFor(short)
        assertTrue(bridge2.deleteAllBeforeCursor(bridge2.captureTarget()!!))
        assertEquals(listOf(7), short.deleteRequests)
        assertEquals("| three", short.toString())
    }

    @Test
    fun pointingAtAWordBeforeTheSnapshotOnLongTextStaysReversible() {
        val original = "old ".repeat(3_750) + "tail" // 15,000 characters before the cursor.
        val editor = Editor(original, cursor = 15_000)
        val bridge = bridgeFor(editor)
        val target = bridge.captureTarget()!!
        val scrub = bridge.scrubSnapshot(target)!!
        assertTrue(scrub.topIsFinal)
        // Word aim at a word older than the snapshot keeps 0 of it: only the snapshot goes.
        assertTrue(bridge.scrubToKept(target, scrub, scrub.before.length, 0))
        assertEquals(5_000, editor.cursor)
        // Back over the keyboard: everything returns.
        assertTrue(bridge.scrubToKept(target, scrub, 0, scrub.before.length))
        assertEquals(original, editor.text)
        assertEquals(15_000, editor.cursor)
    }
}
