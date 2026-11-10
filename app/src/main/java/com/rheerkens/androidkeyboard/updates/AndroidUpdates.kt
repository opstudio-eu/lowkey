package com.rheerkens.androidkeyboard.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.R
import com.rheerkens.androidkeyboard.companion.CompanionLinks
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.models.HttpsArtifactFetcher
import com.rheerkens.androidkeyboard.models.ModelArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestOutputStream
import java.security.MessageDigest
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

    fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New versions of Lowkey"
            },
        )
    }

    /** Whether the user allowed this app to install updates (Settings → Install unknown apps). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()
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
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // An app may update itself without a confirmation on Android 12 and later.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
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
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                val opened = confirm != null &&
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!opened) {
                    AppGraph.updates.onInstallResult(InstallResult.Failed("Android's installer didn't open. Try again."))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // This process is replaced by the new version.
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppGraph.updates.onInstallResult(InstallResult.Cancelled)
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
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
