package com.rheerkens.androidkeyboard

import android.Manifest
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.rheerkens.androidkeyboard.diagnostics.CrashLog
import com.rheerkens.androidkeyboard.diagnostics.SessionLog
import com.rheerkens.androidkeyboard.dictionary.Corrector
import com.rheerkens.androidkeyboard.dictionary.BuiltInWords
import com.rheerkens.androidkeyboard.dictionary.DictionaryStore
import com.rheerkens.androidkeyboard.dictionary.Lexicon
import com.rheerkens.androidkeyboard.dictionary.Suggester
import com.rheerkens.androidkeyboard.models.DiskModelStore
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.ModelStore
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.settings.AudioInput
import com.rheerkens.androidkeyboard.settings.SettingsStore
import com.rheerkens.androidkeyboard.settings.TypingLayout
import com.rheerkens.androidkeyboard.speech.ClipAudioSource
import com.rheerkens.androidkeyboard.speech.LoadableModel
import com.rheerkens.androidkeyboard.speech.LocalSpeechEngine
import com.rheerkens.androidkeyboard.speech.MicAudioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manual dependency graph. The companion app and the input method run in the same process,
 * so they share these stores and observe the same StateFlows.
 */
object AppGraph {
    lateinit var settings: SettingsStore
        private set
    lateinit var dictionary: DictionaryStore
        private set
    lateinit var models: ModelStore
        private set
    lateinit var sessionLog: SessionLog
        private set
    lateinit var speechEngine: LocalSpeechEngine
        private set
    lateinit var crashLog: CrashLog
        private set

    /** Bundled WAV clips under `assets/test-clips/`; only debug builds ship any. */
    var testClips: List<String> = emptyList()
        private set

    val appScope: CoroutineScope = MainScope()

    /** AOSP word lists per typing layout, loaded in the background on first use. */
    private val lexicons = ConcurrentHashMap<TypingLayout, Lexicon>()
    private val loading = ConcurrentHashMap.newKeySet<TypingLayout>()
    private val fallbackLexicons = mapOf(
        TypingLayout.EN_US to Lexicon.fromWords(BuiltInWords.ENGLISH + BuiltInWords.TECH),
        TypingLayout.NL_NL to Lexicon.fromWords(BuiltInWords.DUTCH),
    )
    private lateinit var assets: AssetManager
    private val suggesters = TypingLayout.entries.associateWith { layout -> Suggester { lexicon(layout) } }
    private val nextClip = AtomicInteger(0)
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        assets = app.assets
        settings = SettingsStore(app)
        dictionary = DictionaryStore(app)
        models = DiskModelStore(app, appScope)
        sessionLog = SessionLog(app)
        crashLog = CrashLog(app)
        testClips = app.assets.list(CLIP_DIR).orEmpty().filter { it.endsWith(".wav") }.sorted()
        val mic = MicAudioSource(app)
        val clips = ClipAudioSource(
            clips = testClips.map { "$CLIP_DIR/$it" },
            open = { app.assets.open(it) },
            nextIndex = { nextClip.getAndIncrement() },
        )
        speechEngine = LocalSpeechEngine(
            audioSource = {
                if (settings.current.audioInput == AudioInput.TEST_CLIPS && testClips.isNotEmpty()) clips else mic
            },
            activeModel = {
                val state = models.state.value
                val info = ModelCatalog.byId(state.activeId)
                val dir = state.activeId?.let(models::modelDir)
                if (info != null && dir != null) {
                    LoadableModel(info, dir, info.resolveLanguage(settings.current.speechLanguage)?.code)
                } else {
                    null
                }
            },
            keyterms = { dictionary.state.value.terms.map { it.text } },
            engineWork = crashLog::engineWork,
        )
        appScope.launch {
            settings.settings.map { it.modelRetention }.distinctUntilChanged().collect(speechEngine::setRetention)
        }
        appScope.launch {
            // Release the old model even if the IME service is not currently alive.
            combine(models.state, settings.settings) { _, _ -> activeModelKey() }
                .distinctUntilChanged().drop(1).collect { speechEngine.unload() }
        }
        preloadLexicon(settings.current.currentLayout)
        initialized = true
    }

    fun suggester(layout: TypingLayout): Suggester = suggesters.getValue(layout)

    /** The layout's full word list, or a small built-in list while it loads. */
    fun lexicon(layout: TypingLayout): Lexicon {
        lexicons[layout]?.let { return it }
        preloadLexicon(layout)
        return fallbackLexicons.getValue(layout)
    }

    fun preloadLexicon(layout: TypingLayout) {
        if (lexicons.containsKey(layout) || !loading.add(layout)) return
        appScope.launch(Dispatchers.IO) {
            val file = when (layout) {
                TypingLayout.EN_US -> "dict/en_US.dict"
                TypingLayout.NL_NL -> "dict/nl.dict"
            }
            runCatching {
                val base = assets.open(file).use(Lexicon::parseGzip)
                // Tech/chat words (and, for Dutch, the English basics people mix in) count as known.
                val extra = if (layout == TypingLayout.EN_US) BuiltInWords.TECH else BuiltInWords.TECH + BuiltInWords.ENGLISH
                base.withExtraWords(BuiltInWords.ENGLISH + extra, EXTRA_WORD_FREQUENCY).also {
                    // Initialize the spelling engine's cold path on this loader thread before
                    // making the full dictionary available to a Space press on the main thread.
                    Corrector.corrections("keyboard", it)
                }
            }.onSuccess {
                lexicons[layout] = it
                Log.i(TAG, "Loaded $file: ${it.size} words")
            }.onFailure { Log.e(TAG, "Failed to load $file", it) }
            loading.remove(layout)
        }
    }

    /** Key of the model the next capture would load (see `LoadableModel.key`), or null. */
    fun activeModelKey(): String? = models.state.value.activeModel?.let { info ->
        LoadableModel(info, java.io.File(""), info.resolveLanguage(settings.current.speechLanguage)?.code).key
    }

    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Test clips replace the microphone, so they don't need the permission. */
    fun usesTestClips(): Boolean = settings.current.audioInput == AudioInput.TEST_CLIPS && testClips.isNotEmpty()

    private const val CLIP_DIR = "test-clips"
    private const val TAG = "SlimKeyboard"
    /** Frequency given to built-in tech words: common enough to complete, not to dominate. */
    private const val EXTRA_WORD_FREQUENCY = 120
}
