package com.rheerkens.androidkeyboard.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Posts one report body. Returns the HTTP status; throws [IOException] when there is no answer. */
fun interface ReportTransport {
    fun post(url: String, reportId: String, body: ByteArray): Int
}

/** HTTPS only, no redirects, no cookies, and nothing in the request that identifies the phone. */
class HttpReportTransport(
    private val appVersion: String,
    private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) : ReportTransport {
    override fun post(url: String, reportId: String, body: ByteArray): Int {
        if (!url.startsWith("https://")) throw IOException("Reports go over HTTPS only")
        val connection = open(url)
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "Lowkey/$appVersion")
            // The same report sent twice (an answer lost on the way back) is one report on the server.
            connection.setRequestProperty("Idempotency-Key", reportId)
            connection.outputStream.use { it.write(body) }
            return connection.responseCode
        } catch (e: SecurityException) {
            throw IOException(e)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

/**
 * Sends queued reports to [endpoint]. A report that can't be sent (offline, no service yet) stays
 * queued and is tried again after [ContentReports.backoffMs], or when the app or keyboard starts.
 * Blocking: call [flush] on an IO dispatcher. [schedule] does that, and keeps one retry timer while
 * the process lives.
 */
class ContentReportUploader(
    private val store: ContentReportStore,
    private val transport: ReportTransport,
    private val appVersion: String,
    private val endpoint: String = ContentReports.ENDPOINT,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var retry: Job? = null

    /** Sends every queued report that is due, oldest first. */
    fun flush(): Map<String, SendOutcome> = synchronized(lock) {
        val now = clock()
        val due = store.reports.value
            .filter { it.status == ReportStatus.QUEUED && it.nextAttemptMs <= now }
            .sortedBy { it.createdAtMs }
        due.associate { report -> report.id to send(report) }
    }

    private fun send(report: ContentReport): SendOutcome {
        val image = if (report.imageFile != null) store.readImage(report) else null
        val outcome = if (report.imageFile != null && image == null) {
            // The included image is gone from the phone: send without it rather than not at all.
            trySend(report.copy(imageFile = null, imageMime = null, imageOmitted = true), null)
        } else {
            trySend(report, image)
        }
        val now = clock()
        try {
            when (outcome) {
                SendOutcome.SENT -> store.markSent(report.id, now)
                SendOutcome.REJECTED -> store.markRejected(report.id)
                SendOutcome.RETRY -> store.markRetry(report.id, now)
            }
        } catch (_: IOException) {
            // The index couldn't be written; the report stays as it was and is sent again later.
        }
        return outcome
    }

    private fun trySend(report: ContentReport, image: ByteArray?): SendOutcome = try {
        val body = ContentReports.payload(report, image, appVersion).toByteArray(Charsets.UTF_8)
        ContentReports.outcome(transport.post(endpoint, report.id, body))
    } catch (_: IOException) {
        SendOutcome.RETRY
    } catch (_: RuntimeException) {
        // A broken connection stack must not take the keyboard down; the report waits instead.
        SendOutcome.RETRY
    }

    /** When the next queued report is due, or null when nothing waits. */
    fun nextDueMs(): Long? = store.reports.value
        .filter { it.status == ReportStatus.QUEUED }
        .minOfOrNull { it.nextAttemptMs }

    /**
     * Flushes in [scope] when something is due (and [onlyId] now, waiting or not), then sets one timer for the next
     * retry. Cheap when nothing waits, so the keyboard can call it whenever it starts.
     */
    fun schedule(scope: CoroutineScope, onlyId: String? = null) {
        val next = nextDueMs() ?: return
        if (onlyId == null && next > clock() && retry?.isActive == true) return
        scope.launch(Dispatchers.IO) {
            if (onlyId != null) try { store.makeDue(onlyId) } catch (_: IOException) { }
            flush()
            val wait = (nextDueMs() ?: return@launch) - clock()
            synchronized(lock) {
                retry?.cancel()
                retry = scope.launch {
                    delay(wait.coerceAtLeast(MIN_RETRY_MS))
                    schedule(scope)
                }
            }
        }
    }

    private companion object {
        const val MIN_RETRY_MS = 30_000L
    }
}
