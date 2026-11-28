package com.rheerkens.androidkeyboard.ai

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens the user's email app with a report already written. Returns false when no app can take it.
 * Lowkey does not send the message.
 */
object ContentReportMail {
    fun send(context: Context, report: ContentReport, image: File?, configuredAddress: String): Boolean {
        val email = ContentReports.email(report, configuredAddress)
        val file = image?.takeIf { it.isFile }
        val intent = if (email.attachImage && file != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.clipboard", file)
            imageEmail(email, uri, report.imageMime ?: "image/*", context)
        } else {
            textEmail(email)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Send report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun textEmail(email: ReportEmail): Intent {
        val uri = Uri.parse("mailto:${Uri.encode(email.address)}").buildUpon()
            .appendQueryParameter("subject", email.subject)
            .appendQueryParameter("body", email.body)
            .build()
        return Intent(Intent.ACTION_SENDTO, uri)
    }

    private fun imageEmail(email: ReportEmail, uri: Uri, mime: String, context: Context): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email.address))
            putExtra(Intent.EXTRA_SUBJECT, email.subject)
            putExtra(Intent.EXTRA_TEXT, email.body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
