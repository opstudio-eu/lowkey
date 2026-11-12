package com.rheerkens.androidkeyboard.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.rheerkens.androidkeyboard.keyboard.ui.CustomKeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardSeed
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * The optional custom keyboard theme, stored apart from [Settings] (its own preferences file) so
 * the app's [ThemeMode] stays independent. Turning it off keeps the colours and the image.
 */
class KeyboardThemeStore(context: Context, private val scope: CoroutineScope) {
    private val prefs: SharedPreferences = context.getSharedPreferences("keyboard_theme", Context.MODE_PRIVATE)

    /** Background image files, in app-private storage. */
    val images = BackgroundImages(File(context.filesDir, "keyboard_background"))

    private val _theme = MutableStateFlow(KeyboardThemeCodec.decode(read()))
    val theme: StateFlow<KeyboardTheme> = _theme.asStateFlow()

    private val lock = Any()

    init {
        // Unreferenced images older than a day: left by a draft that was never saved or cancelled.
        scope.launch { images.cleanup(setOf(_theme.value.custom?.image), minAgeMs = DRAFT_FILE_AGE_MS) }
    }

    /** Turns the custom theme on or off. Turning it on without a saved theme saves [initial] as one. */
    fun setEnabled(enabled: Boolean, initial: KeyboardSeed) =
        write { it.copy(enabled = enabled, custom = it.custom ?: CustomKeyboardTheme(initial)) }

    /**
     * Saves [custom] and turns the custom theme on. The preferences are committed before this
     * returns; the image file it no longer references, if any, is deleted after that.
     */
    fun save(custom: CustomKeyboardTheme) {
        write { KeyboardTheme(enabled = true, custom = custom) }
        releaseUnused()
    }

    /** Deletes image files referenced by neither the saved theme nor [draft]. */
    fun releaseUnused(draft: String? = null) {
        val saved = _theme.value.custom?.image
        scope.launch { images.cleanup(setOf(saved, draft)) }
    }

    private fun read() = StoredKeyboardTheme(
        enabled = prefs.getBoolean(K_ENABLED, false),
        colors = prefs.getString(K_SEED, null),
        overrides = prefs.getString(K_OVERRIDES, null),
        image = prefs.getString(K_IMAGE, null),
        dim = if (prefs.contains(K_DIM)) prefs.getInt(K_DIM, 0) else null,
    )

    private fun write(transform: (KeyboardTheme) -> KeyboardTheme) = synchronized(lock) {
        _theme.update(transform)
        val s = KeyboardThemeCodec.encode(_theme.value)
        // One synchronous commit: an image file is only deleted once nothing on disk names it.
        prefs.edit(commit = true) {
            putBoolean(K_ENABLED, s.enabled)
            putOrRemove(K_SEED, s.colors)
            putOrRemove(K_OVERRIDES, s.overrides)
            putOrRemove(K_IMAGE, s.image)
            if (s.dim != null) putInt(K_DIM, s.dim) else remove(K_DIM)
        }
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) {
        if (value != null) putString(key, value) else remove(key)
    }

    private companion object {
        const val K_ENABLED = "custom_enabled"
        const val K_SEED = "custom_colors"
        const val K_OVERRIDES = "custom_overrides"
        const val K_IMAGE = "background_image"
        const val K_DIM = "background_dim"
        const val DRAFT_FILE_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
