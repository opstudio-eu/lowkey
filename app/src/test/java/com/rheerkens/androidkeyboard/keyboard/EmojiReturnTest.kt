package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import com.rheerkens.androidkeyboard.settings.BottomKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiReturnTest {
    @Test fun returnSitsOnTheEmojiKeyInEveryLayer() {
        for (keyboard in TypingKeyboard.all(TestLanguages.catalog)) for (layer in Layer.entries) {
            val row = Layouts.forLayer(layer, keyboard).rows.last()
            val index = emojiReturnIndex(row)
            assertTrue("${keyboard.id} $layer", row[index] is Key.Emoji)
        }
    }

    @Test fun withTheLanguageKeyTheReturnSitsOnTheCommaThatOpensEmoji() {
        for (keyboard in TypingKeyboard.all(TestLanguages.catalog)) for (layer in Layer.entries) {
            val row = Layouts.forLayer(layer, keyboard, bottomKey = BottomKey.LANGUAGE).rows.last()
            val key = row[emojiReturnIndex(row)]
            assertTrue("${keyboard.id} $layer", key is Key.Char && key.opensEmojiOnHold)
        }
    }

    @Test fun rowsWithoutAnEmojiOpenerFallBackToSpaceThenTheFirstKey() {
        assertEquals(1, emojiReturnIndex(listOf(Key.Char("a"), Key.Space(), Key.Enter())))
        assertEquals(0, emojiReturnIndex(listOf(Key.Char("a"), Key.Enter())))
        assertEquals(0, emojiReturnIndex(emptyList()))
    }
}
