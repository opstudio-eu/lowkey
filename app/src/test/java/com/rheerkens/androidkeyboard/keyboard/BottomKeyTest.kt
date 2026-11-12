package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.TestLanguages
import com.rheerkens.androidkeyboard.settings.BottomKey
import org.junit.Assert.*
import org.junit.Test

class BottomKeyTest {
    @Test fun emojiIsTheDefaultAndLanguageKeyRemainsAvailableInEveryLayer() {
        for (keyboard in TypingKeyboard.all(TestLanguages.catalog)) for (layer in Layer.entries) {
            val normal = Layouts.forLayer(layer, keyboard).rows.last()
            assertEquals(1, normal.count { it is Key.Emoji })
            assertEquals(0, normal.count { it is Key.Globe })
            assertEquals(1, normal.count { it is Key.Space })
            val language = Layouts.forLayer(layer, keyboard, bottomKey = BottomKey.LANGUAGE).rows.last()
            assertEquals(1, language.count { it is Key.Globe })
            assertEquals(0, language.count { it is Key.Emoji })
            assertTrue(language.filterIsInstance<Key.Char>().first { it.output == "," }.opensEmojiOnHold)
        }
    }
}
