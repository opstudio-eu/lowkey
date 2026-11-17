package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.keyboard.Layouts
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How much typing the strip saves on a fixed text, with and without learning from your typing
 * ([WordContextModel]). The text (`src/test/resources/prediction/`) is one invented person's short
 * messages, in order: learning reads the first [TRAINING_SHARE] of them, as earlier typing would,
 * then the rest are typed word by word, the model still learning as they go.
 *
 * For each word: if the strip predicts it before a letter is typed, one tap; else letters are
 * typed until the strip (as the keyboard arranges it, autocorrection in the middle) offers it, and
 * one tap; else all letters and Space. Punctuation counts one key in every case.
 *
 * Opt-in (about a minute): `LOWKEY_BENCH=1 ./gradlew testDebugUnitTest --tests '*PredictionBenchmark'
 * --rerun`; writes `build/reports/prediction-benchmark.md` and fails below the thresholds.
 */
class PredictionBenchmark {
    private data class Result(val words: Int, val predictable: Int, val predicted: Int, val keys: Int, val letterKeys: Int)

    private val word = Regex("[\\p{L}'’]+")

    private fun run(id: String, learning: Boolean): Result {
        val messages = File("src/test/resources/prediction/${id.substringBefore('_')}.txt").readText()
            .split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val training = (messages.size * TRAINING_SHARE).toInt()
        val keyboard = TestLanguages.keyboard(id)
        val lexicon = TestLanguages.lexicon(id)
        val suggester = Suggester({ lexicon }, Layouts.keyGrid(keyboard))
        val model = if (learning) WordContextModel() else null
        fun learnAll(text: String) {
            for (end in word.findAll(text).map { it.range.last + 1 }) {
                val after = text.getOrNull(end) ?: ' '
                PersonalPredictions.justTyped(text.substring(0, end) + after)?.let { (previous, w) -> model?.learn(previous, w) }
            }
        }
        if (model != null) messages.take(training).forEach(::learnAll)
        var words = 0; var predictable = 0; var predicted = 0; var keys = 0; var letterKeys = 0
        for (message in messages.drop(training)) {
            var pos = 0
            for (match in word.findAll(message)) {
                keys += match.range.first - pos // spaces and punctuation before the word, one key each
                val before = message.substring(0, match.range.first)
                val target = match.value
                words++
                letterKeys += target.length + 1
                val atStart = before.isBlank() || before.trimEnd().last() in ".!?"
                val previous = PersonalPredictions.sentenceWords(before)
                val predictions = PersonalPredictions.nextWords(before, emptyList(), model, capitalize = atStart)
                if (previous.isNotEmpty()) predictable++
                var cost = target.length + 1
                if (predictions.any { it.text.equals(target, ignoreCase = true) }) {
                    predicted++
                    cost = 1
                } else {
                    for (k in 1 until target.length) {
                        val typed = target.take(k)
                        val fix = suggester.autocorrection(typed, emptyList(), atSentenceStart = atStart)
                        val ranked = PersonalPredictions.rank(typed, suggester.suggest(typed, emptyList()), before, model)
                        val strip = Suggester.arrangeForStrip(typed, ranked, fix?.let { Suggestion(it) })
                        if (strip.any { it?.text.equals(target, ignoreCase = true) }) { cost = k + 1; break }
                    }
                }
                keys += cost
                // The word and the space or punctuation after it are now in the field.
                pos = match.range.last + 1
                if (model != null) PersonalPredictions.justTyped(message.substring(0, pos) + (message.getOrNull(pos) ?: ' '))
                    ?.let { (p, w) -> model.learn(p, w) }
                if (message.getOrNull(pos) == ' ') pos++ // the tap or Space already typed it
            }
            keys += message.length - pos
        }
        return Result(words, predictable, predicted, keys, letterKeys)
    }

    @Test
    fun savings() {
        assumeTrue("Opt-in: set LOWKEY_BENCH=1", System.getenv("LOWKEY_BENCH") == "1")
        val report = StringBuilder("# Prediction benchmark\n\nThe last ${100 - (TRAINING_SHARE * 100).toInt()}% of each text, after learning from the rest.\n\n")
        report.append("| Language | Learning | Words | Next word predicted | Keys | Saved vs typing every letter |\n| --- | --- | ---: | ---: | ---: | ---: |\n")
        val failures = ArrayList<String>()
        fun f(x: Double) = String.format(Locale.ROOT, "%.1f", x)
        for ((id, minSaved) in THRESHOLDS) {
            for (learning in listOf(false, true)) {
                val r = run(id, learning)
                val saved = 100.0 * (r.letterKeys - r.keys) / r.letterKeys
                report.append("| $id | ${if (learning) "on" else "off"} | ${r.words} | ${r.predicted} of ${r.predictable} " +
                    "(${f(100.0 * r.predicted / r.predictable)}%) | ${r.keys} | ${f(saved)}% |\n")
                if (learning && saved < minSaved) failures += "$id: saved ${f(saved)}% < $minSaved%"
            }
        }
        File("build/reports").mkdirs()
        File("build/reports/prediction-benchmark.md").writeText(report.toString())
        println(report)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val TRAINING_SHARE = 0.6
        /** 55.2% and 57.3% saved with learning when added. */
        val THRESHOLDS = listOf("en_US" to 52.0, "nl" to 54.0)
    }
}
