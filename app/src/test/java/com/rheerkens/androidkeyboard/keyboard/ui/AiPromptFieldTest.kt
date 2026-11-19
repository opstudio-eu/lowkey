package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.rheerkens.androidkeyboard.ai.ImageApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptFieldTest {
    @Test
    fun `typing on one line is left alone`() {
        val value = TextFieldValue("a cat in a hat", TextRange(5))
        assertSame(value, flattenPrompt(value))
    }

    @Test
    fun `pasted line breaks become one space and the cursor follows`() {
        val pasted = TextFieldValue("a cat\n\nin a hat", TextRange(15))
        val flat = flattenPrompt(pasted)
        assertEquals("a cat in a hat", flat.text)
        assertEquals(TextRange(14), flat.selection)
    }

    @Test
    fun `the prompt keeps to its length limit`() {
        val long = "x".repeat(ImageApi.MAX_PROMPT_CHARS + 10)
        val flat = flattenPrompt(TextFieldValue(long, TextRange(long.length)))
        assertEquals(ImageApi.MAX_PROMPT_CHARS, flat.text.length)
        assertEquals(TextRange(ImageApi.MAX_PROMPT_CHARS), flat.selection)
    }

    @Test
    fun `one typed line break is Enter, a pasted one is not`() {
        assertTrue(typedLineBreak("a cat", "a cat\n"))
        assertTrue(typedLineBreak("a cat", "a\n cat"))
        assertFalse(typedLineBreak("a cat", "a cat\nin a hat"))
        assertFalse(typedLineBreak("a cat", "a cats"))
    }
}
