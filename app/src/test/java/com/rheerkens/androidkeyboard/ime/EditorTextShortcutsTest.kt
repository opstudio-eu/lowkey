package com.rheerkens.androidkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class EditorTextShortcutsTest {
    @Test fun textShortcutsAreRestrictedToOrdinaryTextFields() {
        val bridge = EditorBridge { null }
        assertFalse(bridge.allowsTextShortcuts)
        bridge.editorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertTrue(bridge.allowsTextShortcuts)
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_FILTER,
        )) {
            bridge.editorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or variation }
            assertFalse("variation $variation", bridge.allowsTextShortcuts)
        }
        for (type in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)) {
            bridge.editorInfo = EditorInfo().apply { inputType = type }
            assertFalse(bridge.allowsTextShortcuts)
        }
        bridge.editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        assertFalse(bridge.allowsTextShortcuts)
    }
}
