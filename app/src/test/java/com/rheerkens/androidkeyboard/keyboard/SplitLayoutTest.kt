package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import com.rheerkens.androidkeyboard.settings.KeyArrangement
import org.junit.Assert.*
import org.junit.Test

class SplitLayoutTest {
    @Test fun qwertySharesCentralKeysAndSpansTheSpaceBarLikeGboard() {
        val kb = TypingKeyboard.all(TestLanguages.catalog).first { it.id == "en_US" }
        val rows = splitRows(Layouts.letters(kb), kb, Layer.LETTERS)
        fun letters(keys: List<Key>) = keys.filterIsInstance<Key.Char>().joinToString("") { it.output }
        assertEquals("qwert", letters(rows[0].left))
        assertEquals("yuiop", letters(rows[0].right))
        assertEquals("asdfg", letters(rows[1].left))
        assertEquals("ghjkl", letters(rows[1].right))
        assertEquals("zxcv", letters(rows[2].left))
        assertEquals("vbnm", letters(rows[2].right))
        assertTrue(rows[2].left.first() is Key.Shift)
        assertTrue(rows[2].right.last() is Key.Backspace)
        assertTrue(rows[3].fullWidth)
        assertEquals(1, rows[3].left.count { it is Key.Space })
        assertTrue(rows[3].left.last() is Key.Enter)
    }
    @Test fun everyLayoutPreservesCharactersWithOnlyCentralLetterDuplicates() {
        for (kb in TypingKeyboard.all(TestLanguages.catalog)) for (layer in Layer.entries) {
            val original = Layouts.forLayer(layer, kb)
            val split = splitRows(original, kb, layer)
            val expected = original.rows.flatten().filterIsInstance<Key.Char>().map { it.output }
            val actual = split.flatMap { it.left + it.right }.filterIsInstance<Key.Char>().map { it.output }
            assertEquals("$kb $layer", expected.toSet(), actual.toSet())
            assertEquals(expected.size + if (layer == Layer.LETTERS) 2 else 0, actual.size)
            assertEquals(1, split.flatMap { it.left + it.right }.count { it is Key.Space })
        }
    }
    @Test fun dvorakThirdRowUsesItsOwnBoundary() {
        val kb = TypingKeyboard.all(TestLanguages.catalog).first { it.arrangement == KeyArrangement.DVORAK }
        val row = splitRows(Layouts.letters(kb), kb, Layer.LETTERS)[2]
        assertEquals("qjkx", row.left.filterIsInstance<Key.Char>().joinToString("") { it.output })
    }
}
