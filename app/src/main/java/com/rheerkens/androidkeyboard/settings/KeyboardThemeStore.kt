package com.rheerkens.androidkeyboard.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.rheerkens.androidkeyboard.keyboard.ui.CustomKeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardSeed
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Where the theme's values are kept. [write] returns false when they could not be stored. */
interface ThemePersistence {
    fun read(): StoredKeyboardTheme
    fun write(theme: StoredKeyboardTheme): Boolean
}

/**
 * The optional custom keyboard theme, stored apart from [Settings] (its own preferences file) so
 * the app's [ThemeMode] stays independent. Turning it off keeps the colours and the image.
 *
 * [theme] only ever shows what has been stored: a change is written first (off the main thread)
 * and published after the write succeeded. The image files the stored theme and the editor's draft
 * name are held in [images], so cleanup never deletes them, whenever it runs.
 */
class KeyboardThemeStore internal constructor(
    private val persistence: ThemePersistence,
    val images: BackgroundImages,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(context: Context, scope: CoroutineScope) : this(
        PrefsThemePersistence(context.getSharedPreferences("keyboard_theme", Context.MODE_PRIVATE)),
        BackgroundImages(File(context.filesDir, "keyboard_background")),
        scope,
    )

    private val _theme = MutableStateFlow(KeyboardThemeCodec.decode(persistence.read()))
    val theme: StateFlow<KeyboardTheme> = _theme.asStateFlow()

    private val writes = Mutex()

    init {
        images.holdSaved(_theme.value.custom?.image)
        // Unreferenced images older than a day: left by a draft that was never saved or cancelled.
        scope.launch { images.cleanup(minAgeMs = DRAFT_FILE_AGE_MS) }
    }

    /**
     * Turns the custom theme on or off. Turning it on without a saved theme saves [initial] as one.
     * Returns false, changing nothing, when the setting could not be stored.
     */
    suspend fun setEnabled(enabled: Boolean, initial: KeyboardSeed): Boolean =
        write { it.copy(enabled = enabled, custom = it.custom ?: CustomKeyboardTheme(initial)) }

    /**
     * Saves [custom] and turns the custom theme on. On success the draft's image is now the saved
     * one, and the image file nothing names any more is deleted. Returns false when it could not be
     * stored: then nothing changes, the draft image stays held and no file is deleted.
     */
    suspend fun save(custom: CustomKeyboardTheme): Boolean {
        if (!write { KeyboardTheme(enabled = true, custom = custom) }) return false
        images.holdDraft(null)
        releaseUnused()
        return true
    }

    /** The editor's draft now shows [name] (or no image); the previous draft image is deleted. */
    fun setDraftImage(name: String?) {
        images.holdDraft(name)
        releaseUnused()
    }

    /** An imported image the editor will not use (it left, or the custom theme was turned off). */
    fun discardImport(name: String) {
        images.release(name)
        releaseUnused()
    }

    /** Deletes image files that neither the saved theme, the draft nor a fresh import holds. */
    fun releaseUnused() {
        scope.launch { images.cleanup() }
    }

    private suspend fun write(transform: (KeyboardTheme) -> KeyboardTheme): Boolean =
        // Not cancelled half-way: what is published always matches what was stored.
        withContext(NonCancellable) {
            writes.withLock {
                val next = transform(_theme.value)
                val stored = withContext(io) {
                    try {
                        persistence.write(KeyboardThemeCodec.encode(next))
                    } catch (e: Exception) {
                        // Blocking code, so never a cancellation: a storage failure, reported as one.
                        false
                    }
                }
                if (stored) {
                    // The previous image is released only now that nothing on disk names it.
                    images.holdSaved(next.custom?.image)
                    _theme.value = next
                }
                stored
            }
        }

    private companion object {
        const val DRAFT_FILE_AGE_MS = 24 * 60 * 60 * 1000L
    }
}

/** The theme in its own preferences file, written in one commit whose result is checked. */
private class PrefsThemePersistence(private val prefs: SharedPreferences) : ThemePersistence {
    override fun read() = StoredKeyboardTheme(
        enabled = prefs.getBoolean(K_ENABLED, false),
        colors = prefs.getString(K_SEED, null),
        overrides = prefs.getString(K_OVERRIDES, null),
        image = prefs.getString(K_IMAGE, null),
        dim = if (prefs.contains(K_DIM)) prefs.getInt(K_DIM, 0) else null,
    )

    // Not the KTX edit {}: it returns Unit and so hides whether the commit succeeded.
    @SuppressLint("UseKtx")
    override fun write(theme: StoredKeyboardTheme): Boolean = prefs.edit()
        .putBoolean(K_ENABLED, theme.enabled)
        .putOrRemove(K_SEED, theme.colors)
        .putOrRemove(K_OVERRIDES, theme.overrides)
        .putOrRemove(K_IMAGE, theme.image)
        .apply { if (theme.dim != null) putInt(K_DIM, theme.dim) else remove(K_DIM) }
        .commit()

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) =
        if (value != null) putString(key, value) else remove(key)

    private companion object {
        const val K_ENABLED = "custom_enabled"
        const val K_SEED = "custom_colors"
        const val K_OVERRIDES = "custom_overrides"
        const val K_IMAGE = "background_image"
        const val K_DIM = "background_dim"
    }
}
