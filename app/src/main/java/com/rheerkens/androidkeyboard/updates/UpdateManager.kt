package com.rheerkens.androidkeyboard.updates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** What the Updates card shows. */
sealed interface UpdateStatus {
    /** Not checked yet, or the last check found nothing newer. */
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val update: AvailableUpdate) : UpdateStatus
    data class Downloading(val update: AvailableUpdate, val bytes: Long) : UpdateStatus
    /** The APK was handed to Android's installer; it may be waiting for the user to confirm. */
    data class Installing(val update: AvailableUpdate) : UpdateStatus
    /** Android's installer waits for the user to confirm; the card offers to open it. */
    data class AwaitingConfirmation(val update: AvailableUpdate) : UpdateStatus
    data class Failed(val message: String, val update: AvailableUpdate?) : UpdateStatus
}

data class UpdateState(val status: UpdateStatus = UpdateStatus.Idle, val lastCheckedAt: Long? = null) {
    /** The newer release this state knows of, whatever is happening with it. */
    val update: AvailableUpdate?
        get() = when (status) {
            is UpdateStatus.Available -> status.update
            is UpdateStatus.Downloading -> status.update
            is UpdateStatus.Installing -> status.update
            is UpdateStatus.AwaitingConfirmation -> status.update
            is UpdateStatus.Failed -> status.update
            else -> null
        }

    val busy: Boolean get() = status is UpdateStatus.Checking || status is UpdateStatus.Downloading
}

/** What survives a process restart: the last check time, the newer release, and what was notified. */
interface UpdatePrefs {
    var lastCheckedAt: Long?
    var available: AvailableUpdate?
    var notifiedVersionCode: Long
}

/** The Android side of the updater, injected so the logic can be tested on the JVM. */
interface UpdatePlatform {
    fun installedVersionCode(): Long
    fun now(): Long

    /** The feed's text, or null when the latest release has no feed (HTTP 404). Throws on other errors. */
    suspend fun fetchFeed(): String?

    /** Downloads [update] into [target], checking its size and SHA-256. Throws on any mismatch. */
    suspend fun download(update: AvailableUpdate, target: File, onBytes: (Long) -> Unit)

    /** Starts Android's package installer for [apk]; the result arrives in [UpdateManager.onInstallResult]. */
    fun install(apk: File, update: AvailableUpdate)

    /** Keeps the process running while [UpdateManager.working], also when the user leaves the app. */
    fun keepRunning()

    /** Shows the "update available" notification, if notifications are allowed. Returns whether it was shown. */
    fun notifyAvailable(update: AvailableUpdate): Boolean
}

/**
 * Checks the release feed and installs updates. Background checks never show errors, only a
 * notification for a newer release (once per version); checks the user starts show every outcome.
 */
class UpdateManager(
    private val platform: UpdatePlatform,
    private val prefs: UpdatePrefs,
    private val downloadDir: File,
    private val scope: CoroutineScope,
    /** File work and handing the ~90 MB APK to the installer: never on the main thread, where the keyboard runs. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _working = MutableStateFlow(false)

    /** True from Update until the APK is handed to Android's installer, or the download fails. */
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val checkLock = Mutex()
    private var downloadJob: Job? = null

    private fun initialState(): UpdateState {
        val stored = prefs.available
        val pending = stored?.takeIf { it.versionCode > platform.installedVersionCode() }
        if (pending == null) {
            // Installed (or never known): forget it and the APK it left behind.
            prefs.available = null
            downloadDir.listFiles()?.forEach { it.delete() }
        }
        val status = if (pending != null) UpdateStatus.Available(pending) else UpdateStatus.Idle
        return UpdateState(status, prefs.lastCheckedAt)
    }

    /** Whether an automatic check is due: never checked, or not within [intervalMillis]. */
    fun checkDue(intervalMillis: Long): Boolean {
        val last = prefs.lastCheckedAt ?: return true
        val now = platform.now()
        return now - last >= intervalMillis || now < last
    }

    /**
     * Checks the feed. With [userStarted], progress and failures are shown. With [notify], a newer
     * release posts a notification once per version (not when the user is looking at the app).
     */
    suspend fun check(userStarted: Boolean, notify: Boolean = !userStarted) {
        checkLock.withLock {
            val before = _state.value
            if (inProgress(before.status)) return
            if (userStarted) setUnlessInProgress(before.copy(status = UpdateStatus.Checking))
            // What the card showed when the fetch started. If it changed meanwhile (an update the
            // user started, or its failure), that is newer than this check's result.
            val shown = if (userStarted) UpdateStatus.Checking else before.status
            val result = runCatching { platform.fetchFeed()?.let(AvailableUpdate::parse) }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val now = platform.now()
            val feed = result.getOrElse { error ->
                // Background checks never show errors, so they have nothing to write.
                if (userStarted) setIfStill(shown, before.copy(status = UpdateStatus.Failed(checkFailure(error), before.update)))
                return
            }
            prefs.lastCheckedAt = now
            // The user started the update while the feed loaded: leave it, its saved release and
            // its notifications alone.
            if (inProgress(_state.value.status)) return
            val newer = feed?.takeIf { it.versionCode > platform.installedVersionCode() }
            prefs.available = newer
            if (newer == null) {
                setIfStill(shown, UpdateState(UpdateStatus.UpToDate, now))
                return
            }
            setIfStill(shown, UpdateState(UpdateStatus.Available(newer), now))
            if (notify && prefs.notifiedVersionCode != newer.versionCode && platform.notifyAvailable(newer)) {
                prefs.notifiedVersionCode = newer.versionCode
            }
        }
    }

    private fun inProgress(status: UpdateStatus) = status is UpdateStatus.Downloading ||
        status is UpdateStatus.Installing || status is UpdateStatus.AwaitingConfirmation

    /** A check that was waiting for the feed must not overwrite an update the user started meanwhile. */
    private fun setUnlessInProgress(next: UpdateState) {
        _state.update { current -> if (inProgress(current.status)) current else next }
    }

    /** Writes [next] only if the card still shows [shown]. */
    private fun setIfStill(shown: UpdateStatus, next: UpdateState) {
        _state.update { current -> if (current.status == shown) next else current }
    }

    /** Downloads the known update, verifies it, and hands it to Android's installer. */
    fun startUpdate() {
        val update = _state.value.update ?: return
        // Also while a cancelled job is still finishing: it may still be cleaning up its files.
        if (downloadJob?.isCompleted == false) return
        _state.update { it.copy(status = UpdateStatus.Downloading(update, 0)) }
        _working.value = true
        var committed = false
        // Lazy, so it is the current job before it can run or complete.
        val job = scope.launch(io, start = CoroutineStart.LAZY) {
            try {
                downloadDir.mkdirs()
                val apk = File(downloadDir, update.fileName)
                if (!apk.isFile || apk.length() != update.sizeBytes) {
                    downloadDir.listFiles()?.forEach { it.delete() }
                    val part = File(downloadDir, update.fileName + ".part")
                    platform.download(update, part) { total ->
                        _state.update { it.copy(status = UpdateStatus.Downloading(update, total)) }
                    }
                    check(part.renameTo(apk)) { "Could not save the download." }
                }
                // A Cancel tapped as the download finished must not still open the installer.
                ensureActive()
                _state.update { it.copy(status = UpdateStatus.Installing(update)) }
                platform.install(apk, update)
                committed = true
            } catch (e: Exception) {
                // Cancelling closes the connection, so the read fails ("Socket closed") before the
                // coroutine notices: that is the cancel too, not a failed download. The partial
                // download goes, inside the job, so it is gone before a new one can start.
                if (e is CancellationException || !isActive) {
                    runCatching { downloadDir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() } }
                    throw e as? CancellationException ?: CancellationException("Update cancelled").apply { initCause(e) }
                }
                downloadDir.listFiles()?.forEach { it.delete() }
                _state.update { it.copy(status = UpdateStatus.Failed(downloadFailure(e), update)) }
            }
        }
        downloadJob = job
        // Runs however the job ends, also when it was cancelled before it started. The bookkeeping
        // runs in [scope] (the main thread in the app), like startUpdate, so a new Update tap can't
        // slip in between its check and its writes; a newer job owns the state from then on.
        job.invokeOnCompletion { cause ->
            scope.launch {
                if (downloadJob !== job) return@launch
                if (cause is CancellationException && !committed) {
                    _state.update { state ->
                        val s = state.status
                        if (s is UpdateStatus.Downloading || s is UpdateStatus.Installing) state.copy(status = UpdateStatus.Available(update))
                        else state
                    }
                }
                _working.value = false
            }
        }
        job.start()
        platform.keepRunning()
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Android's installer waits for the user to confirm the update. */
    fun onConfirmationNeeded() {
        _state.update { state ->
            val update = state.update ?: return@update state
            state.copy(status = UpdateStatus.AwaitingConfirmation(update))
        }
    }

    /** Android's installer finished without replacing the app (success ends this process instead). */
    fun onInstallResult(result: InstallResult) {
        _state.update { state ->
            val update = state.update ?: return@update state
            state.copy(
                status = when (result) {
                    InstallResult.Cancelled -> UpdateStatus.Available(update)
                    is InstallResult.Failed -> UpdateStatus.Failed(result.message, update)
                },
            )
        }
    }

    private fun checkFailure(error: Throwable): String = when (error) {
        is IllegalArgumentException -> "The update information is invalid (${error.message})."
        else -> "Couldn't check for updates. Check your connection and try again."
    }

    private fun downloadFailure(error: Throwable): String = when (error) {
        is ChecksumMismatchException -> "The download was damaged. Try again."
        is NotEnoughSpaceException -> "Not enough storage for the update (about ${error.neededMb} MB)."
        else -> "The download failed. Check your connection and try again."
    }
}

sealed interface InstallResult {
    data object Cancelled : InstallResult
    data class Failed(val message: String) : InstallResult
}

class ChecksumMismatchException(message: String) : Exception(message)

class NotEnoughSpaceException(val neededMb: Long) : Exception("Not enough space")
