package com.rheerkens.androidkeyboard.ai

import com.rheerkens.androidkeyboard.settings.Settings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Pictures added to the prompt (`images/edits`) and Improve (`chat/completions`). */
class PromptInputTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 9, 9)
    private val on = Settings(aiKeyboard = true)
    private val own = Settings(aiKeyboard = true, aiService = AiService.OWN_SERVER, aiServerUrl = "https://ai.example")

    @Test
    fun picturesSendTheRequestToEdits() {
        val job = imageJob(on, "make it night", { "sk-test" }, false, listOf(InputImage(png, "image/png"))).job!!
        assertEquals("https://api.openai.com/v1/images/edits", job.endpoint)
        assertEquals(1, job.images.size)
        assertEquals("https://api.openai.com/v1/images/generations", imageJob(on, "a cat", { "sk-test" }, false).job!!.endpoint)
        assertEquals("Say what to change first", imageJob(on, " ", { "sk-test" }, false, listOf(InputImage(png, "image/png"))).problem)
        val many = List(ImageApi.MAX_INPUT_IMAGES + 2) { InputImage(png, "image/png") }
        assertEquals(ImageApi.MAX_INPUT_IMAGES, imageJob(on, "x", { "sk-test" }, false, many).job!!.images.size)
    }

    @Test
    fun ownServerAddressMayBeAnyActionsEndpoint() {
        fun base(url: String) = AiConnection.apiBase(AiService.OWN_SERVER, url).url
        assertEquals("https://h.example/v1", base("https://h.example/v1/images/edits"))
        assertEquals("https://h.example/v1", base("https://h.example/v1/chat/completions"))
        assertEquals("https://ai.example/v1/images/edits",
            imageJob(own, "x", { null }, false, listOf(InputImage(png, "image/png"))).job!!.endpoint)
    }

    @Test
    fun oneEditPictureIsImageSeveralAreImageArray() {
        fun body(vararg images: InputImage) =
            ImageApi.editBody("b0und", "a \"cat\"", "gpt-image-2.6-flare", ImageQuality.MEDIUM, images.toList())
                .toString(Charsets.ISO_8859_1)
        val one = body(InputImage(png, "image/png"))
        assertTrue(one.contains("--b0und\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\ngpt-image-2.6-flare\r\n"))
        assertTrue(one.contains("name=\"prompt\"\r\n\r\na \"cat\"\r\n"))
        assertTrue(one.contains("name=\"n\"\r\n\r\n1\r\n"))
        assertTrue(one.contains("name=\"quality\"\r\n\r\nmedium\r\n"))
        assertTrue(one.contains("name=\"image\"; filename=\"image-1.png\"\r\nContent-Type: image/png\r\n\r\n" +
            png.toString(Charsets.ISO_8859_1) + "\r\n"))
        assertTrue(one.endsWith("--b0und--\r\n"))
        val two = body(InputImage(png, "image/png"), InputImage(jpeg, "image/jpeg"))
        assertEquals(2, Regex("name=\"image\\[]\"").findAll(two).count())
        assertTrue(two.contains("filename=\"image-2.jpg\"\r\nContent-Type: image/jpeg"))
        assertFalse(two.contains("name=\"image\";"))
        val noModel = ImageApi.editBody("b", "x", null, null, listOf(InputImage(png, "image/png"))).toString(Charsets.ISO_8859_1)
        assertFalse(noModel.contains("name=\"model\""))
        assertFalse(noModel.contains("name=\"quality\""))
    }

    @Test
    fun clientUploadsPicturesAsMultipart() {
        val answer = """{"data":[{"b64_json":"${Base64.getEncoder().encodeToString(png)}"}]}"""
        val fake = FakeConnection(200, answer)
        val job = imageJob(on, "add a hat", { "sk-test" }, false, listOf(InputImage(jpeg, "image/jpeg"))).job!!
        val reply = AiClient { fake }.generate(job)
        assertTrue(reply is ImageReply.Image)
        val type = fake.headers["Content-Type"]!!
        assertTrue(type.startsWith("multipart/form-data; boundary="))
        val boundary = type.substringAfter("boundary=")
        val sent = fake.sent.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(sent.startsWith("--$boundary\r\n"))
        assertTrue(sent.contains(jpeg.toString(Charsets.ISO_8859_1)))
        assertEquals("Bearer sk-test", fake.headers["Authorization"])
    }

    @Test
    fun improveAsksTheTextModel() {
        val job = promptJob(on, "  cat on a skateboard ", { "sk-test" }, false, imageCount = 0).job!!
        assertEquals("https://api.openai.com/v1/chat/completions", job.endpoint)
        assertEquals(PromptApi.OPENAI_MODEL, job.model)
        val body = JSONObject(PromptApi.requestBody(job.prompt, job.model, job.openAi, job.imageCount))
        assertEquals(PromptApi.OPENAI_MODEL, body.getString("model"))
        assertEquals("none", body.getString("reasoning_effort"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertFalse(messages.getJSONObject(0).getString("content").contains("can't see"))
        assertEquals("cat on a skateboard", messages.getJSONObject(1).getString("content"))
        assertEquals("Type a few words first", promptJob(on, " ", { "sk-test" }, false, 0).problem)
        assertEquals("Add your OpenAI API key in Settings › Experimental", promptJob(on, "x", { null }, false, 0).problem)
    }

    @Test
    fun improveOnOwnServerSendsOnlyStandardFields() {
        val job = promptJob(own, "cat", { null }, false, imageCount = 2).job!!
        assertEquals("https://ai.example/v1/chat/completions", job.endpoint)
        assertNull(job.model)
        val body = JSONObject(PromptApi.requestBody(job.prompt, job.model, job.openAi, job.imageCount))
        assertFalse(body.has("model"))
        assertFalse(body.has("reasoning_effort"))
        assertFalse(body.has("max_completion_tokens"))
        assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("2 pictures"))
        assertEquals("llama", promptJob(own.copy(promptServerModel = " llama "), "cat", { null }, false, 0).job!!.model)
    }

    @Test
    fun readsTheImprovedPrompt() {
        assertEquals(PromptReply.Text("A ginger cat on a red skateboard, golden hour"),
            PromptApi.parse(200, """{"choices":[{"message":{"role":"assistant","content":"\"A ginger cat on a red skateboard,\n golden hour\""}}]}"""))
        assertEquals(PromptReply.Text("Two parts"),
            PromptApi.parse(200, """{"choices":[{"message":{"content":[{"type":"text","text":"Two "},{"type":"text","text":"parts"}]}}]}"""))
        assertEquals(PromptReply.Failed("The AI service sent no prompt"), PromptApi.parse(200, """{"choices":[{"message":{"content":"  "}}]}"""))
        assertEquals(PromptReply.Failed("The AI service sent no prompt"), PromptApi.parse(200, """{"choices":[{"message":{"content":null}}]}"""))
        assertEquals(PromptReply.Failed("The API key or token was refused", refused = true), PromptApi.parse(401, "{}"))
        assertEquals(PromptReply.Failed("Nothing found at that address"), PromptApi.parse(404, "Not found"))
        assertEquals(ImageApi.MAX_PROMPT_CHARS, PromptApi.clean("w".repeat(5000)).length)
        assertEquals("“Quoted” inside stays", PromptApi.clean("“Quoted” inside stays"))
    }

    @Test
    fun picturesShrinkToFitButNeverGrow() {
        assertEquals(1536 to 1152, InputImages.fit(4000, 3000))
        assertEquals(864 to 1536, InputImages.fit(1080, 1920))
        assertEquals(800 to 600, InputImages.fit(800, 600))
        assertEquals(1 to 1, InputImages.fit(0, 0))
    }

    /** An HTTP connection that records what is sent and answers [code] with [body]. */
    private class FakeConnection(private val code: Int, private val body: String) : HttpURLConnection(URL("https://fake.example")) {
        val headers = mutableMapOf<String, String>()
        val sent = ByteArrayOutputStream()
        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
        override fun getOutputStream(): OutputStream = sent
        override fun getResponseCode(): Int = code
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
    }
}
