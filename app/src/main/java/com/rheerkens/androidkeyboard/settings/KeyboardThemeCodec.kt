package com.rheerkens.androidkeyboard.settings

import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.keyboard.ui.ColorRole
import com.rheerkens.androidkeyboard.keyboard.ui.CustomKeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.DEFAULT_DIM
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardSeed
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.clampDim
import com.rheerkens.androidkeyboard.keyboard.ui.isBackgroundFileName
import com.rheerkens.androidkeyboard.keyboard.ui.parseHexColor
import com.rheerkens.androidkeyboard.keyboard.ui.toHex

/**
 * The keyboard theme's preference values. [colors] keeps the format of the first release (five
 * seeds), so a theme saved before overrides and images loads unchanged.
 */
data class StoredKeyboardTheme(
    val enabled: Boolean = false,
    val colors: String? = null,
    val overrides: String? = null,
    val image: String? = null,
    val dim: Int? = null,
)

object KeyboardThemeCodec {
    /** Reads stored values, dropping anything unusable instead of failing: unknown roles, bad colours, unsafe names. */
    fun decode(stored: StoredKeyboardTheme): KeyboardTheme {
        val seed = KeyboardSeed.decode(stored.colors)
            ?: return KeyboardTheme(enabled = stored.enabled, custom = null)
        val custom = CustomKeyboardTheme(
            seed = seed,
            overrides = decodeOverrides(stored.overrides),
            image = stored.image?.takeIf(::isBackgroundFileName),
            dim = clampDim(stored.dim ?: DEFAULT_DIM),
        )
        return KeyboardTheme(enabled = stored.enabled, custom = custom)
    }

    fun encode(theme: KeyboardTheme): StoredKeyboardTheme {
        val custom = theme.custom ?: return StoredKeyboardTheme(enabled = theme.enabled)
        return StoredKeyboardTheme(
            enabled = theme.enabled,
            colors = custom.seed.encode(),
            overrides = encodeOverrides(custom.overrides).ifEmpty { null },
            image = custom.image,
            dim = custom.dim,
        )
    }

    /** `role=RRGGBB` pairs separated by `;`, in [ColorRole] order. */
    fun encodeOverrides(overrides: Map<ColorRole, Color>): String =
        ColorRole.entries.filter { it in overrides }.joinToString(";") { "${it.key}=${overrides.getValue(it).toHex()}" }

    fun decodeOverrides(stored: String?): Map<ColorRole, Color> {
        if (stored.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<ColorRole, Color>()
        for (entry in stored.split(';')) {
            val role = ColorRole.ofKey(entry.substringBefore('=', "")) ?: continue
            if (role.seed != null) continue
            val color = parseHexColor(entry.substringAfter('=', "")) ?: continue
            result[role] = color
        }
        return result
    }
}
