package com.rheerkens.androidkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCursorTest {
    private var before = ""
    private var after = ""
    private var selected = ""

    /** Key events sent: a key press is two (down and up). */
    private var events = 0
    private val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, method, args ->
        when (method.name) {
            "sendKeyEvent" -> { events++; true }
            "getTextBeforeCursor" -> before.takeLast(args!![0] as Int)
            "getTextAfterCursor" -> after.take(args!![0] as Int)
            "getSelectedText" -> selected
            else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
        }
    } as InputConnection

    private fun bridge(multiLine: Boolean) = EditorBridge { connection }.apply {
        editorInfo = EditorInfo().apply {
            packageName = "test.editor"
            fieldId = 1
            inputType = InputType.TYPE_CLASS_TEXT or if (multiLine) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
        }
    }

    @Test fun movesAsFarAsTheSlideAsks() {
        before = "Hello world"
        after = "!"
        assertTrue(bridge(multiLine = false).moveCursor(-4, 0))
        assertEquals(8, events)
    }

    @Test fun stopsAtTheStartAndEndOfTheText() {
        // An arrow past the end would move the focus to another field in some apps.
        before = "ab"
        val b = bridge(multiLine = false)
        assertTrue(b.moveCursor(-5, 0))
        assertEquals(4, events)
        assertFalse(b.moveCursor(3, 0))
        assertEquals(4, events)
    }

    @Test fun anEmojiIsOneStep() {
        before = "😀😀"
        assertTrue(bridge(multiLine = false).moveCursor(-3, 0))
        assertEquals(4, events)
    }

    @Test fun linesMoveOnlyInAFieldWithSeveralLines() {
        before = "one\ntwo"
        after = "\nthree"
        assertFalse(bridge(multiLine = false).moveCursor(0, -1))
        assertEquals(0, events)
        assertTrue(bridge(multiLine = true).moveCursor(0, -1))
        assertEquals(2, events)
    }

    @Test fun noLineUpFromTheStartOrDownFromTheEnd() {
        after = "text"
        val b = bridge(multiLine = true)
        assertFalse(b.moveCursor(0, -1))
        assertTrue(b.moveCursor(0, 1))
        after = ""
        before = "text"
        assertFalse(b.moveCursor(0, 1))
        assertEquals(2, events)
    }

    @Test fun selectingHoldsShiftAroundTheArrows() {
        before = "Hello"
        assertTrue(bridge(multiLine = false).moveCursor(-2, 0, select = true))
        // Shift down, two arrows (down and up each), Shift up.
        assertEquals(6, events)
    }

    @Test fun aSelectionAlwaysTakesAnArrow() {
        // The arrow collapses it, also when nothing lies before it.
        selected = "all of it"
        assertTrue(bridge(multiLine = false).moveCursor(-1, 0))
        assertEquals(2, events)
    }
}
