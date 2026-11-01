package com.rheerkens.androidkeyboard.ime

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class EditorClearAllTest {
    private class Editor(var text: String, var supportsSelectAll: Boolean = true) {
        var selected = false
        val calls = mutableListOf<String>()
        val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, method, args ->
            when (method.name) {
                "beginBatchEdit", "endBatchEdit", "finishComposingText" -> { calls += method.name; true }
                "performContextMenuAction" -> {
                    assertEquals(android.R.id.selectAll, args!![0])
                    calls += "selectAll"
                    selected = supportsSelectAll
                    supportsSelectAll
                }
                "commitText" -> {
                    calls += "commitText"
                    text = if (selected) args!![0].toString() else text + args!![0].toString()
                    selected = false
                    true
                }
                "getTextBeforeCursor", "getTextAfterCursor" -> text.take(1) // Deliberately truncated.
                "getSelectedText" -> if (selected) text else null
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            }
        } as InputConnection
    }

    private fun info(id: Int) = EditorInfo().apply {
        packageName = "test.editor"
        fieldId = id
        hintText = "Message"
    }

    @Test
    fun clearsWholeFieldEvenWhenSurroundingTextIsTruncated() {
        val editor = Editor("First line\n" + "hello 😀 ".repeat(2_000))
        val bridge = EditorBridge { editor.connection }.apply { editorInfo = info(1) }
        assertTrue(bridge.clearAll(bridge.captureTarget()!!))
        assertEquals("", editor.text)
        assertEquals(listOf("beginBatchEdit", "finishComposingText", "selectAll", "commitText", "endBatchEdit"), editor.calls)
    }

    @Test
    fun refusesWhenConnectionOrFieldChangedAfterPrompt() {
        val original = Editor("Keep original")
        val other = Editor("Keep other")
        var current = original.connection
        val bridge = EditorBridge { current }.apply { editorInfo = info(1) }
        val target = bridge.captureTarget()!!
        current = other.connection
        assertFalse(bridge.clearAll(target))
        current = original.connection
        bridge.editorInfo = info(2)
        assertFalse(bridge.clearAll(target))
        assertTrue(original.calls.isEmpty())
        assertTrue(other.calls.isEmpty())
        assertEquals("Keep original", original.text)
        assertEquals("Keep other", other.text)
    }

    @Test
    fun unsupportedSelectAllNeverFallsBackToPartialDeletion() {
        val editor = Editor("Keep the whole field", supportsSelectAll = false)
        val bridge = EditorBridge { editor.connection }.apply { editorInfo = info(1) }
        assertFalse(bridge.clearAll(bridge.captureTarget()!!))
        assertEquals("Keep the whole field", editor.text)
        assertFalse("commitText" in editor.calls)
        assertEquals("endBatchEdit", editor.calls.last())
    }
}
