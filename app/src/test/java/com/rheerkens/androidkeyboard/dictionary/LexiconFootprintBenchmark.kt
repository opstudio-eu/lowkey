package com.rheerkens.androidkeyboard.dictionary

import java.io.File
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Heap kept by each language's full word list (as the app loads it, extra words included) and the
 * time to build it from the gzipped text. Opt-in: `LOWKEY_BENCH=1 ./gradlew testDebugUnitTest
 * --tests '*LexiconFootprintBenchmark' --rerun`; writes `build/reports/lexicon-footprint.md`.
 * Heap is measured after full garbage collections, as the median of [RUNS] builds; on a phone
 * (ART) the numbers differ somewhat, but the comparison between versions holds.
 */
class LexiconFootprintBenchmark {
    private fun used(): Long {
        repeat(4) { System.gc(); Thread.sleep(80) }
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    @Test
    fun footprint() {
        assumeTrue("Opt-in: set LOWKEY_BENCH=1", System.getenv("LOWKEY_BENCH") == "1")
        val report = StringBuilder("# Word list footprint\n\n" +
            "| Language | Words | Heap kept | Build from text | Load saved copy | Saved copy |\n| --- | ---: | ---: | ---: | ---: | ---: |\n")
        for (id in listOf("en_US", "nl", "pl")) {
            TestLanguages.lexicon(id) // warm the JIT and class loading
            val heaps = ArrayList<Long>()
            val times = ArrayList<Long>()
            var size = 0
            repeat(RUNS) {
                val base = used()
                val start = System.nanoTime()
                val lexicon = TestLanguages.lexicon(id)
                times += (System.nanoTime() - start) / 1_000_000
                heaps += used() - base
                size = lexicon.size
                check(lexicon.contains("the")) // keep it reachable until measured
            }
            heaps.sort(); times.sort()
            // Saved as the app saves it after the first build, then loaded as after a restart.
            val language = TestLanguages.language(id)
            val saved = File.createTempFile("lexicon", ".lkx")
            saved.outputStream().use { TestLanguages.lexicon(id).write(it, "bench") }
            val loads = List(RUNS) {
                val start = System.nanoTime()
                saved.inputStream().use { Lexicon.read(it, "bench", language.folding, language.rules)!! }
                (System.nanoTime() - start) / 1_000_000
            }.sorted()
            report.append(String.format(Locale.ROOT, "| %s | %,d | %.1f MB | %d ms | %d ms | %.1f MB |\n",
                id, size, heaps[RUNS / 2] / 1_048_576.0, times[RUNS / 2], loads[RUNS / 2], saved.length() / 1_048_576.0))
            saved.delete()
        }
        File("build/reports").mkdirs()
        File("build/reports/lexicon-footprint.md").writeText(report.toString())
        println(report)
    }

    private companion object {
        const val RUNS = 5
    }
}
