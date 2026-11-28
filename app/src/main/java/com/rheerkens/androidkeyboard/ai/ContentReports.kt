package com.rheerkens.androidkeyboard.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant

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
 * One report kept on the phone. [text] and [imageFile] are set only when the user chose to include
 * the content for an email they send themselves. [imageOmitted] means they asked to include an image
 * that was too large to keep.
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

/** The email the user's own mail app will show. Lowkey does not send it. */
data class ReportEmail(val address: String, val subject: String, val body: String, val attachImage: Boolean)

/**
 * Reports and the email text. Nothing here contacts a server. The address is a placeholder until
 * Op Studio publishes a real one; the user can change it, and the mail app is what sends.
 */
object ContentReports {
    const val PLACEHOLDER_EMAIL = "ai-reports@example.com"
    const val MAX_NOTE = 500
    const val MAX_REPORTS = 10
    const val MAX_ATTACHED_IMAGE_BYTES = 8 * 1024 * 1024
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun normalizeEmail(raw: String): String? {
        val text = raw.trim().removePrefix("mailto:").trim()
        if (text.length !in 6..120 || !EMAIL.matches(text)) return null
        return text
    }

    /** A usable address: the saved one, or the placeholder when that is blank or not an address. */
    fun addressOrPlaceholder(configured: String): String = normalizeEmail(configured) ?: PLACEHOLDER_EMAIL

    fun isPlaceholder(configured: String): Boolean =
        addressOrPlaceholder(configured).equals(PLACEHOLDER_EMAIL, ignoreCase = true)

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

    fun email(report: ContentReport, configuredAddress: String): ReportEmail {
        val address = addressOrPlaceholder(configuredAddress)
        val whenText = Instant.ofEpochMilli(report.createdAtMs).toString()
        val content = buildString {
            if (report.includeContent && !report.text.isNullOrBlank()) {
                val label = if (report.kind == ReportKind.IMPROVED_PROMPT) "Improved prompt" else "Prompt"
                append("$label:\n${report.text}\n\n")
            }
            when {
                report.imageFile != null -> append("The image is attached.\n")
                report.imageOmitted ->
                    append("The image was too large to attach. It was removed on this phone and is not in this email.\n")
                !report.includeContent ->
                    append("The content was not included. It was removed on this phone and is not in this email.\n")
                report.kind == ReportKind.IMAGE ->
                    append("The image was not included. It was removed on this phone and is not in this email.\n")
            }
        }
        val body = buildString {
            append("Lowkey AI content report\n\n")
            append("Kind: ${report.kind.label}\n")
            append("Reason: ${report.reason.label}\n")
            append("Note: ${report.note.ifBlank { "(none)" }}\n")
            append("When: $whenText\n\n")
            append(content)
            append("\nLowkey composed this email because you asked it to. It does not send reports by itself.\n")
        }
        return ReportEmail(
            address = address,
            subject = "Lowkey AI content report",
            body = body,
            attachImage = report.imageFile != null,
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
