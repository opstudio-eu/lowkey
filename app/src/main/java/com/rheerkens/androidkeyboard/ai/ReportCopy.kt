package com.rheerkens.androidkeyboard.ai

/** What a report screen says, kept apart from the UI so tests can check it promises only what happens. */
object ReportCopy {
    data class State(val title: String, val body: String)

    fun includeLabel(kind: ReportKind): String = when (kind) {
        ReportKind.IMAGE -> "Include the image and its prompt in the report. Off: Op Studio gets only the reason and your note."
        ReportKind.IMPROVED_PROMPT -> "Include the improved prompt in the report. Off: Op Studio gets only the reason and your note."
    }

    /** The confirm dialog: everything the request carries, and who receives it. */
    fun confirmation(kind: ReportKind, include: Boolean): String {
        val content = when {
            !include -> "no content"
            kind == ReportKind.IMAGE -> "the image and its prompt"
            else -> "the improved prompt"
        }
        val what = if (kind == ReportKind.IMAGE) "image" else "improved prompt"
        return "Lowkey sends the reason, your note, $content, the app version and the time to Op Studio over an " +
            "encrypted connection. Op Studio's server also sees your IP address. No account or device ID is sent. " +
            "The $what is removed from Lowkey. Without a connection, the report waits on this phone and is sent automatically later."
    }

    /** The result screen, from the report's place in the queue ([report] null: deleted meanwhile). */
    fun state(report: ContentReport?): State = when {
        report == null -> State("Report removed", "You deleted this report before it was sent.")
        report.status == ReportStatus.SENT -> State("Report sent", "Thank you. Op Studio received your report.")
        report.status == ReportStatus.REJECTED ->
            State("Report not accepted", "Op Studio's server turned this report down. You can delete it under Settings › Experimental.")
        report.attempts == 0 -> State("Sending report…", "Sending your report to Op Studio.")
        else -> State(
            "Report saved",
            "Lowkey couldn't reach Op Studio right now. The report waits on this phone and is sent automatically " +
                "when a connection is back. You'll find it under Settings › Experimental.",
        )
    }

    /** One word or two beside a report in Settings. */
    fun statusLabel(report: ContentReport): String = when (report.status) {
        ReportStatus.SENT -> "Sent"
        ReportStatus.REJECTED -> "Not accepted"
        ReportStatus.QUEUED -> if (report.attempts == 0) "Sending" else "Waiting to send"
    }
}
