package com.rheerkens.androidkeyboard.companion.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HexFieldInputTest {
    @Test
    fun keepsHexDigitsInUpperCase() {
        assertEquals("1E4632", hexFieldInput("1e4632"))
        assertEquals("1E4632", hexFieldInput(" #1e4632 "))
        assertEquals("1E46", hexFieldInput("1e-46 zz"))
        assertEquals("", hexFieldInput("#"))
    }

    @Test
    fun pastedAlphaStaysTooLongInsteadOfBecomingAnotherColour() {
        assertEquals("801E4632", hexFieldInput("#801E4632"))
        assertEquals("801E4632", hexFieldInput("801E4632FF"))
    }
}
