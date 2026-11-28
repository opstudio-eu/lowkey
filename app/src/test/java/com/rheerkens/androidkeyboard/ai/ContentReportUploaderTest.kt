package com.rheerkens.androidkeyboard.ai

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** In-app report sending: what goes out, the queue, and retries. A fake transport; no network. */
class ContentReportUploaderTest {
    private lateinit var dir: File
    private lateinit var store: ContentReportStore
    private var now = 1_700_000_000_000L
    private val sent = mutableListOf<Pair<String, String>>()
    private var answer: () -> Int = { 201 }
    private val transport = ReportTransport { url, id, body ->
        sent += id to String(body, Charsets.UTF_8)
        assertEquals(ContentReports.ENDPOINT, url)
        answer()
    }
    private lateinit var uploader: ContentReportUploader

    @Before fun setUp() {
        dir = File.createTempFile("lowkey-reports", "").apply { delete(); mkdirs() }
        store = ContentReportStore(dir)
        uploader = ContentReportUploader(store, transport, "9.9.9", clock = { now })
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun file(include: Boolean, id: String, image: ByteArray? = byteArrayOf(1, 2, 3)) = store.add(
        ReportSubmission(ReportKind.IMAGE, ReportReason.VIOLENCE, "a note", include, "red fox", image, "image/png"),
        nowMs = now, id = id,
    )

    @Test fun endpointIsOneHttpsPlaceholderWithoutCredentials() {
        assertEquals("https://opstudio.eu/lowkey/report", ContentReports.ENDPOINT)
        assertFalse(ContentReports.ENDPOINT.contains("@"))
        assertFalse(ContentReports.ENDPOINT.contains("?"))
    }

    @Test fun sendsAndThenForgetsTheContent() {
        val report = file(include = true, id = "r1")
        assertTrue(File(dir, "r1.img").isFile)
        assertEquals(mapOf("r1" to SendOutcome.SENT), uploader.flush())
        val body = JSONObject(sent.single().second)
        assertEquals("r1", body.getString("id"))
        assertEquals("VIOLENCE", body.getString("reason"))
        assertEquals("IMAGE", body.getString("kind"))
        assertEquals("a note", body.getString("note"))
        assertEquals("9.9.9", body.getJSONObject("app").getString("version"))
        assertTrue(body.getBoolean("contentIncluded"))
        val content = body.getJSONObject("content")
        assertEquals("red fox", content.getString("text"))
        assertEquals("image/png", content.getJSONObject("image").getString("mimeType"))
        assertTrue(Base64.getDecoder().decode(content.getJSONObject("image").getString("base64")).contentEquals(byteArrayOf(1, 2, 3)))
        val after = store.find(report.id)!!
        assertEquals(ReportStatus.SENT, after.status)
        assertEquals(now, after.sentAtMs)
        assertNull(after.text)
        assertNull(after.imageFile)
        assertFalse(File(dir, "r1.img").exists())
        // Nothing left to send.
        assertTrue(uploader.flush().isEmpty())
        assertNull(uploader.nextDueMs())
    }

    @Test fun leavesContentOutUnlessIncluded() {
        file(include = false, id = "r2")
        uploader.flush()
        val raw = sent.single().second
        val body = JSONObject(raw)
        assertFalse(body.getBoolean("contentIncluded"))
        assertFalse(body.has("content"))
        assertFalse(raw.contains("red fox"))
        assertFalse(raw.contains("base64"))
    }

    @Test fun sendsNoDeviceIdentifiers() {
        file(include = false, id = "r3")
        uploader.flush()
        val keys = JSONObject(sent.single().second).keys().asSequence().toSet()
        assertEquals(setOf("schema", "id", "app", "kind", "reason", "note", "createdAt", "contentIncluded", "imageOmitted"), keys)
    }

    @Test fun offlineQueuesAndRetriesWithBackoff() {
        answer = { throw IOException("offline") }
        file(include = true, id = "r4")
        assertEquals(mapOf("r4" to SendOutcome.RETRY), uploader.flush())
        var report = store.find("r4")!!
        assertEquals(ReportStatus.QUEUED, report.status)
        assertEquals(1, report.attempts)
        assertEquals(now + 60_000, report.nextAttemptMs)
        assertTrue("content kept for the retry", File(dir, "r4.img").isFile)
        // Not due yet: nothing is sent.
        now += 30_000
        assertTrue(uploader.flush().isEmpty())
        // Due, still offline: the wait grows.
        now += 30_000
        assertEquals(mapOf("r4" to SendOutcome.RETRY), uploader.flush())
        report = store.find("r4")!!
        assertEquals(2, report.attempts)
        assertEquals(now + 5 * 60_000, report.nextAttemptMs)
        assertEquals(report.nextAttemptMs, uploader.nextDueMs())
        // Back online.
        now = report.nextAttemptMs
        answer = { 200 }
        assertEquals(mapOf("r4" to SendOutcome.SENT), uploader.flush())
        assertEquals(ReportStatus.SENT, store.find("r4")!!.status)
        // The queue survives a restart in between.
        assertEquals(ReportStatus.SENT, ContentReportStore(dir).find("r4")!!.status)
    }

    @Test fun sendNowIgnoresTheWait() {
        answer = { 503 }
        file(include = false, id = "r5")
        uploader.flush()
        assertTrue(uploader.flush().isEmpty())
        store.makeDue("r5")
        answer = { 202 }
        assertEquals(mapOf("r5" to SendOutcome.SENT), uploader.flush())
    }

    @Test fun aRejectedReportIsNotRetried() {
        answer = { 413 }
        file(include = true, id = "r6")
        assertEquals(mapOf("r6" to SendOutcome.REJECTED), uploader.flush())
        assertEquals(ReportStatus.REJECTED, store.find("r6")!!.status)
        assertFalse(File(dir, "r6.img").exists())
        now += ContentReports.MAX_BACKOFF_MS * 2
        assertTrue(uploader.flush().isEmpty())
    }

    @Test fun aMissingImageStillSendsTheReport() {
        file(include = true, id = "r7")
        File(dir, "r7.img").delete()
        assertEquals(mapOf("r7" to SendOutcome.SENT), uploader.flush())
        val body = JSONObject(sent.single().second)
        assertTrue(body.getBoolean("imageOmitted"))
        assertFalse(body.getJSONObject("content").has("image"))
    }

    @Test fun statusCodes() {
        assertEquals(SendOutcome.SENT, ContentReports.outcome(200))
        assertEquals(SendOutcome.SENT, ContentReports.outcome(204))
        listOf(400, 413, 415, 422).forEach { assertEquals(SendOutcome.REJECTED, ContentReports.outcome(it)) }
        // No service at the placeholder yet, overload, rate limits and redirects: tried again.
        listOf(301, 401, 403, 404, 408, 429, 500, 503).forEach { assertEquals("$it", SendOutcome.RETRY, ContentReports.outcome(it)) }
    }

    @Test fun backoffGrowsToACap() {
        assertEquals(60_000L, ContentReports.backoffMs(1))
        assertEquals(5 * 60_000L, ContentReports.backoffMs(2))
        assertEquals(25 * 60_000L, ContentReports.backoffMs(3))
        assertEquals(ContentReports.MAX_BACKOFF_MS, ContentReports.backoffMs(5))
        assertEquals(ContentReports.MAX_BACKOFF_MS, ContentReports.backoffMs(500))
    }

    @Test fun aFullListDropsSentReportsBeforeUnsentOnes() {
        answer = { throw IOException("offline") }
        file(include = false, id = "waiting")
        uploader.flush()
        answer = { 200 }
        repeat(ContentReports.MAX_REPORTS - 1) { index ->
            now += 1
            file(include = false, id = "sent-$index")
            uploader.flush()
        }
        now += 1
        file(include = false, id = "newest")
        val ids = store.reports.value.map { it.id }
        assertEquals(ContentReports.MAX_REPORTS, ids.size)
        assertTrue("an unsent report is kept", "waiting" in ids)
        assertFalse("the oldest sent one goes", "sent-0" in ids)
    }

    @Test fun reportsFromTheEmailOnlyVersionAreNotUploaded() {
        File(dir, "index.json").writeText(
            """{"reports":[{"id":"old","kind":"IMAGE","reason":"OTHER","note":"","createdAtMs":1,"includeContent":false}]}""",
        )
        val reloaded = ContentReportStore(dir)
        assertTrue(reloaded.reports.value.isEmpty())
        assertTrue(ContentReportUploader(reloaded, transport, "1").flush().isEmpty())
        assertTrue(sent.isEmpty())
    }

    @Test fun httpTransportPostsJsonOverHttpsOnly() {
        val connection = FakeConnection(204)
        val http = HttpReportTransport("1.2.3") { connection }
        assertEquals(204, http.post("https://opstudio.eu/lowkey/report", "id-1", "{}".toByteArray()))
        assertEquals("POST", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("application/json; charset=utf-8", connection.headers["Content-Type"])
        assertEquals("id-1", connection.headers["Idempotency-Key"])
        assertEquals("Lowkey/1.2.3", connection.headers["User-Agent"])
        assertEquals(setOf("Content-Type", "Idempotency-Key", "User-Agent"), connection.headers.keys)
        assertEquals("{}", connection.body.toString(Charsets.UTF_8.name()))
        assertTrue(connection.disconnected)
        try {
            http.post("http://opstudio.eu/lowkey/report", "id-2", "{}".toByteArray())
            throw AssertionError("plain HTTP must not be used")
        } catch (_: IOException) {
        }
    }

    @Test fun copyNamesWhatIsSentAndToWhom() {
        val without = ReportCopy.confirmation(ReportKind.IMAGE, include = false)
        assertTrue(without.contains("no content"))
        assertTrue(without.contains("Op Studio"))
        assertTrue(without.contains("IP address"))
        assertTrue(without.contains("removed"))
        assertTrue(ReportCopy.confirmation(ReportKind.IMAGE, include = true).contains("the image and its prompt"))
        assertTrue(ReportCopy.confirmation(ReportKind.IMPROVED_PROMPT, include = true).contains("the improved prompt"))
        val queued = file(include = false, id = "c1")
        assertEquals("Sending report…", ReportCopy.state(queued).title)
        assertEquals("Report saved", ReportCopy.state(queued.copy(attempts = 1)).title)
        assertEquals("Report sent", ReportCopy.state(queued.copy(status = ReportStatus.SENT)).title)
        assertEquals("Waiting to send", ReportCopy.statusLabel(queued.copy(attempts = 2)))
        listOf(without, ReportCopy.state(queued.copy(attempts = 1)).body).forEach {
            assertFalse("no email hand-off: $it", it.contains("email", ignoreCase = true))
        }
    }

    private class FakeConnection(private val code: Int) : HttpURLConnection(URL("https://opstudio.eu/lowkey/report")) {
        val headers = linkedMapOf<String, String>()
        val body = ByteArrayOutputStream()
        var disconnected = false
        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
        override fun getOutputStream(): OutputStream = body
        override fun getResponseCode(): Int = code
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
    }
}
