package com.rheerkens.androidkeyboard.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
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

/**
 * One report kept on the phone until it is sent, or after a send that didn't go through.
 * [text] and [imageFile] are set only when the user chose to include the content.
 * [imageOmitted] means they asked to include an image that was too large to keep.
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

/** Whether an in-app report post reached the address, or stayed on the phone. */
sealed interface ReportDelivery {
    data object Sent : ReportDelivery
    data class Kept(val message: String) : ReportDelivery
}

/**
 * Reports and the JSON posted from the app. The address is a placeholder until a real one is
 * published. No key is added to the request.
 */
object ContentReports {
    const val PLACEHOLDER_ENDPOINT = "https://example.com/lowkey/ai-report"
    const val MAX_NOTE = 500
    const val MAX_REPORTS = 10
    const val MAX_ATTACHED_IMAGE_BYTES = 8 * 1024 * 1024

    /** An https address with no user, password, query or fragment, or null when [raw] isn't one. */
    fun normalizeEndpoint(raw: String): String? {
        val text = raw.trim()
        if (text.length !in 12..300 || text.any { it.isWhitespace() }) return null
        val uri = try { URI(text) } catch (_: URISyntaxException) { return null }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        return text
    }

    /** A usable address: the saved one, or the placeholder when that is blank or not https. */
    fun endpointOrPlaceholder(configured: String): String = normalizeEndpoint(configured) ?: PLACEHOLDER_ENDPOINT

    fun isPlaceholder(configured: String): Boolean {
        val host = try { URI(endpointOrPlaceholder(configured)).host } catch (_: URISyntaxException) { null }
        return host.equals("example.com", ignoreCase = true) ||
            host.equals("example.org", ignoreCase = true) ||
            host.equals("example.net", ignoreCase = true)
    }

    /** [code] from the report address. A redirect is not followed, so the body is not sent on. */
    fun deliveryOf(code: Int): ReportDelivery = when (code) {
        in 200..299 -> ReportDelivery.Sent
        in 300..399 -> ReportDelivery.Kept("The report address tried to redirect. The report is saved on this phone.")
        else -> ReportDelivery.Kept("The report address didn't accept it ($code). The report is saved on this phone.")
    }

    /**
     * The JSON body of a report. [imageBytes] are included only when the user opted in and the image
     * was small enough to keep. The text is included only when they opted in.
     */
    fun jsonBody(report: ContentReport, imageBytes: ByteArray?): String {
        val text = report.text?.takeIf { report.includeContent && it.isNotBlank() }
        val image = imageBytes?.takeIf {
            report.includeContent && report.imageFile != null && it.isNotEmpty() && it.size <= MAX_ATTACHED_IMAGE_BYTES
        }
        return JSONObject()
            .put("id", report.id)
            .put("kind", report.kind.name)
            .put("reason", report.reason.name)
            .put("note", report.note)
            .put("createdAt", Instant.ofEpochMilli(report.createdAtMs).toString())
            .put("includeContent", report.includeContent)
            .put("text", text ?: JSONObject.NULL)
            .put("imageMime", if (image != null) report.imageMime ?: JSONObject.NULL else JSONObject.NULL)
            .put("imageBase64", if (image != null) Base64.getEncoder().encodeToString(image) else JSONObject.NULL)
            .put("imageOmitted", report.imageOmitted)
            .toString()
    }

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
                .put("imageOmitted", report.imageOmitted))
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
