package com.rheerkens.androidkeyboard.dictionary

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The learned [WordContextModel] in [file] (the app's no-backup folder: it never leaves the
 * phone). Loads off the main thread on first use and saves a while after the last change, so
 * typing never waits for the disk. A damaged file starts over.
 */
class PersonalModelStore(private val file: File, private val scope: CoroutineScope) {
    @Volatile private var loaded: WordContextModel? = null
    private var loading: Job? = null
    private var saving: Job? = null

    /** The model once loaded, starting the load the first time; null until it is ready. */
    fun model(): WordContextModel? {
        loaded?.let { return it }
        load()
        return null
    }

    @Synchronized
    private fun load() {
        if (loaded != null || loading != null) return
        loading = scope.launch(Dispatchers.IO) {
            loaded = runCatching { if (file.isFile) file.inputStream().use { WordContextModel.read(it) } else WordContextModel() }
                .getOrElse { WordContextModel() }
        }
    }

    /** Call after learning: saves [SAVE_DELAY_MS] after the last change. */
    @Synchronized
    fun changed() {
        saving?.cancel()
        saving = scope.launch(Dispatchers.IO) {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    /** Saves now if anything is waiting to be saved (the keyboard is closing). */
    @Synchronized
    fun flush() {
        val pending = saving ?: return
        if (!pending.isActive) return
        pending.cancel()
        saving = scope.launch(Dispatchers.IO) { save() }
    }

    private fun save() {
        val model = loaded ?: return
        val partial = File(file.path + ".partial")
        runCatching {
            file.parentFile?.mkdirs()
            partial.outputStream().use(model::write)
            check(partial.renameTo(file))
        }.onFailure { partial.delete() }
    }

    /** Forgets everything learned, on disk too. */
    @Synchronized
    fun forget() {
        saving?.cancel()
        loaded?.clear()
        scope.launch(Dispatchers.IO) { file.delete() }
    }

    private companion object {
        const val SAVE_DELAY_MS = 10_000L
    }
}
