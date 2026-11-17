package com.rheerkens.androidkeyboard.dictionary

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordContextModelTest {
    private fun WordContextModel.type(text: String) {
        for (end in Regex("""[\p{L}'’]+""").findAll(text).map { it.range.last + 1 }) {
            PersonalPredictions.justTyped(text.substring(0, end) + (text.getOrNull(end) ?: ' '))?.let { (p, w) -> learn(p, w) }
        }
    }

    @Test fun learnsWhatFollowsOneAndTwoWords() {
        val model = WordContextModel()
        model.type("I will send it later. I will call you. I will send the report.")
        assertEquals(listOf("send", "call"), model.next(listOf("I", "will")))
        assertEquals(listOf("it", "the"), model.next(listOf("will", "send")))
        // Two matching words outweigh one: after "you will" only one word matches.
        model.type("You will see.")
        assertEquals("see", model.next(listOf("you", "will")).first())
        // "see" followed only "will", so it ranks below "send", which followed "I will" too.
        assertEquals(listOf("send", "see"), model.next(listOf("I", "will"), prefix = "s"))
        assertEquals(emptyList<String>(), model.next(emptyList()))
    }

    @Test fun aSentenceStartIsNotLearnedAndNamesKeepTheirSpelling() {
        val model = WordContextModel()
        model.type("Thanks for the update. Lowkey works. Ask Lowkey later.")
        assertNull(PersonalPredictions.justTyped("Thanks"))
        assertEquals(listOf("Lowkey"), model.next(listOf("ask")))
        // A sentence boundary breaks the context.
        assertEquals(emptyList<String>(), model.next(listOf("update")))
    }

    @Test fun unlearnTakesBackAnUndoneCorrectionAndRejectionsStay() {
        val model = WordContextModel()
        model.learn(listOf("see", "you"), "tomorrow")
        model.unlearn(listOf("see", "you"), "tomorrow")
        assertEquals(0, model.size)
        model.reject("Lowkey")
        assertTrue(model.isRejected("lowkey"))
        assertFalse(model.isRejected("lowkeys"))
    }

    @Test fun pastTheLimitOldCountsFadeAndFrequentOnesStay() {
        val model = WordContextModel(maxEntries = 100)
        repeat(5) { model.learn(listOf("good"), "morning") }
        for (i in 0 until 200) model.learn(listOf("word$i".filter { it.isLetter() } + ('a' + i % 26)), "next" + ('a' + i % 26))
        assertTrue(model.size <= 100)
        assertEquals(listOf("morning"), model.next(listOf("good")))
    }

    @Test fun savesAndLoadsEverything() {
        val model = WordContextModel()
        model.type("I will send it later. I will call Mira.")
        model.reject("lowkey")
        val bytes = ByteArrayOutputStream().also(model::write).toByteArray()
        val loaded = WordContextModel.read(ByteArrayInputStream(bytes))
        assertEquals(model.size, loaded.size)
        assertEquals(model.next(listOf("I", "will")), loaded.next(listOf("I", "will")))
        assertEquals(listOf("Mira"), loaded.next(listOf("will", "call")))
        assertTrue(loaded.isRejected("Lowkey"))
        assertTrue(WordContextModel.read(ByteArrayInputStream("other".toByteArray())).isEmpty)
    }

    @Test fun predictionsJoinTheFieldsOwnAndCompletionsGoFirst() {
        val model = WordContextModel()
        model.type("See you tomorrow. See you later. See you tomorrow.")
        assertEquals(listOf("tomorrow", "later"), PersonalPredictions.nextWords("Ok, see you ", emptyList(), model, capitalize = false).map { it.text })
        assertEquals(emptyList<String>(), PersonalPredictions.nextWords("Ok, see you ", emptyList(), null, capitalize = false))
        val ranked = listOf(Suggestion("today"), Suggestion("toward"))
        assertEquals(listOf("tomorrow", "today", "toward"), PersonalPredictions.rank("to", ranked, "See you to", model).map { it.text })
        assertEquals(ranked, PersonalPredictions.rank("to", ranked, "See you to", null))
    }
}
