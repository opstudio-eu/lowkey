package com.rheerkens.androidkeyboard.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException

/**
 * Posts one report to the configured https address. No API key or token is added.
 * A failure leaves the report on the phone; this does not open another app.
 */
class ContentReportSender(
    private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) {
    fun send(endpoint: String, body: String): ReportDelivery {
        val url = ContentReports.normalizeEndpoint(endpoint)
            ?: return ReportDelivery.Kept("That report address isn't https. The report is saved on this phone.")
        val bytes = body.toByteArray(Charsets.UTF_8)
        return try {
            val connection = open(url)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                ContentReports.deliveryOf(connection.responseCode)
            } finally {
                connection.disconnect()
            }
        } catch (_: SSLException) {
            ReportDelivery.Kept("The report address's secure connection failed. The report is saved on this phone.")
        } catch (e: IOException) {
            val cleartext = e.message?.contains("Cleartext", ignoreCase = true) == true
            ReportDelivery.Kept(
                if (cleartext) "Use https:// for the report address. The report is saved on this phone."
                else "Can't reach the report address. The report is saved on this phone.",
            )
        } catch (_: SecurityException) {
            ReportDelivery.Kept("Lowkey isn't allowed to use the internet. The report is saved on this phone.")
        } catch (_: IllegalArgumentException) {
            ReportDelivery.Kept("That report address couldn't be used. The report is saved on this phone.")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
