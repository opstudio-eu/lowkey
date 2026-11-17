package com.rheerkens.androidkeyboard.dictionary

import com.rheerkens.androidkeyboard.keyboard.Layouts
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import java.io.File
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Autocorrect and suggestion quality over a broad, reproducible sample, per language and language
 * mix, measured the way the app suggests: [Suggester] over the full word lists with the drawn key
 * geometry, and the strip arranged as [Suggester.arrangeForStrip] does.
 *
 * - **Typos:** 1,000 words drawn from each language's 10,000 most frequent lowercase words of at
 *   least three letters, each with exactly one slip made on the drawn keys: 55% a neighbouring key,
 *   15% a letter left out, 15% an extra neighbouring letter, 15% two letters swapped. A slip that
 *   is itself a known word counts as a real-word slip, which only context could fix.
 * - **Correct words:** 1,000 frequent words typed correctly must be left alone.
 * - **Second language:** 1,000 frequent words of another language typed on a keyboard, with and
 *   without that language enabled.
 * - **Accents:** up to 500 accented words typed without their accents.
 *
 * Opt-in, as it takes a few minutes: `LOWKEY_BENCH=1 ./gradlew testDebugUnitTest --tests
 * '*QualityBenchmark' --rerun`. Writes `build/reports/quality-benchmark.md` and fails when a result
 * falls past its threshold, so an engine change shows whether it helps. The sample is fixed by
 * seed: the same code gives the same numbers and checksum on every machine; only the times vary.
 */
class QualityBenchmark {
    private val lexicons = HashMap<String, Lexicon>()
    private fun lexicon(id: String) = lexicons.getOrPut(id) { TestLanguages.lexicon(id) }

    private fun suggester(keyboard: String, others: List<String>): Suggester {
        val typing = TestLanguages.keyboard(keyboard)
        val primary = lexicon(typing.language.id)
        val secondary = others.map(::lexicon)
        return Suggester({ primary }, Layouts.keyGrid(typing), { secondary })
    }

    /** [id]'s words, most frequent first, as its word list stores them. */
    private fun ranked(id: String): List<String> {
        val entries = ArrayList<Pair<String, Int>>()
        GZIPInputStream(TestLanguages.dictFile(id).inputStream()).bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line == "#whitelist") break
                val tab = line.indexOf('\t')
                if (tab > 0) entries += line.substring(0, tab) to (line.substring(tab + 1).toIntOrNull() ?: 0)
            }
        }
        return entries.sortedByDescending { it.second }.map { it.first }.distinct()
    }

    private fun frequent(id: String, pool: Int = POOL) =
        ranked(id).filter { w -> w.length >= 3 && w == w.lowercase() && w.all { it.isLetter() } }.take(pool)

    private fun sample(words: List<String>, n: Int, seed: Long) = words.shuffled(Random(seed)).take(n)

    /** One slip per word on [keyboard]'s drawn keys; deterministic for a given seed. */
    private class Slips(keyboard: TypingKeyboard, seed: Long) {
        private val random = Random(seed)
        private val centres: Map<Char, Pair<Double, Double>> = Layouts.letterCentres(keyboard)
            .flatMapIndexed { row, keys -> keys.map { (c, x) -> c to (x to row.toDouble()) } }.toMap()
        private val neighbours = centres.mapValues { (c, at) ->
            centres.filter { (k, p) -> k != c && hypot(p.first - at.first, p.second - at.second) < 1.5 }.keys.toList()
        }

        fun of(word: String): String {
            repeat(50) {
                val roll = random.nextDouble()
                val slip = when {
                    roll < 0.55 -> substitute(word)
                    roll < 0.70 -> random.nextInt(word.length).let { i -> word.removeRange(i, i + 1) }
                    roll < 0.85 -> insert(word)
                    else -> swap(word)
                }
                if (slip != null && slip != word) return slip
            }
            error("No slip for $word")
        }

        private fun substitute(word: String): String? {
            val i = random.nextInt(word.length)
            val near = neighbours[word[i]].orEmpty().ifEmpty { return null }
            return word.substring(0, i) + near[random.nextInt(near.size)] + word.substring(i + 1)
        }

        private fun insert(word: String): String? {
            val i = random.nextInt(word.length)
            val near = neighbours[word[i]].orEmpty().ifEmpty { return null }
            val extra = near[random.nextInt(near.size)]
            return if (random.nextBoolean()) word.substring(0, i) + extra + word.substring(i) else word.substring(0, i + 1) + extra + word.substring(i + 1)
        }

        private fun swap(word: String): String? {
            val i = random.nextInt(word.length - 1)
            if (word[i] == word[i + 1]) return null
            return word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2)
        }
    }

    private data class TypoResult(
        val name: String, val fixed: Int, val wrong: Int, val kept: Int, val inStrip: Int, val realWord: Int,
        val total: Int, val medianMs: Double, val p95Ms: Double,
    ) {
        fun pct(n: Int) = 100.0 * n / total
    }

    private val checksum = CRC32()
    private fun record(vararg parts: Any?) = checksum.update(parts.joinToString("|", postfix = "\n").toByteArray())

    private fun typos(keyboard: String, others: List<String> = emptyList(), size: Int = SAMPLE): TypoResult {
        val typing = TestLanguages.keyboard(keyboard)
        val id = typing.language.id
        val suggester = suggester(keyboard, others)
        val known = (listOf(id) + others).map(::lexicon)
        val slips = Slips(typing, SEED)
        // A smaller sample is the start of the larger one, with the same slips.
        val words = sample(frequent(id), size, SEED)
        // Warm the JIT and the suggester's caches on other words first.
        sample(frequent(id), 100, SEED + 1).forEach { suggester.autocorrection(slips.of(it), emptyList()) }
        var fixed = 0; var wrong = 0; var kept = 0; var inStrip = 0; var realWord = 0
        val times = DoubleArray(words.size)
        for ((n, target) in words.withIndex()) {
            val typed = slips.of(target)
            val start = System.nanoTime()
            val fix = suggester.autocorrection(typed, emptyList())
            val ranked = suggester.suggest(typed, emptyList())
            times[n] = (System.nanoTime() - start) / 1e6
            val strip = Suggester.arrangeForStrip(typed, ranked, fix?.let { Suggestion(it) })
            when (fix) {
                null -> kept++
                target -> fixed++
                else -> wrong++
            }
            if (strip.any { it?.text.equals(target, ignoreCase = true) }) inStrip++
            if (known.any { it.contains(typed) }) realWord++
            record(keyboard, others, target, typed, fix, strip.map { it?.text })
        }
        times.sort()
        val name = (listOf(id) + others).joinToString(" + ") { NAMES[it] ?: it }
        return TypoResult(name, fixed, wrong, kept, inStrip, realWord, words.size,
            times[times.size / 2], times[(times.size * 95) / 100])
    }

    /**
     * Runs with every unit test run: the first [QUICK_SAMPLE] words of each language's typo row
     * and of its correct words, against the same kind of thresholds, in a few seconds.
     */
    @Test
    fun quickQuality() {
        val failures = ArrayList<String>()
        for ((id, minFixed) in QUICK_MIN_FIXED) {
            val r = typos(id, size = QUICK_SAMPLE)
            println("quick $id: fixed ${r.fixed} wrong ${r.wrong} kept ${r.kept} of $QUICK_SAMPLE")
            if (r.pct(r.fixed) < minFixed) failures += "$id: fixed ${r.pct(r.fixed)}% < $minFixed% of $QUICK_SAMPLE slips"
            val (changed, examples) = changed(sample(frequent(id), QUICK_SAMPLE, SEED + 2), id, emptyList())
            if (changed > 0) failures += "$id: $changed correct words changed: $examples"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Slips of [ContextCase.words]-language words on a keyboard, each after [CONTEXT_LENGTH]
     * frequent words of [ContextCase.context] (or none), with the language written in leading as
     * the app picks it ([Suggester.contextLanguage]). Also returns how often it picked the
     * context's language.
     */
    private fun typosInContext(case: ContextCase): Pair<TypoResult, Int> {
        val typing = TestLanguages.keyboard(case.keyboard)
        val enabled = listOf(typing.language.id) + case.others
        val base = suggester(case.keyboard, case.others)
        val known = enabled.map(::lexicon)
        val slips = Slips(typing, SEED + 5)
        val words = sample(frequent(case.words), SAMPLE, SEED + 5)
        val contextWords = case.context?.let { frequent(it, CONTEXT_POOL) }
        val random = Random(SEED + 6)
        val expectedLead = case.context?.let(enabled::indexOf) ?: 0
        var fixed = 0; var wrong = 0; var kept = 0; var inStrip = 0; var realWord = 0; var leadRight = 0
        for (target in words) {
            val typed = slips.of(target)
            val before = contextWords?.let { list ->
                List(CONTEXT_LENGTH) { list[random.nextInt(list.size)] }.joinToString(" ", postfix = " ")
            }.orEmpty()
            val lead = base.contextLanguage(before + typed)
            if (lead == expectedLead) leadRight++
            val suggester = base.leading(lead)
            val fix = suggester.autocorrection(typed, emptyList())
            val strip = Suggester.arrangeForStrip(typed, suggester.suggest(typed, emptyList()), fix?.let { Suggestion(it) })
            when (fix) {
                null -> kept++
                target -> fixed++
                else -> wrong++
            }
            if (strip.any { it?.text.equals(target, ignoreCase = true) }) inStrip++
            if (known.any { it.contains(typed) }) realWord++
            record(case, before, target, typed, lead, fix, strip.map { it?.text })
        }
        return TypoResult(case.toString(), fixed, wrong, kept, inStrip, realWord, words.size, 0.0, 0.0) to leadRight
    }

    /** How many of [words] (correct in some language) autocorrect changes on [keyboard] with [others]. */
    private fun changed(words: List<String>, keyboard: String, others: List<String>): Pair<Int, List<String>> {
        val suggester = suggester(keyboard, others)
        val changes = words.mapNotNull { w -> suggester.autocorrection(w, emptyList())?.let { "$w → $it" } }
        record(keyboard, others, changes)
        return changes.size to changes.take(4)
    }

    private data class AccentResult(val language: String, val restored: Int, val wrong: Int, val kept: Int, val bareIsWord: Int, val total: Int)

    private fun accents(id: String): AccentResult {
        val lexicon = lexicon(id)
        val accented = ranked(id).take(Lexicon.CORRECTION_POOL)
            .filter { w -> w.length >= 3 && w == w.lowercase() && w.all { it.isLetter() } && lexicon.folding.fold(w) != w }
        val words = sample(accented, ACCENT_SAMPLE, SEED)
        val suggester = suggester(id, emptyList())
        var restored = 0; var wrong = 0; var kept = 0; var bareIsWord = 0
        for (word in words) {
            val bare = lexicon.folding.fold(word)
            val fix = suggester.autocorrection(bare, emptyList())
            when (fix) {
                null -> kept++
                word -> restored++
                else -> wrong++
            }
            if (lexicon.contains(bare)) bareIsWord++
            record(id, word, bare, fix)
        }
        return AccentResult(id, restored, wrong, kept, bareIsWord, words.size)
    }

    @Test
    fun quality() {
        assumeTrue("Opt-in: set LOWKEY_BENCH=1", System.getenv("LOWKEY_BENCH") == "1")
        val started = System.nanoTime()
        val report = StringBuilder()
        fun line(s: String = "") { report.append(s).append('\n') }
        val failures = ArrayList<String>()
        fun f(x: Double) = String.format(Locale.ROOT, "%.1f", x)

        line("# Autocorrect quality benchmark")
        line()
        line("$SAMPLE words per row, one slip each, seed $SEED. Percentages are of all words.")
        line()
        line("| Keyboard and languages | Fixed | Wrong word | Left alone | Target in strip | Slip is a word | Median ms | p95 ms |")
        line("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        for (mix in TYPO_MIXES) {
            val r = typos(mix.keyboard, mix.others)
            line("| ${r.name} | ${f(r.pct(r.fixed))}% | ${f(r.pct(r.wrong))}% | ${f(r.pct(r.kept))}% | ${f(r.pct(r.inStrip))}% | ${r.realWord} | ${f(r.medianMs)} | ${f(r.p95Ms)} |")
            if (r.pct(r.fixed) < mix.minFixed) failures += "${r.name}: fixed ${f(r.pct(r.fixed))}% < ${mix.minFixed}%"
            if (r.pct(r.wrong) > mix.maxWrong) failures += "${r.name}: wrong ${f(r.pct(r.wrong))}% > ${mix.maxWrong}%"
            if (r.pct(r.inStrip) < mix.minStrip) failures += "${r.name}: in strip ${f(r.pct(r.inStrip))}% < ${mix.minStrip}%"
        }
        line()
        line("## Correct words left alone")
        line()
        line("| Keyboard | Correct words changed (of $SAMPLE) | Examples |")
        line("| --- | ---: | --- |")
        for (id in LANGUAGES) {
            val (count, examples) = changed(sample(frequent(id), SAMPLE, SEED + 2), id, emptyList())
            line("| $id | $count | ${examples.joinToString(", ")} |")
            if (count > MAX_CORRECT_CHANGED) failures += "$id: $count correct words changed > $MAX_CORRECT_CHANGED"
        }
        line()
        line("## A second language")
        line()
        line("| Words typed | Keyboard | Enabled | Changed (of $SAMPLE) | Examples |")
        line("| --- | --- | --- | ---: | --- |")
        for (case in SECOND_LANGUAGE) {
            val words = sample(frequent(case.words), SAMPLE, SEED + 3)
            val (count, examples) = changed(words, case.keyboard, case.others)
            line("| ${case.words} | ${case.keyboard} | ${(listOf(case.keyboard) + case.others).joinToString(" + ")} | $count | ${examples.joinToString(", ")} |")
            if (count > case.maxChanged) failures += "${case.words} on ${case.keyboard} ${case.others}: $count changed > ${case.maxChanged}"
        }
        line()
        line("## Accents typed without accents")
        line()
        line("| Language | Restored | Wrong | Left alone | Bare form is a word | Words |")
        line("| --- | ---: | ---: | ---: | ---: | ---: |")
        for ((id, minRestored) in ACCENT_LANGUAGES) {
            val r = accents(id)
            line("| ${r.language} | ${r.restored} | ${r.wrong} | ${r.kept} | ${r.bareIsWord} | ${r.total} |")
            val pct = 100.0 * r.restored / r.total
            if (pct < minRestored) failures += "$id: accents restored ${f(pct)}% < $minRestored%"
        }
        line()
        line("Checksum of the results above: ${String.format(Locale.ROOT, "%08x", checksum.value)}.")
        line()
        line("## The language written in")
        line()
        line("Each slip follows $CONTEXT_LENGTH frequent words of the context language, as in a sentence.")
        line()
        line("| Slips in | Keyboard | Enabled | Context | Context language leads | Fixed | Wrong word | Target in strip |")
        line("| --- | --- | --- | --- | ---: | ---: | ---: | ---: |")
        for (case in CONTEXT_CASES) {
            val (r, leadRight) = typosInContext(case)
            val enabled = (listOf(case.keyboard) + case.others).joinToString(" + ") { NAMES[it] ?: it }
            line("| ${NAMES[case.words]} | ${NAMES[case.keyboard]} | $enabled | ${case.context?.let { NAMES[it] } ?: "none"} | " +
                "${if (case.context == null) "–" else "${f(100.0 * leadRight / r.total)}%"} | ${f(r.pct(r.fixed))}% | ${f(r.pct(r.wrong))}% | ${f(r.pct(r.inStrip))}% |")
            if (r.pct(r.fixed) < case.minFixed) failures += "$case: fixed ${f(r.pct(r.fixed))}% < ${case.minFixed}%"
            if (r.pct(r.inStrip) < case.minStrip) failures += "$case: in strip ${f(r.pct(r.inStrip))}% < ${case.minStrip}%"
        }
        line()
        line("Checksum of every result: ${String.format(Locale.ROOT, "%08x", checksum.value)}. Run time ${(System.nanoTime() - started) / 1_000_000_000} s.")
        File("build/reports").mkdirs()
        File("build/reports/quality-benchmark.md").writeText(report.toString())
        println(report)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** A typo row and its thresholds, set a little below the results when the benchmark was added. */
    private data class TypoMix(val keyboard: String, val others: List<String>, val minFixed: Double, val maxWrong: Double, val minStrip: Double)

    private data class SecondLanguage(val words: String, val keyboard: String, val others: List<String>, val maxChanged: Int)

    /** Slips of [words] typed after a few words of [context] (null: none); thresholds as in [TypoMix]. */
    private data class ContextCase(val words: String, val keyboard: String, val others: List<String>, val context: String?,
        val minFixed: Double = 0.0, val minStrip: Double = 0.0)

    private companion object {
        const val SEED = 20_261_116L
        const val SAMPLE = 1_000
        const val ACCENT_SAMPLE = 500
        const val POOL = 10_000
        const val QUICK_SAMPLE = 200
        /** 87.5%, 90% and 88.5% when added: a few words' slack. */
        val QUICK_MIN_FIXED = listOf("en_US" to 85.0, "nl" to 87.5, "pl" to 86.0)
        val LANGUAGES = listOf("en_US", "nl", "pl")
        val NAMES = mapOf("en_US" to "English", "nl" to "Dutch", "pl" to "Polish")
        // About 1.5 points of slack on the results when the benchmark was added (in comments).
        val TYPO_MIXES = listOf(
            TypoMix("en_US", emptyList(), 83.5, 8.5, 90.0), // 85.3, 7.2, 91.8
            TypoMix("nl", emptyList(), 85.5, 7.0, 91.5), // 87.4, 5.7, 93.1
            TypoMix("pl", emptyList(), 85.5, 6.5, 92.5), // 87.4, 4.9, 94.1
            TypoMix("en_US", listOf("nl"), 82.0, 9.0, 86.5), // 83.9, 7.4, 88.0
            TypoMix("en_US", listOf("nl", "pl"), 81.5, 9.0, 86.0), // 83.2, 7.4, 87.7
            TypoMix("nl", listOf("en_US"), 84.5, 7.5, 88.5), // 86.3, 6.1, 90.3
        )
        const val MAX_CORRECT_CHANGED = 5 // 0
        val SECOND_LANGUAGE = listOf(
            SecondLanguage("nl", "en_US", emptyList(), 420), // 391
            SecondLanguage("nl", "en_US", listOf("nl"), 5), // 0
            SecondLanguage("en_US", "nl", emptyList(), 500), // 476
            SecondLanguage("en_US", "nl", listOf("en_US"), 5), // 1
        )
        val ACCENT_LANGUAGES = listOf("nl" to 94.0, "pl" to 85.0) // 96.2, 87.4
        const val CONTEXT_LENGTH = 5
        const val CONTEXT_POOL = 2_000
        // With context, the language written in does as well as on its own keyboard; without, it
        // trails the keyboard's language. Switching languages mid-sentence costs the first word.
        val CONTEXT_CASES = listOf(
            ContextCase("nl", "en_US", listOf("nl"), null, 79.0, 88.5), // 80.9, 90.0
            ContextCase("nl", "en_US", listOf("nl"), "nl", 83.5, 89.5), // 85.1, 90.9
            ContextCase("nl", "nl", listOf("en_US"), null, 83.5, 89.5), // 85.1, 90.9
            ContextCase("en_US", "nl", listOf("en_US"), null, 78.0, 87.0), // 79.7, 88.7
            ContextCase("en_US", "nl", listOf("en_US"), "en_US", 83.5, 89.0), // 85.3, 90.4
            ContextCase("en_US", "en_US", listOf("nl"), null, 83.5, 89.0), // 85.3, 90.4
            ContextCase("nl", "nl", listOf("en_US"), "en_US"),
        )
    }
}
