package com.rheerkens.androidkeyboard.privacy

import com.rheerkens.androidkeyboard.ai.AiDisclosure
import com.rheerkens.androidkeyboard.ai.AiSendKind
import com.rheerkens.androidkeyboard.ai.AiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The prominent disclosures say what Play's User Data policy asks for, and each can be declined. */
class DisclosuresTest {
    private val all = listOf(Disclosures.MICROPHONE, Disclosures.PHOTOS)

    @Test fun everyDisclosureCanBeDeclined() {
        all.forEach { d ->
            assertTrue(d.title, d.decline in setOf("Not now", "Cancel"))
            assertEquals(d.title, "Continue", d.accept)
            assertTrue(d.title, d.title.isNotBlank() && d.body.isNotBlank())
        }
    }

    @Test fun microphoneSaysOnDeviceNotSavedNotUploaded() {
        val body = Disclosures.MICROPHONE.body
        assertTrue(body.contains("on this phone"))
        assertTrue(body.contains("not saved"))
        assertTrue(body.contains("not uploaded"))
        assertTrue(body.contains("only while you dictate"))
    }

    @Test fun photosExplainScreenshotsAndUploadOnlyWhenAttached() {
        val body = Disclosures.PHOTOS.body
        assertTrue(body.contains("screenshots"))
        assertTrue(body.contains("uploaded only if you attach it to an AI prompt"))
        assertTrue(body.contains("stay on this phone"))
    }

    @Test fun firstAiSendSaysItIsExperimentalAndCanBeCancelled() {
        val notice = AiDisclosure.notice(AiSendKind.CREATE, 0, AiService.OPENAI, "api.openai.com")
        assertTrue(notice.lines.any { it.contains("experimental") })
        val prompt = File("src/main/java/com/rheerkens/androidkeyboard/ai/AiPromptActivity.kt").readText()
        // Both sends stop at the notice until Continue.
        assertEquals(2, Regex("""if \(!sendNoticeAccepted\(\)\)""").findAll(prompt).count())
        assertTrue(prompt.contains("onCancel = { pendingSend = null }"))
    }

    @Test fun playPrivacyTextDoesNotClaimGitHubUpdates() {
        val play = Disclosures.aboutPrivacy(selfUpdate = false)
        assertFalse(play.contains("GitHub"))
        assertFalse(play.contains("check for a newer version"))
        assertTrue(play.contains("Google Play"))
        val github = Disclosures.aboutPrivacy(selfUpdate = true)
        assertTrue(github.contains("check GitHub for a newer version"))
        assertFalse(github.contains("Google Play"))
        listOf(play, github).forEach {
            assertTrue(it.contains("experimental"))
            assertTrue(it.contains("not saved or uploaded"))
        }
    }

    @Test fun permissionRequestsComeAfterTheirDisclosure() {
        val screens = File("src/main/java/com/rheerkens/androidkeyboard/companion/screens")
        val models = File(screens, "ModelsScreen.kt").readText()
        // The only microphone request runs from the disclosure's Continue.
        assertEquals(1, Regex("""permission\.launch\(Manifest\.permission\.RECORD_AUDIO\)""").findAll(models).count())
        val micAccept = models.indexOf("Disclosures.MICROPHONE")
        assertTrue(micAccept >= 0 && models.indexOf("permission.launch(Manifest.permission.RECORD_AUDIO)") > micAccept)
        val photos = File(screens, "PhotoAccessState.kt").readText()
        assertEquals(1, Regex("""launcher\.launch\(RecentScreenshots\.REQUEST\)""").findAll(photos).count())
        val accept = photos.substringAfter("fun acceptDisclosure()").substringBefore("\n    }")
        assertTrue(accept.contains("launcher.launch(RecentScreenshots.REQUEST)"))
        // No other screen asks for these permissions directly.
        val others = File("src/main/java").walk().filter { it.extension == "kt" }
            .filterNot { it.name == "ModelsScreen.kt" || it.name == "PhotoAccessState.kt" }
            .filter { f ->
                f.readText().let {
                    "launch(Manifest.permission.RECORD_AUDIO" in it || "launch(RecentScreenshots.REQUEST" in it ||
                        "requestPermissions(" in it
                }
            }
            .map { it.name }.toList()
        assertTrue("Unannounced requests in $others", others.isEmpty())
    }
}
