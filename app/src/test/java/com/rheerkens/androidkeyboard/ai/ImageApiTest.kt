package com.rheerkens.androidkeyboard.ai

import com.rheerkens.androidkeyboard.settings.Settings
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ImageApiTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    /** The Create image endpoint for an own server's address, and why there is none. */
    private data class Endpoint(val url: String?, val problem: String?)
    private fun own(url: String, local: Boolean = false) = AiConnection.apiBase(AiService.OWN_SERVER, url, local)
        .let { Endpoint(it.endpoint(AiAction.CREATE_IMAGE.path), it.problem) }

    @Test
    fun openAiAlwaysUsesItsOwnEndpoint() {
        assertEquals("https://api.openai.com/v1/images/generations",
            AiConnection.apiBase(AiService.OPENAI, "https://ignored.example").endpoint(AiAction.CREATE_IMAGE.path))
    }

    @Test
    fun ownServerGetsTheImageApiPath() {
        assertEquals("https://images.example.net/v1/images/generations", own("https://images.example.net").url)
        assertEquals("https://images.example.net/v1/images/generations", own(" https://images.example.net/ ").url)
        assertEquals("https://images.example.net:8443/api/v1/images/generations", own("https://images.example.net:8443/api").url)
        assertEquals("https://h.example/custom/images/generations", own("https://h.example/custom/images/generations").url)
        assertEquals("https://h.example/v1/images/generations", own("https://h.example/v1").url)
        assertEquals("https://h.example/v1/images/generations", own("https://h.example/v1/").url)
    }

    @Test
    fun ownServerNeedsHttpsExceptLocallyInDebug() {
        assertNull(own("http://images.example.net").url)
        assertNull(own("http://127.0.0.1:8787").url)
        assertEquals("http://127.0.0.1:8787/v1/images/generations", own("http://127.0.0.1:8787", local = true).url)
        assertNull(own("http://images.example.net", local = true).url)
    }

    @Test
    fun badAddressesSayWhy() {
        assertEquals("Add your server's address", own("  ").problem)
        assertTrue(own("images.example.net").problem!!.startsWith("Use a full address"))
        assertTrue(own("ftp://images.example.net").problem!!.startsWith("Use a full address"))
        assertTrue(own("https://name:secret@localhost").problem!!.contains("Access token"))
        assertTrue(own("https://images.example.net/?key=abc").problem!!.contains("Access token"))
        assertTrue(own("https://images.example.net/#x").problem!!.contains("Access token"))
        assertEquals("That port number doesn't exist", own("https://images.example.net:99999").problem)
    }

    @Test
    fun anImageReplacesOnlyTheClipFromBeforeOrLowkeysOwn() {
        // Nothing copied meanwhile.
        assertTrue(mayReplaceClip(current = 100, atSubmit = 100, lastOwn = 0))
        // Another image Lowkey made meanwhile: the newest image takes its place.
        assertTrue(mayReplaceClip(current = 200, atSubmit = 100, lastOwn = 200))
        // The user copied something meanwhile: it stays.
        assertTrue(!mayReplaceClip(current = 300, atSubmit = 100, lastOwn = 200))
        assertTrue(!mayReplaceClip(current = 300, atSubmit = 100, lastOwn = 0))
    }

    @Test
    fun requestAsksForOneImage() {
        val body = JSONObject(ImageApi.requestBody("a cat \"in\" sunglasses", "gpt-image-2.6-flare", ImageQuality.LOW))
        assertEquals("gpt-image-2.6-flare", body.getString("model"))
        assertEquals("a cat \"in\" sunglasses", body.getString("prompt"))
        assertEquals(1, body.getInt("n"))
        assertEquals("low", body.getString("quality"))
        assertTrue(!JSONObject(ImageApi.requestBody("x", "m", null)).has("quality"))
        assertEquals(ImageApi.MAX_PROMPT_CHARS, JSONObject(ImageApi.requestBody("y".repeat(5000), "m", null)).getString("prompt").length)
    }

    @Test
    fun readsABase64Image() {
        val body = """{"created":1,"data":[{"b64_json":"${Base64.getEncoder().encodeToString(png)}"}]}"""
        val reply = ImageApi.parse(200, body) as ImageReply.Image
        assertEquals("image/png", reply.mimeType)
        assertArrayEquals(png, reply.bytes)
    }

    @Test
    fun rejectsWhatIsNotAnImage() {
        val text = Base64.getEncoder().encodeToString("hello".toByteArray())
        assertTrue(ImageApi.parse(200, """{"data":[{"b64_json":"$text"}]}""") is ImageReply.Failed)
        assertTrue(ImageApi.parse(200, """{"data":[]}""") is ImageReply.Failed)
        assertTrue(ImageApi.parse(200, "<html>") is ImageReply.Failed)
    }

    @Test
    fun refusesImagesLargerThanTheHistoryKeeps() {
        val huge = "A".repeat(ImageApi.MAX_IMAGE_BYTES / 3 * 4 + 8)
        assertEquals(ImageReply.Failed("The image is too large"), ImageApi.parse(200, """{"data":[{"b64_json":"$huge"}]}"""))
    }

    @Test
    fun followsOnlyHttpsLinks() {
        assertEquals(ImageReply.Link("https://cdn.example/x.png"), ImageApi.parse(200, """{"data":[{"url":"https://cdn.example/x.png"}]}"""))
        assertTrue(ImageApi.parse(200, """{"data":[{"url":"http://cdn.example/x.png"}]}""") is ImageReply.Failed)
        // A JSON null must not read as the text "null" (Android's optString does that).
        assertEquals(ImageReply.Link("https://cdn.example/x.png"),
            ImageApi.parse(200, """{"data":[{"b64_json":null,"url":"https://cdn.example/x.png"}]}"""))
    }

    @Test
    fun errorsArePlain() {
        assertEquals(ImageReply.Failed("The API key or token was refused", refused = true),
            ImageApi.parse(401, """{"error":{"message":"Incorrect API key provided: sk-abc."}}"""))
        assertEquals(ImageReply.Failed(ContentSafety.PROVIDER_REFUSAL, safety = true),
            ImageApi.parse(400, """{"error":{"message":"Your request was rejected by the safety system. More text here."}}"""))
        assertEquals(ImageReply.Failed("Not done: The prompt is empty"),
            ImageApi.parse(400, """{"error":{"message":"The prompt is empty"}}"""))
        assertEquals(ImageReply.Failed("Too many requests or no credit left"), ImageApi.parse(429, ""))
        assertEquals(ImageReply.Failed("The AI service had a problem (502). Try again"), ImageApi.parse(502, "Bad gateway"))
    }

    @Test
    fun sniffsCommonImageTypes() {
        assertEquals("image/png", ImageApi.sniffMime(png))
        assertEquals("image/jpeg", ImageApi.sniffMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals("image/webp", ImageApi.sniffMime("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("image/gif", ImageApi.sniffMime("GIF89a".toByteArray()))
        assertNull(ImageApi.sniffMime(byteArrayOf(1, 2)))
    }

    @Test
    fun jobNeedsAKeyForOpenAi() {
        val on = Settings(aiKeyboard = true)
        assertEquals("Add your OpenAI API key in Settings › Experimental", imageJob(on, "cat", { null }, false).problem)
        val job = imageJob(on, "  cat  ", { "sk-test" }, false).job!!
        assertEquals("cat", job.prompt)
        assertEquals("sk-test", job.token)
        assertEquals("https://api.openai.com/v1/images/generations", job.endpoint)
        assertEquals(ImageModel.FLARE.id, job.model)
        assertEquals("Describe the image first", imageJob(on, " ", { "sk-test" }, false).problem)
    }

    @Test
    fun ownServerTokenIsOptional() {
        val s = Settings(aiKeyboard = true, aiService = AiService.OWN_SERVER, aiServerUrl = "https://img.example")
        val job = imageJob(s, "cat", { null }, false).job!!
        assertNull(job.token)
        assertNull(job.model)
        assertTrue(!JSONObject(ImageApi.requestBody("x", null, null)).has("model"))
        assertEquals("flux", imageJob(s.copy(imageServerModel = " flux "), "cat", { null }, false).job!!.model)
        assertEquals("https://img.example/v1/images/generations", job.endpoint)
        assertEquals("Check your server's address in Settings › Experimental",
            imageJob(s.copy(aiServerUrl = "http://img.example"), "cat", { null }, false).problem)
    }
}
