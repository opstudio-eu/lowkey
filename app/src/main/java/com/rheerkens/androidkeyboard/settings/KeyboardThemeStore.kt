package com.rheerkens.androidkeyboard.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardSeed
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The optional custom keyboard palette, stored apart from [Settings] (its own preferences file) so
 * the app's [ThemeMode] stays independent. Turning it off keeps the colours.
 */
class KeyboardThemeStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("keyboard_theme", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        KeyboardTheme(enabled = prefs.getBoolean(K_ENABLED, false), seed = KeyboardSeed.decode(prefs.getString(K_SEED, null))),
    )
    val theme: StateFlow<KeyboardTheme> = _theme.asStateFlow()

    private val lock = Any()

    /** Turns the custom palette on or off. Turning it on without saved colours saves [initial]. */
    fun setEnabled(enabled: Boolean, initial: KeyboardSeed) = write { it.copy(enabled = enabled, seed = it.seed ?: initial) }

    /** Saves [seed] as the custom palette and turns it on. */
    fun save(seed: KeyboardSeed) = write { KeyboardTheme(enabled = true, seed = seed) }

    private fun write(transform: (KeyboardTheme) -> KeyboardTheme) = synchronized(lock) {
        _theme.update(transform)
        val t = _theme.value
        prefs.edit {
            putBoolean(K_ENABLED, t.enabled)
            if (t.seed != null) putString(K_SEED, t.seed.encode()) else remove(K_SEED)
        }
    }

    private companion object {
        const val K_ENABLED = "custom_enabled"
        const val K_SEED = "custom_colors"
    }
}
