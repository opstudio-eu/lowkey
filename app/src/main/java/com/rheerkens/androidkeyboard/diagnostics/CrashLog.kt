package com.rheerkens.androidkeyboard.diagnostics

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** One recorded crash, newest first in [CrashLog.entries]. */
data class CrashEntry(
    val timeMs: Long,
    val kind: String,
    /** Model that was loading or running in the speech engine when the process died, if any. */
    val engineWork: String?,
    val details: String,
)

/**
 * Keeps the last few crashes so they can be read in Diagnostics and copied, without adb:
 * - Java/Kotlin exceptions via the default uncaught-exception handler.
 * - Native crashes and other abnormal exits via ApplicationExitInfo (Android 11+), including
 *   readable strings from the native tombstone (abort message, library and function names).
 *
 * The speech engine also writes a marker while native model work runs. If the process dies
 * with the marker present, [suspectModelKey] names that model, so the keyboard can avoid
 * auto-starting it again and looping into the same crash.
 */
class CrashLog(context: Context) {
    private val packageName = context.packageName
    private val activityManager: ActivityManager? = context.getSystemService(ActivityManager::class.java)
    private val dir = File(context.filesDir, "crash").apply { mkdirs() }
    private val logFile = File(dir, "crashes.json")
    private val markerFile = File(dir, "engine-work")
    private val prefs = context.getSharedPreferences("crash", Context.MODE_PRIVATE)
    private val appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<CrashEntry>> = _entries.asStateFlow()

    /**
     * Model key (see `LoadableModel.key`) that was in native work when the process died. Kept
     * across restarts until the user retries it with Talk.
     */
    val suspectModelKey: String? get() = prefs.getString(KEY_SUSPECT, null)

    fun install() {
        val stale = markerFile.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        markerFile.delete()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                add(CrashEntry(System.currentTimeMillis(), "Java exception on ${thread.name}", currentWork(), sw.toString()))
            }
            previous?.uncaughtException(thread, e)
        }
        collectExitReasons(stale)
        if (stale != null) prefs.edit { putString(KEY_SUSPECT, stale.substringAfter(' ', stale)) }
    }

    /** Called by the speech engine around native work; null clears the marker. */
    fun engineWork(description: String?) {
        runCatching { if (description == null) markerFile.delete() else markerFile.writeText(description) }
    }

    /** The user retried the suspect model on purpose, or it worked: stop holding it back. */
    fun clearSuspect() {
        prefs.edit { remove(KEY_SUSPECT) }
    }

    fun clear() {
        logFile.delete()
        _entries.value = emptyList()
    }

    /** Plain-text report for the clipboard. */
    fun report(): String = buildString {
        append("Lowkey Keyboard $appVersion crash report · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ")
        append("${Build.MANUFACTURER} ${Build.MODEL} · ${Build.SUPPORTED_ABIS.joinToString()}\n")
        _entries.value.forEach { e ->
            append("\n== ${java.util.Date(e.timeMs)} · ${e.kind}")
            e.engineWork?.let { append(" · engine: $it") }
            append("\n${e.details.take(MAX_DETAILS)}\n")
        }
    }

    private fun currentWork(): String? = markerFile.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    private fun collectExitReasons(staleWork: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (staleWork != null) add(CrashEntry(System.currentTimeMillis(), "Process ended during engine work", staleWork, ""))
            return
        }
        val am = activityManager ?: return
        val seen = prefs.getLong(KEY_LAST_EXIT, 0L)
        val reasons = runCatching { am.getHistoricalProcessExitReasons(packageName, 0, 10) }.getOrDefault(emptyList())
        var newest = seen
        var attributed = false
        val fresh = reasons.filter { it.timestamp > seen && it.reason in REPORTED }.sortedBy { it.timestamp }
        fresh.forEachIndexed { i, info ->
            newest = maxOf(newest, info.timestamp)
            // The marker belongs to the process that died last.
            val work = if (i == fresh.lastIndex) staleWork else null
            // A Java crash was already recorded, with its stack trace, by the uncaught handler.
            // (The handler entry already carries the engine work, if any.)
            if (info.reason == ApplicationExitInfo.REASON_CRASH && recordedJavaCrashNear(info.timestamp)) {
                attributed = attributed || work != null
                return@forEachIndexed
            }
            val details = buildString {
                info.description?.let { append(it).append('\n') }
                append("reason=${reasonName(info.reason)} status=${info.status} importance=${info.importance} pss=${info.pss}kB rss=${info.rss}kB\n")
                if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || info.reason == ApplicationExitInfo.REASON_ANR) {
                    runCatching { info.traceInputStream?.use { readableStrings(it.readAtMost(MAX_TRACE_BYTES)) } }
                        .getOrNull()?.let { append(it) }
                }
            }
            add(CrashEntry(info.timestamp, reasonName(info.reason), work, details.take(MAX_DETAILS)))
            attributed = attributed || work != null
        }
        if (!attributed && staleWork != null) {
            add(CrashEntry(System.currentTimeMillis(), "Process ended during engine work", staleWork, ""))
        }
        prefs.edit { putLong(KEY_LAST_EXIT, newest) }
    }

    private fun recordedJavaCrashNear(timeMs: Long) =
        _entries.value.any { it.kind.startsWith("Java exception") && kotlin.math.abs(it.timeMs - timeMs) < JAVA_CRASH_WINDOW_MS }

    private fun add(entry: CrashEntry) {
        val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        _entries.value = updated
        runCatching {
            val arr = JSONArray()
            updated.forEach {
                arr.put(JSONObject().put("t", it.timeMs).put("kind", it.kind).put("work", it.engineWork ?: "").put("details", it.details))
            }
            logFile.writeText(arr.toString())
        }
    }

    private fun load(): List<CrashEntry> = runCatching {
        val arr = JSONArray(logFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CrashEntry(o.getLong("t"), o.getString("kind"), o.optString("work").ifBlank { null }, o.getString("details"))
        }
    }.getOrDefault(emptyList())

    // ApplicationExitInfo constants are compile-time ints (safe to inline below API 30); the
    // exit-reason query itself only runs on Android 11+.
    @SuppressLint("InlinedApi")
    companion object {
        private const val MAX_ENTRIES = 10
        private const val MAX_DETAILS = 12_000
        /** Tombstones can be megabytes; the readable strings that matter are near the start. */
        private const val MAX_TRACE_BYTES = 512 * 1024
        private const val JAVA_CRASH_WINDOW_MS = 10_000L
        private const val KEY_LAST_EXIT = "last_exit_ms"
        private const val KEY_SUSPECT = "suspect_model"

        private val REPORTED = setOf(
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        )

        private fun reasonName(reason: Int) = when (reason) {
            ApplicationExitInfo.REASON_CRASH -> "Java crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
            ApplicationExitInfo.REASON_ANR -> "Not responding (ANR)"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Killed for low memory"
            ApplicationExitInfo.REASON_SIGNALED -> "Killed by signal"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Initialization failure"
            else -> "Exit reason $reason"
        }

        /**
         * Native tombstones are protobuf; their useful parts (abort message, signal, library and
         * symbol names) are plain strings inside. Keep printable runs of 6+ characters.
         */
        fun readableStrings(bytes: ByteArray, limit: Int = MAX_DETAILS): String {
            val out = StringBuilder()
            val run = StringBuilder()
            fun flush() {
                if (run.length >= 6 && out.length < limit) out.append(run).append('\n')
                run.setLength(0)
            }
            for (b in bytes) {
                if (out.length >= limit) break
                val c = b.toInt() and 0xff
                if (c in 0x20..0x7e) run.append(c.toChar()) else flush()
            }
            flush()
            return out.take(limit).toString()
        }

        private fun java.io.InputStream.readAtMost(max: Int): ByteArray {
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (buf.size() < max) {
                val n = read(chunk, 0, minOf(chunk.size, max - buf.size()))
                if (n < 0) break
                buf.write(chunk, 0, n)
            }
            return buf.toByteArray()
        }
    }
}
