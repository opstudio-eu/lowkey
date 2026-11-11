package com.rheerkens.androidkeyboard.companion.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The OpStudio logo's waves and dot must stay exactly as in the official SVG: both drawables keep
 * its four paths unchanged and differ only in their brand colour.
 */
class LogoDrawableTest {
    private fun drawable(name: String): String {
        val candidates = listOf(File("src/main/res/drawable/$name.xml"), File("app/src/main/res/drawable/$name.xml"))
        return candidates.first { it.isFile }.readText()
    }

    private fun paths(xml: String) = Regex("""android:pathData="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
    private fun fills(xml: String) = Regex("""android:fillColor="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()

    private fun sha256(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun pathsMatchTheOfficialLogo() {
        for (name in listOf("opstudio_logo_blue", "opstudio_logo_light_blue")) {
            val p = paths(drawable(name))
            assertEquals("$name: three waves and the dot", 4, p.size)
            // SHA-256 of the official SVG's top, middle, bottom and dot path data, joined by newlines.
            assertEquals(name, OFFICIAL_PATHS_SHA256, sha256(p.joinToString("\n")))
        }
    }

    @Test
    fun variantsUseTheBrandColours() {
        assertEquals(setOf("#FF17659C"), fills(drawable("opstudio_logo_blue")))
        assertEquals(setOf("#FF83C9F4"), fills(drawable("opstudio_logo_light_blue")))
    }

    private companion object {
        const val OFFICIAL_PATHS_SHA256 = "4aca186166a3c338883054ec8e083a7e3eb571de0cde7f908d76afad320adebf"
    }
}
