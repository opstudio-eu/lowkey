package com.rheerkens.androidkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.rheerkens.androidkeyboard.keyboard.ui.EnterKind
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFanActionTest {
    /** Records what the bridge asked the editor to do. */
    private val calls = mutableListOf<String>()
    private val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, method, args ->
        when (method.name) {
            "performEditorAction" -> { calls += "action:${args!![0]}"; true }
            "sendKeyEvent" -> { calls += "key"; true }
            "commitText" -> { calls += "text:${args!![0]}"; true }
            else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        }
    } as InputConnection

    private fun bridge(imeOptions: Int, inputType: Int = InputType.TYPE_CLASS_TEXT) = EditorBridge { connection }.apply {
        editorInfo = EditorInfo().apply {
            packageName = "test.editor"
            fieldId = 1
            this.imeOptions = imeOptions
            this.inputType = inputType
        }
    }

    @Test
    fun aChatFieldThatHidesItsSendActionFromEnterStillSendsFromTheFan() {
        val b = bridge(
            EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
        )
        assertEquals(EnterKind.NEWLINE, b.enterKind())
        assertEquals(EnterKind.SEND, b.fanAction())
        assertTrue(b.fanNewLine())
        b.performFanAction()
        assertEquals(listOf("action:${EditorInfo.IME_ACTION_SEND}"), calls)
    }

    @Test
    fun aMultiLineFieldWithoutAnActionGetsAPlainEnterKeyAndANewLine() {
        val b = bridge(EditorInfo.IME_ACTION_UNSPECIFIED, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        assertNull(b.fanAction())
        assertTrue(b.fanNewLine())
        b.performFanAction()
        // Down and up.
        assertEquals(listOf("key", "key"), calls)
    }

    @Test
    fun aSingleLineSearchFieldHasNoSeparateNewLine() {
        val b = bridge(EditorInfo.IME_ACTION_SEARCH)
        assertEquals(EnterKind.SEARCH, b.fanAction())
        assertFalse(b.fanNewLine())
    }
}
