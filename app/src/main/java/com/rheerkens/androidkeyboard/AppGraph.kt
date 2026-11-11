package com.rheerkens.androidkeyboard

import android.Manifest
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.rheerkens.androidkeyboard.diagnostics.CrashLog
import com.rheerkens.androidkeyboard.diagnostics.SessionLog
import com.rheerkens.androidkeyboard.dictionary.BuiltInWords
import com.rheerkens.androidkeyboard.dictionary.Corrector
import com.rheerkens.androidkeyboard.dictionary.DictionaryStore
import com.rheerkens.androidkeyboard.dictionary.KeyboardLanguage
import com.rheerkens.androidkeyboard.dictionary.LanguageCatalog
import com.rheerkens.androidkeyboard.dictionary.LanguagePackStore
import com.rheerkens.androidkeyboard.dictionary.Lexicon
import com.rheerkens.androidkeyboard.dictionary.Suggester
import com.rheerkens.androidkeyboard.keyboard.Layouts
import com.rheerkens.androidkeyboard.models.DiskModelStore
import com.rheerkens.androidkeyboard.models.ModelCatalog
import com.rheerkens.androidkeyboard.models.ModelStore
import com.rheerkens.androidkeyboard.models.resolveLanguage
import com.rheerkens.androidkeyboard.models.speechLanguageCode
import com.rheerkens.androidkeyboard.settings.AudioInput
import com.rheerkens.androidkeyboard.settings.Settings
import com.rheerkens.androidkeyboard.settings.SettingsStore
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import com.rheerkens.androidkeyboard.speech.ClipAudioSource
import com.rheerkens.androidkeyboard.speech.LoadableModel
import com.rheerkens.androidkeyboard.speech.LocalSpeechEngine
import com.rheerkens.androidkeyboard.speech.MicAudioSource
import com.rheerkens.androidkeyboard.updates.UpdateManager
import com.rheerkens.androidkeyboard.updates.Updates
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
    lateinit var updates: UpdateManager
        private set
    /** Keyboard languages from `assets/dict/languages.txt`. */
    lateinit var languages: LanguageCatalog
        private set
    /** Downloaded word lists of the languages that are not bundled. */
    lateinit var languagePacks: LanguagePackStore
        private set

    /** Bundled WAV clips under `assets/test-clips/`; only debug builds ship any. */
    var testClips: List<String> = emptyList()
        private set

    val appScope: CoroutineScope = MainScope()

    /**
     * AOSP word lists by language id, loaded in the background on first use. Only the most
     * recently used [MAX_LOADED_LEXICONS] stay in memory; each holds about 200,000 words.
     */
    private val lexicons = object : LinkedHashMap<String, Lexicon>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lexicon>?) = size > MAX_LOADED_LEXICONS
    }
    private val loading = ConcurrentHashMap.newKeySet<String>()
    /**
     * Bumped when a language's word list is deleted or replaced, so a load that started before
     * never publishes the old list. Guarded by [lexicons].
     */
    private val generations = HashMap<String, Int>()
    /** Small built-in lists used while a language's full list loads. */
    private val fallbackLexicons = ConcurrentHashMap<String, Lexicon>()
    private lateinit var assets: AssetManager
    /** One per keyboard id: suggestions in its language, with slips judged on its letter rows. */
    private val suggesters = ConcurrentHashMap<String, Suggester>()
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
        updates = Updates.create(app)
        languages = LanguageCatalog.parse(app.assets.open(LanguageCatalog.ASSET).use { String(it.readBytes(), Charsets.UTF_8) })
        languagePacks = LanguagePackStore(
            dir = java.io.File(app.filesDir, "dictionaries"),
            catalog = languages,
            scope = appScope,
            onInstalled = { language ->
                invalidateLexicon(language.id)
                // A downloaded language is ready to use at once, on its own layout.
                settings.update { it.copy(enabledKeyboards = it.enabledKeyboards + TypingKeyboard(language).id) }
            },
        )
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
                    LoadableModel(info, dir, info.resolveLanguage(speechLanguage(settings.current))?.code)
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
        appScope.launch {
            settings.settings.map { it.checkForUpdates }.distinctUntilChanged().collect { Updates.schedule(app, it) }
        }
        // An enabled language whose word list is missing downloads again: Dutch for installs from
        // before 0.5.4, when it was built in, or a list whose pinned checksum changed.
        settings.current.enabledKeyboards.mapNotNull { languages[it.substringBefore(':')] }.distinct()
            .filter { !languagePacks.isInstalled(it) }
            .forEach { languagePacks.download(it.id) }
        preloadLexicon(currentLanguage(settings.current))
        initialized = true
    }

    /** Every keyboard whose language's word list is on the phone, in globe order. */
    fun installedKeyboards(): List<TypingKeyboard> =
        TypingKeyboard.all(languages).filter { languagePacks.isInstalled(it.language) }

    /** Enabled keyboards whose word list is on the phone, in globe order; never empty. */
    fun usableKeyboards(s: Settings = settings.current): List<TypingKeyboard> =
        TypingKeyboard.ordered(
            s.enabledKeyboards.mapNotNull { TypingKeyboard.parse(it, languages) }.filter { languagePacks.isInstalled(it.language) }
        ).ifEmpty { listOf(TypingKeyboard(languages.default)) }

    /** The keyboard the keys show: the chosen one if usable, else the first usable one. */
    fun currentKeyboard(s: Settings = settings.current): TypingKeyboard {
        val usable = usableKeyboards(s)
        val chosen = TypingKeyboard.parse(s.currentKeyboard, languages)
        return usable.firstOrNull { it.id == chosen?.id } ?: usable.first()
    }

    /** The current keyboard's language. */
    fun currentLanguage(s: Settings = settings.current): KeyboardLanguage = currentKeyboard(s).language

    /** Suggestions in [keyboard]'s language, with slips judged on its letter rows. */
    fun suggester(keyboard: TypingKeyboard): Suggester =
        suggesters.getOrPut(keyboard.id) { Suggester({ lexicon(keyboard.language) }, Layouts.keyGrid(keyboard)) }

    /** The speech language code to resolve against a model: [Settings.speechLanguage], or the keyboard's. */
    fun speechLanguage(s: Settings = settings.current): String = speechLanguageCode(s.speechLanguage, currentLanguage(s))

    /**
     * Removes a downloaded language: its file, its loaded word list, and its keyboards in the
     * settings. If no usable keyboard is left on, English (US) is; the current keyboard moves to
     * the first usable one.
     */
    fun deleteLanguage(id: String) {
        languagePacks.delete(id)
        invalidateLexicon(id)
        settings.update(::withoutUnusableKeyboards)
    }

    /**
     * Stops a download, and turns its language off, so a language enabled before (Dutch from an
     * older version) isn't downloaded again at the next start once the user said no.
     */
    fun cancelDownload(id: String) {
        languagePacks.cancel(id)
        settings.update { withoutUnusableKeyboards(it, dropped = id) }
    }

    /** [s] without the keyboards whose word list isn't on the phone or downloading, nor [dropped]'s. */
    private fun withoutUnusableKeyboards(s: Settings, dropped: String? = null): Settings {
        val downloading = languagePacks.state.value.downloads.keys
        val kept = s.enabledKeyboards.filterTo(HashSet()) { id ->
            val language = languages[id.substringBefore(':')]
            language != null && language.id != dropped &&
                (languagePacks.isInstalled(language) || language.id in downloading)
        }
        val next = s.copy(enabledKeyboards = kept)
        val usable = usableKeyboards(next).map { it.id }
        val enabled = if (usable.any { it in kept }) kept else kept + Settings.DEFAULT_KEYBOARD
        return next.copy(enabledKeyboards = enabled, currentKeyboard = currentKeyboard(next.copy(enabledKeyboards = enabled)).id)
    }

    /** The language's full word list, or a small built-in list while it loads. */
    fun lexicon(language: KeyboardLanguage): Lexicon {
        synchronized(lexicons) { lexicons[language.id] }?.let { return it }
        preloadLexicon(language)
        return fallbackLexicons.getOrPut(language.id) {
            val words = when (language.id) {
                "en_US", "en_GB" -> BuiltInWords.ENGLISH + BuiltInWords.TECH
                "nl" -> BuiltInWords.DUTCH
                else -> emptyList()
            }
            Lexicon.fromWords(words, folding = language.folding)
        }
    }

    /** Drops a loaded word list and makes any load in progress discard its result. */
    private fun invalidateLexicon(id: String) = synchronized(lexicons) {
        lexicons.remove(id)
        generations[id] = (generations[id] ?: 0) + 1
    }

    /** Counts published word lists, so cached results computed on a fallback list can be redone. */
    val lexiconLoads = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun preloadLexicon(language: KeyboardLanguage) {
        // A downloaded list that isn't (or no longer) on the phone has nothing to load.
        if (!languagePacks.isInstalled(language)) return
        if (synchronized(lexicons) { lexicons.containsKey(language.id) } || !loading.add(language.id)) return
        val generation = synchronized(lexicons) { generations[language.id] ?: 0 }
        appScope.launch(Dispatchers.IO) {
            val name = language.dictionaryFile
            val started = android.os.SystemClock.elapsedRealtime()
            runCatching {
                val input = if (language.bundled) assets.open("dict/$name") else languagePacks.file(language).inputStream()
                val base = input.use { Lexicon.parseGzip(it, language.folding, language.rules) }
                // Tech/chat words (and, outside English, the English basics people mix in) count as known.
                val extra = if (language.id.startsWith("en_")) BuiltInWords.TECH else BuiltInWords.TECH + BuiltInWords.ENGLISH
                base.withExtraWords(BuiltInWords.ENGLISH + extra, EXTRA_WORD_FREQUENCY).also {
                    // Initialize the spelling engine's cold path on this loader thread before
                    // making the full dictionary available to a Space press on the main thread.
                    Corrector.corrections("keyboard", it)
                }
            }.onSuccess {
                val current = synchronized(lexicons) {
                    ((generations[language.id] ?: 0) == generation).also { fresh -> if (fresh) lexicons[language.id] = it }
                }
                if (current) lexiconLoads.value++
                val ms = android.os.SystemClock.elapsedRealtime() - started
                Log.i(TAG, if (current) "Loaded $name: ${it.size} words in $ms ms" else "Discarded $name: deleted or replaced while loading")
            }.onFailure { Log.e(TAG, "Failed to load $name", it) }
            loading.remove(language.id)
        }
    }

    /** Key of the model the next capture would load (see `LoadableModel.key`), or null. */
    fun activeModelKey(): String? = models.state.value.activeModel?.let { info ->
        LoadableModel(info, java.io.File(""), info.resolveLanguage(speechLanguage(settings.current))?.code).key
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
    /** Word lists kept in memory: the current language plus the two used before it. */
    private const val MAX_LOADED_LEXICONS = 3
}
