package com.rheerkens.androidkeyboard.models

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Snapshot of installed models, running downloads (0f..1f), the active model, and the last
 * download error per model (cleared when a new download starts).
 */
data class ModelStoreState(
    val installed: Set<String> = emptySet(),
    val downloads: Map<String, Float> = emptyMap(),
    val activeId: String? = null,
    val errors: Map<String, String> = emptyMap(),
) {
    val activeModel: ModelInfo? get() = ModelCatalog.byId(activeId)
    val usedMb: Int get() = installed.sumOf { ModelCatalog.byId(it)?.sizeMb ?: 0 }
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object NotInstalled : DeleteResult
    /** The active model can't be deleted; the user must choose another model first. */
    data object ActiveModelProtected : DeleteResult
}

/** Speech-model management: download, verify, select, and delete model packages. */
interface ModelStore {
    val state: StateFlow<ModelStoreState>

    /**
     * Starts a cancellable download. No-op if installed or already downloading.
     * When the download completes and verifies, the model becomes the active model.
     */
    fun download(id: String)

    /** Cancels a running download and deletes its partial files. Never installs afterwards. */
    fun cancelDownload(id: String)

    /** Makes an installed model active for dictation. Returns false if not installed. */
    fun setActive(id: String): Boolean

    /** Deletes an installed model. The active model is protected. */
    fun delete(id: String): DeleteResult

    /** Directory holding an installed model's files, or null if it isn't installed. */
    fun modelDir(id: String): File?
}
