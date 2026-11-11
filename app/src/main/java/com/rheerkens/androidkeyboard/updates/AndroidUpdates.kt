package com.rheerkens.androidkeyboard.updates

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.app.ServiceCompat
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.R
import com.rheerkens.androidkeyboard.companion.CompanionLinks
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.models.HttpsArtifactFetcher
import com.rheerkens.androidkeyboard.models.ModelArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

/** Settings and timing of the updater. */
object Updates {
    /** The newest release's feed; GitHub redirects `latest/download` to the release marked Latest. */
    val FEED_URL: String = BuildConfig.UPDATE_FEED_URL

    /** Background checks run about once a day; opening the app checks if the last one is older than this. */
    const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
    const val APP_OPEN_CHECK_AFTER_MILLIS = 6L * 60 * 60 * 1000

    fun create(context: Context): UpdateManager {
        val app = context.applicationContext
        return UpdateManager(
            platform = AndroidUpdatePlatform(app),
            prefs = SharedUpdatePrefs(app.getSharedPreferences("updates", Context.MODE_PRIVATE)),
            downloadDir = File(app.cacheDir, "updates"),
            scope = AppGraph.appScope,
        )
    }

    /**
     * Schedules the daily background check, or cancels it when automatic checks are off. Never
     * throws: the keyboard runs in this process, and a missing check must not stop it.
     */
    fun schedule(context: Context, enabled: Boolean) {
        try {
            scheduleOrThrow(context, enabled)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't schedule the update check", e)
        }
    }

    private fun scheduleOrThrow(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!enabled) {
            scheduler.cancel(UpdateCheckJob.JOB_ID)
            return
        }
        // Keeps an existing schedule, so restarting the process doesn't push the next check back.
        if (scheduler.getPendingJob(UpdateCheckJob.JOB_ID) != null) return
        val job = JobInfo.Builder(UpdateCheckJob.JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(CHECK_INTERVAL_MILLIS, 6L * 60 * 60 * 1000)
            .build()
        scheduler.schedule(job)
    }

    /** Whether Android lets this app post the "update available" notification. */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private const val TAG = "LowkeyUpdates"
    const val CHANNEL_ID = "updates"
    const val NOTIFICATION_ID = 1
    const val PROGRESS_NOTIFICATION_ID = 2

    fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New versions of Lowkey"
            },
        )
    }

    /** Whether the user allowed this app to install updates (Settings → Install unknown apps). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Android's "confirm this update" screen for the session waiting on the user, kept so the card
     * can open it again. Android blocks opening it from the background, so it opens from a tap.
     */
    @Volatile
    var confirmIntent: Intent? = null

    /** The session whose result counts; results of an earlier, abandoned session are ignored. */
    @Volatile
    var currentSessionId: Int = -1

    private val resumedActivities = AtomicInteger()

    /**
     * Whether one of this app's activities is in front, so it may open Android's installer. Resumed,
     * not just started: a started activity can sit under the lock screen or a dialog, where Android
     * would silently block the start and no notification would be posted.
     */
    val appVisible: Boolean get() = resumedActivities.get() > 0

    fun trackVisibleActivities(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumedActivities.incrementAndGet() }
            override fun onActivityPaused(activity: Activity) { resumedActivities.decrementAndGet() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Forgets the confirmation screen of a session that ended, and its "ready to install" notification. */
    fun clearConfirmation(context: Context) {
        confirmIntent = null
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Opens Android's confirmation screen from a tap in the app, or returns false if there is none. */
    fun openConfirmation(context: Context): Boolean {
        val confirm = confirmIntent ?: return false
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        return runCatching { context.startActivity(Intent(confirm)) }
            .onFailure { Log.w(TAG, "Couldn't open the installer", it) }
            .isSuccess
    }

    /** "Tap to install": opens Android's confirmation screen when the app isn't on screen. */
    fun notifyConfirmation(context: Context, update: AvailableUpdate?, confirm: Intent): Boolean {
        if (!canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        ensureChannel(manager)
        val open = PendingIntent.getActivity(
            context, 1, Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(update?.let { "Lowkey ${it.versionName} is ready to install" } ?: "Lowkey update ready to install")
            .setContentText("Tap to install the update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess
    }
}

private class SharedUpdatePrefs(private val prefs: SharedPreferences) : UpdatePrefs {
    override var lastCheckedAt: Long?
        get() = prefs.getLong(K_CHECKED, 0L).takeIf { it > 0 }
        set(value) = prefs.edit { if (value == null) remove(K_CHECKED) else putLong(K_CHECKED, value) }

    override var available: AvailableUpdate?
        get() = prefs.getString(K_AVAILABLE, null)?.let { runCatching { AvailableUpdate.parse(it) }.getOrNull() }
        set(value) = prefs.edit { if (value == null) remove(K_AVAILABLE) else putString(K_AVAILABLE, value.toProperties()) }

    override var notifiedVersionCode: Long
        get() = prefs.getLong(K_NOTIFIED, 0L)
        set(value) = prefs.edit { putLong(K_NOTIFIED, value) }

    private companion object {
        const val K_CHECKED = "last_checked_at"
        const val K_AVAILABLE = "available"
        const val K_NOTIFIED = "notified_version_code"
    }
}

private class AndroidUpdatePlatform(private val context: Context) : UpdatePlatform {
    override fun installedVersionCode(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    override fun now(): Long = System.currentTimeMillis()

    override suspend fun fetchFeed(): String? = withContext(Dispatchers.IO) {
        val connection = URL(Updates.FEED_URL).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Lowkey/${BuildConfig.VERSION_NAME}")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null
                in 200..299 -> connection.inputStream.use { input ->
                    val bytes = input.readNBytesCompat(MAX_FEED_BYTES + 1)
                    if (bytes.size > MAX_FEED_BYTES) throw IOException("Feed too large")
                    bytes.toString(Charsets.UTF_8)
                }
                else -> throw IOException("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(update: AvailableUpdate, target: File, onBytes: (Long) -> Unit) {
        val dir = target.parentFile ?: throw IOException("No download directory")
        val needed = update.sizeBytes + SPACE_MARGIN_BYTES
        if (dir.usableSpace < needed) throw NotEnoughSpaceException(needed / (1024 * 1024))
        val artifact = ModelArtifact(update.fileName, update.url, update.sizeBytes, update.sha256)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var reported = 0L
        DigestOutputStream(FileOutputStream(target), digest).use { out ->
            HttpsArtifactFetcher().fetch(artifact, out) { n ->
                total += n
                if (total - reported >= PROGRESS_STEP_BYTES || total == update.sizeBytes) {
                    reported = total
                    onBytes(total)
                }
                if (total > update.sizeBytes) throw ChecksumMismatchException("Larger than announced")
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (total != update.sizeBytes || hex != update.sha256) {
            target.delete()
            throw ChecksumMismatchException("Size or SHA-256 differs")
        }
    }

    override fun install(apk: File, update: AvailableUpdate) {
        val installer = context.packageManager.packageInstaller
        // An earlier session (Install again, or one left by a process that was killed while Android
        // waited for confirmation) still holds a staged copy of the APK: drop it.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        Updates.confirmIntent = null
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // An app may update itself without a confirmation on Android 12 and later.
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(
                    if (BuildConfig.UPDATE_REQUIRE_USER_ACTION) PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                    else PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED,
                )
            }
        }
        val sessionId = installer.createSession(params)
        Updates.currentSessionId = sessionId
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val status = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(status.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    override fun keepRunning() {
        // Without a foreground service, Android freezes the app soon after the user leaves it, and
        // the download fails. startService, not startForegroundService: the user just tapped Update,
        // so the app is in front and may start it, and the service then goes foreground itself. If
        // that fails, nothing has promised Android a foreground service, which would otherwise end
        // this process (the keyboard's) when the promise isn't kept.
        runCatching { context.startService(Intent(context, UpdateDownloadService::class.java)) }
            .onFailure { Log.w("LowkeyUpdates", "Couldn't start the download service", it) }
    }

    override fun notifyAvailable(update: AvailableUpdate): Boolean {
        if (!Updates.canNotify(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        Updates.ensureChannel(manager)
        val open = PendingIntent.getActivity(
            context, 0, CompanionLinks.intent(context, Destination.HOME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Updates.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Lowkey ${update.versionName} is available")
            .setContentText("Tap to update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return runCatching { manager.notify(Updates.NOTIFICATION_ID, notification) }.isSuccess
    }

    private companion object {
        const val MAX_FEED_BYTES = 4096
        const val SPACE_MARGIN_BYTES = 32L * 1024 * 1024
        const val PROGRESS_STEP_BYTES = 256L * 1024
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** Receives the package installer's result for an update session. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        AppGraph.init(context)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        Log.i(TAG, "Installer status $status ($detail) for session $sessionId, app visible: ${Updates.appVisible}")
        val current = Updates.currentSessionId
        if (current != -1 && sessionId != -1 && sessionId != current) return // An abandoned, earlier session.
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    AppGraph.updates.onInstallResult(InstallResult.Failed("Android's installer didn't open. Try again."))
                    return
                }
                Updates.confirmIntent = confirm
                AppGraph.updates.onConfirmationNeeded()
                // Android silently ignores an activity start from the background (no exception), so
                // open the installer only while the app is on screen; otherwise the notification and
                // the card's Install button open it from a tap.
                val opened = Updates.appVisible &&
                    runCatching { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!opened) Updates.notifyConfirmation(context, AppGraph.updates.state.value.update, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // This process is replaced by the new version.
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Updates.clearConfirmation(context)
                AppGraph.updates.onInstallResult(InstallResult.Cancelled)
            }
            else -> {
                Updates.clearConfirmation(context)
                Log.w(TAG, "Update install failed: status $status, $detail")
                AppGraph.updates.onInstallResult(InstallResult.Failed(installFailure(status)))
            }
        }
    }

    private fun installFailure(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update."
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "Android refused the update: it doesn't match the installed app."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the update."
        else -> "The update couldn't be installed. Try again."
    }

    companion object {
        const val ACTION = "com.rheerkens.androidkeyboard.UPDATE_INSTALL_STATUS"
        private const val TAG = "LowkeyUpdates"
    }
}

/**
 * A foreground service that keeps the process running, with a progress notification, from Update
 * until the APK is handed to Android's installer. Without it Android freezes the app soon after the
 * user leaves it, and the download fails.
 */
class UpdateDownloadService : Service() {
    private var job: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        AppGraph.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.let(Updates::ensureChannel)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, Updates.PROGRESS_NOTIFICATION_ID, progress(AppGraph.updates.state.value), type)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't start in the foreground", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (job == null) {
            job = AppGraph.appScope.launch {
                val shown = launch {
                    // At most once a second: Android drops a notification updated much more often.
                    var downloading = true
                    var postedAt = 0L
                    AppGraph.updates.state.collect { state ->
                        val now = SystemClock.elapsedRealtime()
                        val isDownloading = state.status is UpdateStatus.Downloading
                        if (isDownloading != downloading || now - postedAt >= 1_000) {
                            downloading = isDownloading
                            postedAt = now
                            runCatching { manager?.notify(Updates.PROGRESS_NOTIFICATION_ID, progress(state)) }
                        }
                    }
                }
                // Stop once no update is being worked on; a new Update tap may have started one since.
                do {
                    AppGraph.updates.working.first { !it }
                } while (AppGraph.updates.working.value)
                shown.cancel()
                finish()
            }
        }
        return START_NOT_STICKY
    }

    private fun progress(state: UpdateState): Notification {
        val update = state.update
        val status = state.status
        val builder = NotificationCompat.Builder(this, Updates.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, CompanionLinks.intent(this, Destination.HOME),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        if (status is UpdateStatus.Downloading && update != null) {
            val total = (update.sizeBytes / MB).toInt().coerceAtLeast(1)
            builder.setContentTitle("Downloading Lowkey ${update.versionName}")
                .setContentText("${status.bytes / MB} of $total MB")
                .setProgress(total, (status.bytes / MB).toInt(), false)
        } else {
            builder.setContentTitle(update?.let { "Installing Lowkey ${it.versionName}" } ?: "Updating Lowkey")
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun finish() {
        val running = job
        job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Only if no newer start arrived; a newer one runs its own check.
        stopSelf(lastStartId)
        running?.cancel()
    }

    /** Android 15 limits dataSync services to 6 hours a day; an update download never needs that long. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        AppGraph.updates.cancelDownload()
        finish()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LowkeyUpdates"
        const val MB = 1_000_000L
    }
}

/** The daily background check. */
class UpdateCheckJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        AppGraph.init(this)
        if (!AppGraph.settings.current.checkForUpdates) return false
        AppGraph.appScope.launch {
            try {
                AppGraph.updates.check(userStarted = false)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        const val JOB_ID = 4201
    }
}

/** Runs once after this app was replaced by a newer version, and says so in a notification. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AppGraph.init(context)
        if (!Updates.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, 0, CompanionLinks.intent(context, Destination.HOME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        Updates.ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, Updates.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Lowkey updated to ${BuildConfig.VERSION_NAME}")
            .setContentText("Tap to open Lowkey.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(Updates.NOTIFICATION_ID, notification) }
    }
}
