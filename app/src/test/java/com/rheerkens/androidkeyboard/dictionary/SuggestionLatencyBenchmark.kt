package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.keyboard.Layouts
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import kotlin.random.Random

/**
 * The work behind one keystroke, as the keyboard's strip refresh does it: a fresh [Suggester],
 * [Suggester.autocorrection], then [Suggester.suggest], on one thread after JIT warm-up. Inputs are
 * prefixes of frequent words and words with one simulated touch typo, in English, Dutch and Polish.
 *
 * Skipped unless LOWKEY_BENCH=1, because it takes minutes:
 *
 *     LOWKEY_BENCH=1 ./gradlew testDebugUnitTest --tests '*SuggestionLatencyBenchmark' --rerun
 *
 * Prints, and writes to `build/reports/benchmarks/suggestion-latency.txt`, per scenario the
 * median, 95th percentile and slowest time per keystroke, the medians of its two parts, plus a
 * checksum of every result: equal checksums before and after a change mean the suggestions and
 * corrections did not change.
 */
class SuggestionLatencyBenchmark {
    private val report = StringBuilder()

    private fun log(line: String = "") {
        println(line)
        report.appendLine(line)
    }

    /** The list's words, most frequent first. */
    private fun words(id: String): List<String> =
        GZIPInputStream(TestLanguages.dictFile(id).inputStream()).bufferedReader().useLines { lines ->
            lines.takeWhile { it != "#whitelist" }.mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else line.substring(0, tab) to (line.substring(tab + 1).toIntOrNull() ?: 0)
            }.toList()
        }.sortedByDescending { it.second }.map { it.first }

    /** [n] lowercase words of 3–14 letters, drawn from the 10,000 most frequent. */
    private fun sample(words: List<String>, n: Int, seed: Int): List<String> =
        words.filter { w -> w.length in 3..14 && w.all { it.isLetter() } && w == w.lowercase() }
            .distinct().take(10_000).shuffled(Random(seed)).take(n)

    /** One touch typo per word: a neighbouring key, a dropped or extra letter, or two letters swapped. */
    private class TypoModel(keyboard: TypingKeyboard, private val rnd: Random) {
        private val grid = KeyGrid(Layouts.letterCentres(keyboard))
        private val letters = Layouts.letterCentres(keyboard).flatten().map { it.first }.toSet()
        private val neighbours = letters.associateWith { a -> letters.filter { b -> b != a && grid.adjacent(a, b) } }

        fun typo(w: String): String? {
            repeat(30) {
                val r = rnd.nextDouble()
                val i = rnd.nextInt(w.length)
                val near = neighbours[if (w[i] in letters) w[i] else AccentFolding.baseLetter(w[i])].orEmpty()
                val t = when {
                    r < 0.55 -> near.randomOrNull(rnd)?.let { w.substring(0, i) + it + w.substring(i + 1) }
                    r < 0.70 -> if (w.length > 3) w.removeRange(i, i + 1) else null
                    r < 0.85 -> near.randomOrNull(rnd)?.let { w.substring(0, i + 1) + it + w.substring(i + 1) }
                    else -> if (i < w.length - 1 && w[i] != w[i + 1]) w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2) else null
                }
                if (t != null && t != w) return t
            }
            return null
        }
    }

    private fun percentile(sorted: List<Double>, p: Double) = sorted[((sorted.size - 1) * p).toInt()]

    private fun scenario(label: String, inputs: List<String>, suggester: () -> Suggester) {
        val fixTimes = ArrayList<Double>(inputs.size)
        val suggestTimes = ArrayList<Double>(inputs.size)
        val results = StringBuilder()
        fun keystroke(input: String, measure: Boolean) {
            val start = System.nanoTime()
            val s = suggester()
            val fix = s.autocorrection(input, emptyList(), atSentenceStart = false)
            val fixed = System.nanoTime()
            val ranked = s.suggest(input, emptyList())
            val end = System.nanoTime()
            if (!measure) return
            fixTimes += (fixed - start) / 1e6
            suggestTimes += (end - fixed) / 1e6
            results.append(input).append('>').append(fix).append(':').append(ranked.joinToString(",") { it.text }).append('\n')
        }
        inputs.take(300).forEach { keystroke(it, measure = false) }
        inputs.forEach { keystroke(it, measure = true) }
        val total = fixTimes.indices.map { fixTimes[it] + suggestTimes[it] }.sorted()
        val checksum = CRC32().apply { update(results.toString().toByteArray()) }.value
        log("%-22s %6d %8.2f %8.2f %8.1f %10.2f %9.2f   %08x".format(label, inputs.size, percentile(total, 0.5),
            percentile(total, 0.95), total.last(), percentile(fixTimes.sorted(), 0.5),
            percentile(suggestTimes.sorted(), 0.5), checksum))
    }

    @Test
    fun keystrokeLatency() {
        assumeTrue("Set LOWKEY_BENCH=1 to run the benchmark", System.getenv("LOWKEY_BENCH") == "1")
        val ids = listOf("en_US", "nl", "pl")
        val lexicons = ids.associateWith { TestLanguages.lexicon(it) }
        val keyboards = ids.associateWith { TypingKeyboard(TestLanguages.language(it)) }
        val words = ids.associateWith { words(it) }
        fun suggester(primary: String, vararg others: String) = {
            Suggester({ lexicons.getValue(primary) }, Layouts.keyGrid(keyboards.getValue(primary)),
                { others.map { lexicons.getValue(it) } })
        }
        fun prefixes(list: List<String>) = list.flatMap { w -> (1 until w.length).map { w.take(it) } }
        fun typos(id: String, list: List<String>) = TypoModel(keyboards.getValue(id), Random(7)).let { m -> list.mapNotNull(m::typo) }

        val enPrefixes = prefixes(sample(words.getValue("en_US"), 250, seed = 51))
        val nlPrefixes = prefixes(sample(words.getValue("nl"), 250, seed = 52))
        val enTypos = typos("en_US", sample(words.getValue("en_US"), 600, seed = 11))
        val nlTypos = typos("nl", sample(words.getValue("nl"), 600, seed = 12))
        val longTypos = typos("en_US", sample(words.getValue("en_US"), 3000, seed = 61).filter { it.length >= 10 }.take(150))

        log("Per keystroke: autocorrection + suggest (JVM ${System.getProperty("java.version")}, " +
            "${Runtime.getRuntime().availableProcessors()} cores)")
        log("%-22s %6s %8s %8s %8s %10s %9s   %s".format("Scenario", "inputs", "p50 ms", "p95 ms", "max ms",
            "fix p50", "sugg p50", "checksum"))
        scenario("en prefixes", enPrefixes, suggester("en_US"))
        scenario("en typos", enTypos, suggester("en_US"))
        scenario("en+nl prefixes", enPrefixes, suggester("en_US", "nl"))
        scenario("en+nl typos", enTypos, suggester("en_US", "nl"))
        scenario("nl+en typos", nlTypos, suggester("nl", "en_US"))
        scenario("nl+en prefixes", nlPrefixes, suggester("nl", "en_US"))
        scenario("en+nl+pl typos", enTypos, suggester("en_US", "nl", "pl"))
        scenario("en+nl+pl long typos", longTypos, suggester("en_US", "nl", "pl"))
        File("build/reports/benchmarks").mkdirs()
        File("build/reports/benchmarks/suggestion-latency.txt").writeText(report.toString())
    }
}
