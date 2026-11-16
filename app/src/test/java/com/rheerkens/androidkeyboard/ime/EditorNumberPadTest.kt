package com.rheerkens.androidkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.rheerkens.androidkeyboard.keyboard.NumberPad
import org.junit.Assert.*
import org.junit.Test

class EditorNumberPadTest {
    private fun padFor(type: Int?): NumberPad? {
        val bridge = EditorBridge { null }
        if (type != null) bridge.editorInfo = EditorInfo().apply { inputType = type }
        return bridge.numberPad
    }

    @Test fun numberPhoneAndDateFieldsGetTheirPad() {
        assertEquals(NumberPad.NUMBER, padFor(InputType.TYPE_CLASS_NUMBER))
        assertEquals(NumberPad.NUMBER, padFor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED))
        // A PIN is a number password: the pad, and still a sensitive field.
        assertEquals(NumberPad.NUMBER, padFor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertEquals(NumberPad.PHONE, padFor(InputType.TYPE_CLASS_PHONE))
        assertEquals(NumberPad.DATETIME, padFor(InputType.TYPE_CLASS_DATETIME))
        assertEquals(NumberPad.DATETIME, padFor(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME))
    }

    @Test fun textFieldsKeepTheLetters() {
        assertNull(padFor(null))
        assertNull(padFor(InputType.TYPE_NULL))
        assertNull(padFor(InputType.TYPE_CLASS_TEXT))
        assertNull(padFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertNull(padFor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS))
    }
}
