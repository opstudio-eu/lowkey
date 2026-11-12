package com.rheerkens.androidkeyboard.companion.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.rheerkens.androidkeyboard.keyboard.ui.parseHexColor

class HexFieldInputTest {
    @Test
    fun normalizesCaseAndOptionalHash() {
        assertEquals("1E4632", hexFieldInput("1e4632"))
        assertEquals("1E4632", hexFieldInput(" #1e4632 "))
        assertEquals("1E-46 ZZ", hexFieldInput("1e-46 zz"))
        assertEquals("", hexFieldInput("#"))
    }

    @Test
    fun pastedAlphaStaysTooLongInsteadOfBecomingAnotherColour() {
        assertEquals("801E4632", hexFieldInput("#801E4632"))
        assertEquals("801E4632FF", hexFieldInput("801E4632FF"))
    }

    @Test
    fun invalidPasteCannotSilentlyBecomeAValidColor() {
        assertNull(parseHexColor(hexFieldInput("1E4632G")))
        assertNull(parseHexColor(hexFieldInput("1E-4632")))
        assertNull(parseHexColor(hexFieldInput("1E4632ZZZZ")))
    }
}
