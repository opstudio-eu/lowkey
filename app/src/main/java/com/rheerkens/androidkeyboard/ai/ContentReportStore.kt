package com.rheerkens.androidkeyboard.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Reports saved under [dir] (the app's files, not a backup and not the settings export) until they
 * are sent, and a short list of sent ones. The index is JSON; an included image is a separate file
 * named by the report id, deleted once the report is sent.
 */
class ContentReportStore(private val dir: File) {
    private val index = File(dir, "index.json")
    private val _reports = MutableStateFlow(load())
    val reports: StateFlow<List<ContentReport>> = _reports.asStateFlow()

    fun find(id: String): ContentReport? = _reports.value.firstOrNull { it.id == id }

    fun imageFile(report: ContentReport): File? =
        report.imageFile?.let { File(dir, it) }?.takeIf { it.isFile }

    /** The included image's bytes, or null when there is none or it can't be read. */
    fun readImage(report: ContentReport): ByteArray? =
        imageFile(report)?.let { file -> try { file.readBytes() } catch (_: IOException) { null } }

    @Synchronized
    fun add(
        submission: ReportSubmission,
        nowMs: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString(),
    ): ContentReport {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't create the report folder")
        val prepared = ContentReports.prepare(submission, id, nowMs)
        val image = prepared.report.imageFile?.let { File(dir, it) }
        try {
            prepared.imageBytes?.let { bytes -> image?.writeBytes(bytes) }
            val next = ContentReports.keep(listOf(prepared.report) + _reports.value)
            val dropped = _reports.value.filter { old -> next.none { it.id == old.id } }
            write(next)
            dropped.forEach { old -> old.imageFile?.let { File(dir, it).delete() } }
            _reports.value = next
        } catch (e: IOException) {
            image?.delete()
            throw e
        }
        return prepared.report
    }

    /** Only a report still waiting to be sent takes a new note. */
    @Synchronized
    fun updateNote(id: String, note: String) = change(id) { report ->
        if (report.status != ReportStatus.QUEUED) report
        else report.copy(note = note.trim().take(ContentReports.MAX_NOTE))
    }

    /** Received: the included words and image are no longer kept on the phone. */
    @Synchronized
    fun markSent(id: String, nowMs: Long) {
        val before = find(id) ?: return
        change(id) { it.copy(status = ReportStatus.SENT, sentAtMs = nowMs, text = null, imageFile = null, imageMime = null) }
        before.imageFile?.let { File(dir, it).delete() }
    }

    /** Not received: tried again automatically after [ContentReports.backoffMs]. */
    @Synchronized
    fun markRetry(id: String, nowMs: Long) = change(id) { report ->
        if (report.status != ReportStatus.QUEUED) report
        else {
            val attempts = report.attempts + 1
            report.copy(attempts = attempts, nextAttemptMs = nowMs + ContentReports.backoffMs(attempts))
        }
    }

    /** The server turned it down; the content is dropped, as it won't be sent. */
    @Synchronized
    fun markRejected(id: String) {
        val before = find(id) ?: return
        change(id) { it.copy(status = ReportStatus.REJECTED, text = null, imageFile = null, imageMime = null) }
        before.imageFile?.let { File(dir, it).delete() }
    }

    /** "Send now": the next flush tries it even while it waits for a retry. */
    @Synchronized
    fun makeDue(id: String) = change(id) { report ->
        if (report.status == ReportStatus.QUEUED) report.copy(nextAttemptMs = 0) else report
    }

    @Synchronized
    fun delete(id: String) {
        val report = _reports.value.firstOrNull { it.id == id } ?: return
        val next = _reports.value.filterNot { it.id == id }
        write(next)
        report.imageFile?.let { File(dir, it).delete() }
        _reports.value = next
    }

    private fun change(id: String, update: (ContentReport) -> ContentReport) {
        val next = _reports.value.map { if (it.id == id) update(it) else it }
        if (next == _reports.value) return
        write(next)
        _reports.value = next
    }

    private fun load(): List<ContentReport> =
        if (!index.isFile) emptyList() else ContentReports.decode(index.readText())

    private fun write(reports: List<ContentReport>) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't create the report folder")
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(ContentReports.encode(reports))
        if (!tmp.renameTo(index)) {
            index.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
