package com.rheerkens.androidkeyboard.settings

import androidx.compose.runtime.saveable.SaverScope
import com.rheerkens.androidkeyboard.companion.screens.DraftSaver
import com.rheerkens.androidkeyboard.keyboard.ui.ColorRole
import com.rheerkens.androidkeyboard.keyboard.ui.CustomKeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.DEFAULT_DIM
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardSeed
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.MAX_DIM
import com.rheerkens.androidkeyboard.keyboard.ui.MIN_DIM
import com.rheerkens.androidkeyboard.keyboard.ui.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeCodecTest {
    private fun hex(s: String) = parseHexColor(s)!!

    private val forest = KeyboardSeed(
        background = hex("0F2A1D"), keys = hex("1E4632"), functionKeys = hex("163826"),
        text = hex("F1FAF4"), accent = hex("F2B33D"),
    )
    private val image = "bg-0123456789abcdef0123456789abcdef.jpg"
    private val full = CustomKeyboardTheme(
        forest,
        overrides = mapOf(ColorRole.MUTED to hex("A0B0C0"), ColorRole.DANGER to hex("7A1FA2")),
        image = image,
        dim = 35,
    )

    @Test
    fun aThemeSavedBeforeOverridesLoadsUnchanged() {
        // Only the two keys the first release wrote.
        val old = StoredKeyboardTheme(enabled = true, colors = "0F2A1D,1E4632,163826,F1FAF4,F2B33D")
        val theme = KeyboardThemeCodec.decode(old)
        assertEquals(KeyboardTheme(true, CustomKeyboardTheme(forest)), theme)
        assertTrue(theme.custom!!.overrides.isEmpty())
        assertNull(theme.custom!!.image)
        assertEquals(DEFAULT_DIM, theme.custom!!.dim)
        // And it is written back in the same seed format.
        assertEquals(old.colors, KeyboardThemeCodec.encode(theme).colors)
    }

    @Test
    fun everythingRoundTrips() {
        for (enabled in listOf(true, false)) {
            val theme = KeyboardTheme(enabled, full)
            assertEquals(theme, KeyboardThemeCodec.decode(KeyboardThemeCodec.encode(theme)))
        }
    }

    @Test
    fun turningCustomOffKeepsColoursOverridesAndImage() {
        val stored = KeyboardThemeCodec.encode(KeyboardTheme(enabled = false, custom = full))
        assertEquals(false, stored.enabled)
        assertEquals(image, stored.image)
        assertEquals(full, KeyboardThemeCodec.decode(stored).custom)
    }

    @Test
    fun noOverridesAreStoredAsAbsent() {
        val stored = KeyboardThemeCodec.encode(KeyboardTheme(true, CustomKeyboardTheme(forest)))
        assertNull(stored.overrides)
        assertNull(stored.image)
    }

    @Test
    fun badStoredValuesAreDroppedNotFatal() {
        val stored = StoredKeyboardTheme(
            enabled = true,
            colors = forest.encode(),
            overrides = "muted=A0B0C0;nonsense=FFFFFF;danger=XYZXYZ;background=000000;amber=80FFEE00;;chip_text",
            image = "../../shared_prefs/keyboard_theme.xml",
            dim = 500,
        )
        val custom = KeyboardThemeCodec.decode(stored).custom!!
        assertEquals(mapOf(ColorRole.MUTED to hex("A0B0C0")), custom.overrides)
        assertNull(custom.image)
        assertEquals(MAX_DIM, custom.dim)
        assertEquals(MIN_DIM, KeyboardThemeCodec.decode(stored.copy(dim = -3)).custom!!.dim)
    }

    @Test
    fun withoutSeedsThereIsNoCustomTheme() {
        val theme = KeyboardThemeCodec.decode(StoredKeyboardTheme(enabled = true, colors = "broken", overrides = "muted=A0B0C0"))
        assertNull(theme.custom)
        assertEquals(KeyboardTheme(enabled = true, custom = null), theme)
    }

    @Test
    fun theEditorDraftSurvivesRecreation() {
        val scope = SaverScope { true }
        for (draft in listOf(full, CustomKeyboardTheme(forest), null)) {
            val saved = with(DraftSaver) { scope.save(draft) }!!
            assertEquals(draft, DraftSaver.restore(saved))
        }
        assertNull(DraftSaver.restore("garbage"))
    }
}
