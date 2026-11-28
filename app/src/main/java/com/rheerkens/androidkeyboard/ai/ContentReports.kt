package com.rheerkens.androidkeyboard.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.Base64

/** What was flagged. */
enum class ReportKind(val label: String) {
    IMAGE("Generated image"),
    IMPROVED_PROMPT("Improved prompt"),
}

/** Why the user flagged it. The labels are the form's choices. */
enum class ReportReason(val label: String, val detail: String) {
    CHILD_SEXUAL("Sexual content involving a child", "A child is shown or described sexually"),
    SEXUAL("Sexual content", "Nudity or other sexual content"),
    VIOLENCE("Violence", "Graphic violence or gore"),
    HATE("Hate or harassment", "An attack on a person or group"),
    OTHER("Something else", "Another problem with this result"),
}

/** Where a report is in the queue. */
enum class ReportStatus {
    /** Saved on the phone, waiting to be sent (first try, or offline and retrying). */
    QUEUED,
    /** Op Studio's server received it. The included content is then deleted from the phone. */
    SENT,
    /** The server turned the report itself down (for example too large). Not retried. */
    REJECTED,
}

/**
 * One report. [text] and [imageFile] are set only when the user chose to include the content.
 * [imageOmitted] means they asked to include an image that was too large to send. Once [status] is
 * [ReportStatus.SENT], the text and image are dropped from the phone.
 */
data class ContentReport(
    val id: String,
    val kind: ReportKind,
    val reason: ReportReason,
    val note: String,
    val createdAtMs: Long,
    val includeContent: Boolean,
    val text: String?,
    val imageFile: String?,
    val imageMime: String?,
    val imageOmitted: Boolean,
    val status: ReportStatus = ReportStatus.QUEUED,
    /** Failed sends so far. */
    val attempts: Int = 0,
    /** No automatic retry before this time. */
    val nextAttemptMs: Long = 0,
    val sentAtMs: Long? = null,
)

/** What the form collected. Image bytes are dropped unless [includeContent] is set. */
data class ReportSubmission(
    val kind: ReportKind,
    val reason: ReportReason,
    val note: String,
    val includeContent: Boolean,
    val text: String?,
    val imageBytes: ByteArray?,
    val imageMime: String?,
)

data class PreparedReport(val report: ContentReport, val imageBytes: ByteArray?, val imageOmitted: Boolean)

/** How one send ended, from the HTTP status code (or a connection that failed: [RETRY]). */
enum class SendOutcome { SENT, RETRY, REJECTED }

/**
 * Reports and what is uploaded. Lowkey sends a report itself, over HTTPS to [ENDPOINT], only after
 * the user confirms it in the report screen. No account, key or device identifier goes with it.
 */
object ContentReports {
    /**
     * The one place the report address is set. A placeholder until Op Studio runs the service;
     * until then sends fail and reports wait in the queue.
     */
    const val ENDPOINT = "https://opstudio.eu/lowkey/report"
    const val MAX_NOTE = 500
    const val MAX_REPORTS = 10
    const val MAX_ATTACHED_IMAGE_BYTES = 8 * 1024 * 1024
    /** Payload version, so the server can read older apps' reports. */
    const val SCHEMA = 1
    private const val MINUTE_MS = 60_000L
    const val MAX_BACKOFF_MS = 6 * 60 * MINUTE_MS

    fun prepare(submission: ReportSubmission, id: String, nowMs: Long): PreparedReport {
        val note = cleanNote(submission.note)
        val text = if (submission.includeContent) {
            submission.text?.trim()?.take(ImageApi.MAX_PROMPT_CHARS)?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val bytes = submission.imageBytes?.takeIf { submission.includeContent && it.isNotEmpty() }
        val omitted = bytes != null && bytes.size > MAX_ATTACHED_IMAGE_BYTES
        val kept = bytes?.takeIf { !omitted }
        val mime = submission.imageMime?.takeIf { kept != null && it in ModerationApi.IMAGE_TYPES }
        val file = if (kept != null && mime != null) "$id.img" else null
        return PreparedReport(
            ContentReport(
                id = id,
                kind = submission.kind,
                reason = submission.reason,
                note = note,
                createdAtMs = nowMs,
                includeContent = submission.includeContent,
                text = text,
                imageFile = file,
                imageMime = mime,
                imageOmitted = omitted,
            ),
            imageBytes = if (file != null) kept else null,
            imageOmitted = omitted,
        )
    }

    /**
     * The JSON body sent to [ENDPOINT]: what the user saw and chose, nothing else. The content
     * (words and image) is in it only when they included it; [image] is that file's bytes.
     */
    fun payload(report: ContentReport, image: ByteArray?, appVersion: String): String {
        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("id", report.id)
            .put("app", JSONObject().put("name", "Lowkey").put("version", appVersion))
            .put("kind", report.kind.name)
            .put("reason", report.reason.name)
            .put("note", report.note)
            .put("createdAt", Instant.ofEpochMilli(report.createdAtMs).toString())
            .put("contentIncluded", report.includeContent)
            .put("imageOmitted", report.imageOmitted)
        if (report.includeContent) {
            val content = JSONObject()
            report.text?.let { content.put("text", it) }
            val mime = report.imageMime
            if (image != null && mime != null) {
                content.put("image", JSONObject()
                    .put("mimeType", mime)
                    .put("base64", Base64.getEncoder().encodeToString(image)))
            }
            json.put("content", content)
        }
        return json.toString()
    }

    /**
     * 2xx is received. A request the server calls malformed or too large won't get better by sending
     * it again. Everything else (no service yet, overload, rate limits, redirects) is retried later.
     */
    fun outcome(httpStatus: Int): SendOutcome = when (httpStatus) {
        in 200..299 -> SendOutcome.SENT
        400, 413, 415, 422 -> SendOutcome.REJECTED
        else -> SendOutcome.RETRY
    }

    /** Wait after the [attempts]th failed send: 1, 5, 25 minutes, then about 2 hours, at most [MAX_BACKOFF_MS]. */
    fun backoffMs(attempts: Int): Long {
        var wait = MINUTE_MS
        repeat((attempts - 1).coerceIn(0, 8)) { wait = (wait * 5).coerceAtMost(MAX_BACKOFF_MS) }
        return wait
    }

    /** Reports to keep when a new one is added: sent and turned-down ones go first, then the oldest. */
    fun keep(reports: List<ContentReport>, max: Int = MAX_REPORTS): List<ContentReport> {
        if (reports.size <= max) return reports
        val dropFirst = reports.filter { it.status != ReportStatus.QUEUED }.sortedBy { it.createdAtMs }
        val dropLater = reports.filter { it.status == ReportStatus.QUEUED }.sortedBy { it.createdAtMs }
        val dropped = (dropFirst + dropLater).take(reports.size - max).map { it.id }.toSet()
        return reports.filterNot { it.id in dropped }
    }

    fun encode(reports: List<ContentReport>): String {
        val array = JSONArray()
        reports.forEach { report ->
            array.put(JSONObject()
                .put("id", report.id)
                .put("kind", report.kind.name)
                .put("reason", report.reason.name)
                .put("note", report.note)
                .put("createdAtMs", report.createdAtMs)
                .put("includeContent", report.includeContent)
                .put("text", report.text ?: JSONObject.NULL)
                .put("imageFile", report.imageFile ?: JSONObject.NULL)
                .put("imageMime", report.imageMime ?: JSONObject.NULL)
                .put("imageOmitted", report.imageOmitted)
                .put("status", report.status.name)
                .put("attempts", report.attempts)
                .put("nextAttemptMs", report.nextAttemptMs)
                .put("sentAtMs", report.sentAtMs ?: JSONObject.NULL))
        }
        return JSONObject().put("reports", array).toString()
    }

    fun decode(json: String): List<ContentReport> {
        val array = try { JSONObject(json).optJSONArray("reports") } catch (_: JSONException) { null } ?: return emptyList()
        val out = ArrayList<ContentReport>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val kind = enumOrNull<ReportKind>(item.opt("kind") as? String) ?: continue
            val reason = enumOrNull<ReportReason>(item.opt("reason") as? String) ?: continue
            // No status: a report from the unreleased email-only version, which promised to keep it on
            // the phone. It is not uploaded now without the user confirming it.
            val status = enumOrNull<ReportStatus>(item.opt("status") as? String) ?: continue
            val id = (item.opt("id") as? String)?.takeIf { raw -> raw.isNotBlank() && raw.none { it == '/' || it == '\\' } } ?: continue
            out.add(ContentReport(
                id = id,
                kind = kind,
                reason = reason,
                note = cleanNote(item.opt("note") as? String ?: ""),
                createdAtMs = item.optLong("createdAtMs"),
                includeContent = item.optBoolean("includeContent", false),
                text = (item.opt("text") as? String)?.takeIf { it.isNotBlank() },
                imageFile = (item.opt("imageFile") as? String)?.takeIf { it == "$id.img" },
                imageMime = (item.opt("imageMime") as? String)?.takeIf { it in ModerationApi.IMAGE_TYPES },
                imageOmitted = item.optBoolean("imageOmitted", false),
                status = status,
                attempts = item.optInt("attempts", 0).coerceAtLeast(0),
                nextAttemptMs = item.optLong("nextAttemptMs", 0),
                sentAtMs = (item.opt("sentAtMs") as? Number)?.toLong(),
            ))
        }
        return out
    }

    /**
     * Bytes of an image in [cacheDir], or null when [path] is missing, too large, or not inside that
     * directory. The canonical path is checked so a link can't point outside it.
     */
    fun readCacheImage(cacheDir: File, path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        val root = try { cacheDir.canonicalFile } catch (_: IOException) { return null }
        val file = try { File(path).canonicalFile } catch (_: IOException) { return null }
        val inside = file == root || file.path.startsWith(root.path + File.separator)
        if (!inside || !file.isFile || file.length() > ImageApi.MAX_IMAGE_BYTES) return null
        return try { file.readBytes() } catch (_: IOException) { null }
    }

    private fun cleanNote(raw: String): String =
        raw.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "").trim().take(MAX_NOTE)

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
