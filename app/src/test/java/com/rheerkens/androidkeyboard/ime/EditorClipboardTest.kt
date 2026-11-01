package com.rheerkens.androidkeyboard.ime

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class EditorClipboardTest {
    private class Editor {
        val committed = mutableListOf<String>()
        val connection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "commitText" -> { committed += args!![0].toString(); true }
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            }
        } as InputConnection
    }

    private fun info(id: Int) = EditorInfo().apply { packageName = "test.editor"; fieldId = id }

    @Test
    fun textPasteKeepsCaseEmojiNewlinesAndWhitespace() {
        val editor = Editor()
        val bridge = EditorBridge { editor.connection }.apply { editorInfo = info(1) }
        val text = "  MiXeD 😀\nsecond line \t"
        assertTrue(bridge.pasteText(bridge.captureTarget()!!, text))
        assertEquals(listOf(text), editor.committed)
    }

    @Test
    fun pendingPasteCannotFollowFocusToAnotherConnectionOrField() {
        val original = Editor()
        val other = Editor()
        var current = original.connection
        val bridge = EditorBridge { current }.apply { editorInfo = info(1) }
        val target = bridge.captureTarget()!!
        current = other.connection
        assertFalse(bridge.pasteText(target, "Private clip"))
        current = original.connection
        bridge.editorInfo = info(2)
        assertFalse(bridge.pasteText(target, "Private clip"))
        assertTrue(original.committed.isEmpty())
        assertTrue(other.committed.isEmpty())
    }
}
