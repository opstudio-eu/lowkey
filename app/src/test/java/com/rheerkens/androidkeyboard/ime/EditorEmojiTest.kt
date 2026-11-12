package com.rheerkens.androidkeyboard.ime

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class EditorEmojiTest {
    @Test fun backspaceDeletesACompleteEmojiSequenceWithoutTouchingEarlierText() {
        val emoji = "👩🏽‍💻"
        var text = "Hello $emoji"
        val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, method, args ->
            when (method.name) {
                "getSelectedText" -> null
                "getTextBeforeCursor" -> text.takeLast(args!![0] as Int)
                "deleteSurroundingText" -> { text = text.dropLast(args!![0] as Int); true }
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            }
        } as InputConnection
        EditorBridge { connection }.deleteBackward(emoji)
        assertEquals("Hello ", text)
    }
}
