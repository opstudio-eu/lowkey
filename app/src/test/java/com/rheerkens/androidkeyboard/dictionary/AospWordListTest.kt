package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/** The on-phone converter must match `scripts/build-dictionaries.py` (see `test_build_dictionaries.py`). */
class AospWordListTest {
    private val fixture = File("src/test/resources/aosp")

    @Test
    fun convertsTheSharedFixtureLikeThePythonScript() {
        val expected = File(fixture, "sample.dict.txt").readText()
        assertEquals(expected, AospWordList.convert(File(fixture, "sample.combined").readText()))
    }

    @Test
    fun convertsGzipInput() {
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(File(fixture, "sample.combined").readBytes()) }
        }.toByteArray()
        assertEquals(File(fixture, "sample.dict.txt").readText(), AospWordList.convertGzip(bytes))
    }

    @Test
    fun keepsOnlyTheMostFrequentWords() {
        val n = AospWordList.MAX_WORDS + 5
        val combined = buildString {
            append("dictionary=main:xx\n")
            for (i in 0 until n) append(" word=w$i,f=${i % 256},flags=\n")
        }
        val lines = AospWordList.convert(combined).lines()
        val words = lines.takeWhile { it != "#whitelist" }
        assertEquals(AospWordList.MAX_WORDS, words.size)
        assertTrue(words.first().endsWith("\t255"))
    }

    @Test
    fun convertedOutputLoadsAsALexicon() {
        val lexicon = Lexicon.parse(AospWordList.convert(File(fixture, "sample.combined").readText()).byteInputStream())
        assertEquals(150, lexicon.frequency("żółw"))
        assertEquals("I'm", lexicon.whitelist["im"])
        assertTrue(!lexicon.contains("badword"))
    }
}
