package com.rheerkens.androidkeyboard.updates

import android.app.Application
import android.content.Context
import com.rheerkens.androidkeyboard.AppGraph
import java.io.File

/**
 * Play builds are updated by Google Play. There is no release feed, download, installer session,
 * or background check. The GitHub flavor provides the real updater under the same name.
 */
object Updates {
    const val APP_OPEN_CHECK_AFTER_MILLIS = 6L * 60 * 60 * 1000

    fun create(context: Context): UpdateManager = UpdateManager(
        platform = PlayUpdatePlatform,
        prefs = PlayUpdatePrefs,
        downloadDir = File(context.cacheDir, "updates"),
        scope = AppGraph.appScope,
    )

    fun schedule(context: Context, enabled: Boolean) = Unit

    fun trackVisibleActivities(app: Application) = Unit

    fun canNotify(context: Context): Boolean = false

    fun canInstall(context: Context): Boolean = false

    fun openConfirmation(context: Context): Boolean = false
}

private object PlayUpdatePrefs : UpdatePrefs {
    override var lastCheckedAt: Long? = null
    override var available: AvailableUpdate? = null
    override var notifiedVersionCode: Long = 0
}

private object PlayUpdatePlatform : UpdatePlatform {
    override fun installedVersionCode(): Long = 0
    override fun now(): Long = 0
    override suspend fun fetchFeed(): String? = null
    override suspend fun download(update: AvailableUpdate, target: File, onBytes: (Long) -> Unit) {
        error("Play builds do not download updates")
    }
    override fun install(apk: File, update: AvailableUpdate) {
        error("Play builds do not install updates")
    }
    override fun keepRunning() = Unit
    override fun notifyAvailable(update: AvailableUpdate): Boolean = false
}
