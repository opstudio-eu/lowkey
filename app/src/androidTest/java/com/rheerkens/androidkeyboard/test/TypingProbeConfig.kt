package com.rheerkens.androidkeyboard.test

import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.settings.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Make performance comparisons independent of the preview's saved preferences. */
object TypingProbeConfig {
    @JvmStatic fun configure(): Settings {
        val before = AppGraph.settings.current
        val defaults = Settings()
        AppGraph.settings.update {
            it.copy(autocorrect = true, suggestionStrip = true, autoCapitalize = true,
                enabledKeyboards = defaults.enabledKeyboards, currentKeyboard = defaults.currentKeyboard)
        }
        return before
    }

    @JvmStatic fun restore(before: Settings) { AppGraph.settings.update { before } }

    /** English (US) first and Dutch also on, for mixed-language typing. Dutch must be downloaded. */
    @JvmStatic fun configureEnglishAndDutch(): Settings {
        val before = AppGraph.settings.current
        check(AppGraph.languagePacks.isInstalled(AppGraph.languages["nl"]!!)) { "Download Dutch on the device first" }
        AppGraph.settings.update {
            it.copy(autocorrect = true, suggestionStrip = true, autoCapitalize = true,
                enabledKeyboards = setOf("en_US", "nl"), currentKeyboard = "en_US")
        }
        return before
    }

    /** Waits until [id]'s full word list is loaded. */
    @JvmStatic fun awaitLexicon(id: String) {
        val language = AppGraph.languages[id]!!
        val deadline = android.os.SystemClock.uptimeMillis() + 60_000
        while (AppGraph.lexicon(language).size < 100_000) {
            check(android.os.SystemClock.uptimeMillis() < deadline) { "Full $id dictionary not ready" }
            android.os.SystemClock.sleep(100)
        }
    }

    /** Typing defaults with learning on, starting from nothing learned. */
    @JvmStatic fun configureLearning(): Settings {
        val before = configure()
        AppGraph.settings.update { it.copy(learnFromTyping = true) }
        AppGraph.personalModel.forget()
        AppGraph.personalModel.model()
        return before
    }

    /** Forgets what the learning probe taught, so the device keeps nothing from it. */
    @JvmStatic fun forgetLearning() { AppGraph.personalModel.forget() }

    /**
     * Exports through the app's own backup code to a file, deletes a saved word and flips a
     * setting, imports the file again, and reports whether both came back. Cleans up after.
     */
    @JvmStatic fun backupRoundTrip(context: android.content.Context): String {
        val file = java.io.File(context.cacheDir, "probe-backup.json")
        val uri = android.net.Uri.fromFile(file)
        val word = "Zorblax"
        AppGraph.dictionary.addTerm(word)
        val numberRow = AppGraph.settings.current.numberRow
        val saved = kotlinx.coroutines.runBlocking { AppGraph.exportBackup(context, uri) }
        AppGraph.dictionary.state.value.terms.first { it.text == word }.let { AppGraph.dictionary.deleteTerm(it.id) }
        AppGraph.settings.update { it.copy(numberRow = !numberRow) }
        val imported = kotlinx.coroutines.runBlocking { AppGraph.importBackup(context, uri) }
        val wordBack = AppGraph.dictionary.contains(word)
        val settingBack = AppGraph.settings.current.numberRow == numberRow
        val again = kotlinx.coroutines.runBlocking { AppGraph.importBackup(context, uri) }
        AppGraph.dictionary.state.value.terms.firstOrNull { it.text == word }?.let { AppGraph.dictionary.deleteTerm(it.id) }
        file.delete()
        val report = "export: $saved | import: $imported | word back: $wordBack, setting back: $settingBack | again: $again"
        check(wordBack && settingBack && again.startsWith("Added 0 words and 0 speech rules")) { report }
        return report
    }

    @JvmStatic fun awaitDictionary() {
        val language = AppGraph.currentLanguage(AppGraph.settings.current)
        val deadline = android.os.SystemClock.uptimeMillis() + 60_000
        while (AppGraph.lexicon(language).size < 100_000) {
            check(android.os.SystemClock.uptimeMillis() < deadline) { "Full test dictionary not ready" }
            android.os.SystemClock.sleep(100)
        }
    }

    /** Hold every Default CPU worker so the IME cannot warm its correction cache. */
    @JvmStatic fun blockSuggestions(): AutoCloseable {
        val count = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val ready = CountDownLatch(count)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(Dispatchers.Default)
        repeat(count) { scope.launch { ready.countDown(); release.await() } }
        if (!ready.await(10, TimeUnit.SECONDS)) {
            release.countDown()
            scope.cancel()
            error("Could not reserve suggestion workers")
        }
        return AutoCloseable { release.countDown(); scope.cancel() }
    }
}
