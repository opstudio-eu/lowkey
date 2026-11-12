package com.rheerkens.androidkeyboard.ime

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class EditorContextTest {
    @Test fun oneSnapshotSuppliesWordTokenAndSentenceWithoutFurtherEditorCalls() {
        var reads = 0
        val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, method, _ ->
            if (method.name == "getTextBeforeCursor") { reads++; "Earlier sentence. \"recieve" }
            else if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        } as InputConnection
        val context = EditorBridge { connection }.readContext()
        repeat(10) {
            assertEquals("recieve", context.word)
            assertEquals("\"recieve", context.token)
            assertTrue(context.wordStartsSentence)
        }
        assertEquals(1, reads)
    }

    @Test fun boundedContextPreservesWordTokenAndSentenceRules() {
        assertEquals("a".repeat(48), EditorContext("a".repeat(120)).word)
        assertEquals("a".repeat(96), EditorContext("a".repeat(120)).token)
        assertFalse(EditorContext(" ".repeat(112)).wordStartsSentence)
        assertTrue(EditorContext("hello").wordStartsSentence)
        assertFalse(EditorContext("hello world").wordStartsSentence)
        assertTrue(EditorContext("hello! (world").wordStartsSentence)
        assertEquals("teh", EditorContext("https://teh").word)
        assertEquals("https://teh", EditorContext("https://teh").token)
    }
}
