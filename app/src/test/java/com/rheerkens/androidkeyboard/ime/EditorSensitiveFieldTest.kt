package com.rheerkens.androidkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class EditorSensitiveFieldTest {
    private fun info(type: Int, options: Int = 0) = EditorInfo().apply { inputType = type; imeOptions = options }

    @Test fun passwordsAndPrivateFieldsAreSensitiveWithOrWithoutTheKeyboard() {
        val text = InputType.TYPE_CLASS_TEXT
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) assertTrue(EditorBridge.isSensitive(info(text or variation)))
        assertTrue(EditorBridge.isSensitive(info(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)))
        assertTrue(EditorBridge.isSensitive(info(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)))
        assertFalse(EditorBridge.isSensitive(info(text)))
        assertFalse(EditorBridge.isSensitive(info(InputType.TYPE_CLASS_PHONE)))
        assertFalse(EditorBridge.isSensitive(null))

        val bridge = EditorBridge { null }
        bridge.editorInfo = info(text or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertTrue(bridge.isSensitiveField && bridge.isPasswordField)
        bridge.editorInfo = info(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertTrue(bridge.isSensitiveField && !bridge.isPasswordField)
    }
}
