package com.rheerkens.androidkeyboard.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Reports saved under [dir] (the app's files, not a backup and not the settings export).
 * The index is JSON; an included image is a separate file named by the report id.
 */
class ContentReportStore(private val dir: File) {
    private val index = File(dir, "index.json")
    private val _reports = MutableStateFlow(load())
    val reports: StateFlow<List<ContentReport>> = _reports.asStateFlow()

    fun find(id: String): ContentReport? = _reports.value.firstOrNull { it.id == id }

    fun imageFile(report: ContentReport): File? =
        report.imageFile?.let { File(dir, it) }?.takeIf { it.isFile }

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
            val next = (listOf(prepared.report) + _reports.value).take(ContentReports.MAX_REPORTS)
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

    @Synchronized
    fun updateNote(id: String, note: String) {
        val next = _reports.value.map { report ->
            if (report.id != id) report else report.copy(note = note.trim().take(ContentReports.MAX_NOTE))
        }
        if (next == _reports.value) return
        write(next)
        _reports.value = next
    }

    @Synchronized
    fun delete(id: String) {
        val report = _reports.value.firstOrNull { it.id == id } ?: return
        val next = _reports.value.filterNot { it.id == id }
        write(next)
        report.imageFile?.let { File(dir, it).delete() }
        _reports.value = next
    }

    private fun load(): List<ContentReport> =
        if (!index.isFile) emptyList() else ContentReports.decode(index.readText())

    private fun write(reports: List<ContentReport>) {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(ContentReports.encode(reports))
        if (!tmp.renameTo(index)) {
            index.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
