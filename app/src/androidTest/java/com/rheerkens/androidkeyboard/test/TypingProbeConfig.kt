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
