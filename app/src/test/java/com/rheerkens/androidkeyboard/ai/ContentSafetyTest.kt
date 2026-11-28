package com.rheerkens.androidkeyboard.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The on-device list, the provider's safety-check answers, and reports. No network and no keys. */
class ContentSafetyTest {
    @Test
    fun allowsOrdinaryPrompts() {
        listOf(
            "a child playing in the park",
            "a 5 year old birthday cake",
            "minor details of a sunset",
            "a 21 year old portrait",
            "kill the lights",
            "a deepfake of a cat",
            "the county of essex at dusk",
        ).forEach { text ->
            assertNull(text, LocalBlocklist.check(text, strict = true, SafetyStage.INPUT))
            assertNull(text, LocalBlocklist.check(text, strict = false, SafetyStage.OUTPUT))
        }
    }

    @Test
    fun alwaysBlocksChildSexualContent() {
        listOf("a nude child", "csam", "16 year old in lingerie", "ch1ld p0rn", "underage sex", "lolita", "child\u200Bporn").forEach { text ->
            val blocked = LocalBlocklist.check(text, strict = false, SafetyStage.INPUT)
            assertTrue(text, blocked != null)
            assertTrue(text, blocked!!.message.contains("sexual content involving a child"))
        }
    }

    @Test
    fun alwaysBlocksNonConsensualPhrasesWithTheirOwnMessage() {
        listOf("revenge porn", "deepfake nude").forEach { text ->
            val blocked = LocalBlocklist.check(text, strict = false, SafetyStage.OUTPUT)
            assertTrue(text, blocked != null)
            assertTrue(text, blocked!!.message.contains("without consent"))
            assertFalse(text, blocked.message.contains("child"))
        }
    }

    @Test
    fun strictListIsExtraAndStaysOffWhenRelaxed() {
        assertNull(LocalBlocklist.check("nsfw portrait", strict = false, SafetyStage.INPUT))
        val blocked = LocalBlocklist.check("nsfw portrait", strict = true, SafetyStage.INPUT)
        assertTrue(blocked!!.message.contains("doesn't allow"))
    }

    @Test
    fun playBuildKeepsStrictChecksOn() {
        assertTrue(ContentSafetyPolicy.strict(setting = false, playStoreBuild = true))
        assertFalse(ContentSafetyPolicy.allowsRelaxing(playStoreBuild = true))
        assertFalse(ContentSafetyPolicy.strict(setting = false, playStoreBuild = false))
        assertTrue(ContentSafetyPolicy.allowsRelaxing(playStoreBuild = false))
    }

    @Test
    fun moderationParsesFlagsAndFailures() {
        val flagged = ModerationApi.parse(200, """{"results":[{"flagged":true,"categories":{"sexual/minors":true,"sexual":false}}]}""")
        assertEquals(ModerationReply.Result(true, setOf("sexual/minors")), flagged)
        assertEquals(
            SafetyDecision.Blocked(ContentSafety.childMessage(SafetyStage.OUTPUT)),
            ContentSafety.fromModeration(flagged, strict = false, SafetyStage.OUTPUT),
        )
        val adult = ModerationApi.parse(200, """{"results":[{"flagged":true,"categories":{"sexual":true}}]}""")
        assertEquals(SafetyDecision.Allow, ContentSafety.fromModeration(adult, strict = false, SafetyStage.OUTPUT))
        assertTrue(ContentSafety.fromModeration(adult, strict = true, SafetyStage.OUTPUT) is SafetyDecision.Blocked)
        val clear = ModerationApi.parse(200, """{"results":[{"flagged":false,"categories":{}}]}""")
        assertEquals(SafetyDecision.Allow, ContentSafety.fromModeration(clear, strict = true, SafetyStage.INPUT))
        val empty = ModerationApi.parse(200, """{"results":[{"flagged":true,"categories":{}}]}""")
        val held = ContentSafety.fromModeration(empty, strict = false, SafetyStage.OUTPUT) as SafetyDecision.Blocked
        assertTrue(held.failedClosed)
        assertEquals(ModerationReply.Failed("The API key or token was refused", refused = true), ModerationApi.parse(401, "{}"))
        assertTrue(ModerationApi.parse(200, "not json") is ModerationReply.Failed)
        assertTrue(ModerationApi.parse(200, """{"results":[]}""") is ModerationReply.Failed)
    }

    @Test
    fun gateUsesOpenAiModerationAndFailsClosed() {
        val calls = mutableListOf<String>()
        val gate = ContentGate { endpoint, _, _ ->
            calls += endpoint
            ModerationReply.Result(false, emptySet())
        }
        val openAi = "https://api.openai.com/v1/images/generations"
        assertEquals(SafetyDecision.Allow, gate.checkText("a red bicycle", openAi, "placeholder", true, SafetyStage.INPUT))
        assertEquals(listOf("https://api.openai.com/v1/moderations"), calls)
        assertEquals(
            "https://api.openai.com/v1/moderations",
            ContentSafety.moderationsEndpoint("https://api.openai.com/v1/chat/completions"),
        )
        assertEquals(
            "https://api.openai.com/v1/moderations",
            ContentSafety.moderationsEndpoint("https://api.openai.com/v1/images/edits"),
        )
        calls.clear()
        val own = "https://example.com/v1/images/generations"
        assertEquals(SafetyDecision.Allow, gate.checkText("a red bicycle", own, null, true, SafetyStage.INPUT))
        assertTrue(calls.isEmpty())
        val down = ContentGate { _, _, _ -> ModerationReply.Failed("Can't reach the safety check") }
        val held = down.checkText("a red bicycle", openAi, "placeholder", true, SafetyStage.OUTPUT) as SafetyDecision.Blocked
        assertTrue(held.failedClosed)
        assertTrue(held.message.startsWith("Not shown"))
        val refused = ContentGate { _, _, _ -> ModerationReply.Failed("The API key or token was refused", refused = true) }
        assertTrue((refused.checkText("a red bicycle", openAi, "placeholder", false, SafetyStage.INPUT) as SafetyDecision.Blocked).refused)
    }

    @Test
    fun gateBlocksFlaggedImagesAndSkipsACheckItCannotRun() {
        var called = false
        val gate = ContentGate { _, _, _ ->
            called = true
            ModerationReply.Result(true, setOf("sexual/minors"))
        }
        val openAi = "https://api.openai.com/v1/images/generations"
        val minor = gate.checkImage(byteArrayOf(1, 2, 3), "image/png", null, openAi, "placeholder", strict = false)
        assertTrue(called)
        assertTrue((minor as SafetyDecision.Blocked).message.contains("child"))
        called = false
        val local = gate.checkImage(byteArrayOf(1), "image/png", "csam", openAi, "placeholder", strict = false)
        assertFalse(called)
        assertTrue(local is SafetyDecision.Blocked)
        val huge = ByteArray(ModerationApi.MAX_IMAGE_BYTES + 1)
        val tooBig = gate.checkImage(huge, "image/png", "a cat", openAi, "placeholder", strict = true) as SafetyDecision.Blocked
        assertTrue(tooBig.failedClosed)
        assertFalse(called)
        val own = gate.checkImage(byteArrayOf(1, 2, 3), "image/png", "a cat", "https://example.com/v1/images/generations", null, true)
        assertEquals(SafetyDecision.Allow, own)
        assertFalse(called)
    }

    @Test
    fun providerRefusalIsACleanMessage() {
        assertEquals(
            PromptReply.Failed(ContentSafety.PROVIDER_REFUSAL, safety = true),
            PromptApi.parse(200, """{"choices":[{"finish_reason":"content_filter","message":{"content":"here is a prompt"}}]}"""),
        )
        assertEquals(
            PromptReply.Failed(ContentSafety.PROVIDER_REFUSAL, safety = true),
            PromptApi.parse(400, """{"error":{"code":"content_policy_violation","message":"no"}}"""),
        )
    }

    @Test
    fun reportsLeaveContentOutUnlessTheUserIncludesIt() {
        val text = "lavender bicycle at dusk"
        val without = ContentReports.prepare(
            ReportSubmission(ReportKind.IMPROVED_PROMPT, ReportReason.OTHER, "note", false, text, null, null),
            id = "one", nowMs = 1_700_000_000_000,
        )
        val mail = ContentReports.email(without.report, "")
        assertEquals(ContentReports.PLACEHOLDER_EMAIL, mail.address)
        assertFalse(mail.body.contains(text))
        assertFalse(ContentReports.encode(listOf(without.report)).contains(text))
        val with = ContentReports.prepare(
            ReportSubmission(ReportKind.IMPROVED_PROMPT, ReportReason.HATE, "  note  ", true, text, null, null),
            id = "two", nowMs = 1_700_000_000_000,
        )
        assertTrue(ContentReports.email(with.report, ContentReports.PLACEHOLDER_EMAIL).body.contains(text))
        val png = ByteArray(8) { 1 }
        val image = ContentReports.prepare(
            ReportSubmission(ReportKind.IMAGE, ReportReason.VIOLENCE, "", true, text, png, "image/png"),
            id = "three", nowMs = 5,
        )
        assertEquals("three.img", image.report.imageFile)
        val stored = ContentReports.encode(listOf(image.report))
        assertTrue(stored.contains(text))
        assertFalse(stored.contains("base64"))
        val huge = ContentReports.prepare(
            ReportSubmission(ReportKind.IMAGE, ReportReason.SEXUAL, "", true, null, ByteArray(ContentReports.MAX_ATTACHED_IMAGE_BYTES + 1), "image/png"),
            id = "four", nowMs = 5,
        )
        assertTrue(huge.report.imageOmitted)
        assertNull(huge.report.imageFile)
        assertTrue(ContentReports.email(huge.report, ContentReports.PLACEHOLDER_EMAIL).body.contains("too large"))
    }

    @Test
    fun cacheImageReadStaysInsideTheCache() {
        val cache = File.createTempFile("lowkey-cache", "").apply { delete(); mkdirs() }
        try {
            val image = File(cache, "picture.png").apply { writeBytes(byteArrayOf(9, 9)) }
            assertTrue(ContentReports.readCacheImage(cache, image.path)!!.contentEquals(byteArrayOf(9, 9)))
            val outside = File.createTempFile("lowkey-outside", ".png").apply { writeBytes(byteArrayOf(1)) }
            assertNull(ContentReports.readCacheImage(cache, outside.path))
            assertNull(ContentReports.readCacheImage(cache, null))
            outside.delete()
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun storeKeepsOnlyTheNewestReportsAndTheirImages() {
        val dir = File.createTempFile("lowkey-reports", "").apply { delete(); mkdirs() }
        try {
            val store = ContentReportStore(dir)
            repeat(ContentReports.MAX_REPORTS + 1) { index ->
                store.add(
                    ReportSubmission(ReportKind.IMAGE, ReportReason.OTHER, "", true, "prompt $index", byteArrayOf(index.toByte()), "image/png"),
                    nowMs = index.toLong(),
                    id = "id-$index",
                )
            }
            assertEquals(ContentReports.MAX_REPORTS, store.reports.value.size)
            assertFalse(store.reports.value.any { it.id == "id-0" })
            assertFalse(File(dir, "id-0.img").exists())
            assertTrue(File(dir, "id-${ContentReports.MAX_REPORTS}.img").isFile)
            val kept = store.find("id-1")!!
            store.updateNote(kept.id, "added later")
            assertEquals("added later", store.find(kept.id)!!.note)
            store.delete(kept.id)
            assertNull(store.find(kept.id))
            assertFalse(File(dir, "${kept.id}.img").exists())
            val reloaded = ContentReportStore(dir)
            assertEquals(store.reports.value.map { it.id }, reloaded.reports.value.map { it.id })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun galleryNameMatchesTheSavedCopy() {
        assertEquals("lowkey-42.jpg", createdImageDisplayName("image/jpeg", 42))
        assertEquals("lowkey-7.png", createdImageDisplayName("image/png", 7))
    }
}
