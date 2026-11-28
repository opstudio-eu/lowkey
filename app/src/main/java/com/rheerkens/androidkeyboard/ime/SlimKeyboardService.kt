package com.rheerkens.androidkeyboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Rect
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.clipboard.ClipEntry
import com.rheerkens.androidkeyboard.clipboard.ClipboardContent
import com.rheerkens.androidkeyboard.clipboard.ClipboardHistory
import com.rheerkens.androidkeyboard.clipboard.RecentScreenshots
import com.rheerkens.androidkeyboard.companion.theme.resolveDark
import com.rheerkens.androidkeyboard.clipboard.loadThumbnail
import com.rheerkens.androidkeyboard.editor.Attachment
import com.rheerkens.androidkeyboard.editor.ImageEditorActivity
import com.rheerkens.androidkeyboard.editor.PendingAttachment
import com.rheerkens.androidkeyboard.clipboard.ClipboardReader
import com.rheerkens.androidkeyboard.clipboard.asSensitive
import com.rheerkens.androidkeyboard.companion.CompanionLinks
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.dictionary.DictionaryStore
import com.rheerkens.androidkeyboard.dictionary.PersonalTerm
import com.rheerkens.androidkeyboard.dictionary.Suggester
import com.rheerkens.androidkeyboard.dictionary.Corrector
import com.rheerkens.androidkeyboard.dictionary.Suggestion
import com.rheerkens.androidkeyboard.dictionary.NextWordPredictor
import com.rheerkens.androidkeyboard.dictionary.PersonalPredictions
import com.rheerkens.androidkeyboard.dictionary.WordContextModel
import com.rheerkens.androidkeyboard.keyboard.ShiftState
import com.rheerkens.androidkeyboard.keyboard.BackspaceScrub
import com.rheerkens.androidkeyboard.keyboard.FanCommand
import com.rheerkens.androidkeyboard.keyboard.Key
import com.rheerkens.androidkeyboard.keyboard.KeyboardController
import com.rheerkens.androidkeyboard.keyboard.Layer
import com.rheerkens.androidkeyboard.keyboard.EmojiSuggestions
import com.rheerkens.androidkeyboard.keyboard.Layouts
import com.rheerkens.androidkeyboard.keyboard.ui.ClipChip
import com.rheerkens.androidkeyboard.keyboard.ui.EditAction
import com.rheerkens.androidkeyboard.keyboard.ui.EnterKind
import com.rheerkens.androidkeyboard.keyboard.ui.FanSpec
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardActions
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardRoot
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardUiState
import com.rheerkens.androidkeyboard.keyboard.ui.resolveKeyboardColors
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardBackgroundImage
import com.rheerkens.androidkeyboard.keyboard.ui.NavigationBarStyle
import com.rheerkens.androidkeyboard.keyboard.ui.keyboardNavigationBarStyle
import com.rheerkens.androidkeyboard.settings.LoadedBackground
import com.rheerkens.androidkeyboard.keyboard.ui.StripNote
import com.rheerkens.androidkeyboard.keyboard.ui.StripState
import com.rheerkens.androidkeyboard.keyboard.ui.WordAim
import com.rheerkens.androidkeyboard.keyboard.KeyboardGesture
import com.rheerkens.androidkeyboard.keyboard.wordsFrom
import com.rheerkens.androidkeyboard.settings.InputMode
import com.rheerkens.androidkeyboard.settings.Settings
import com.rheerkens.androidkeyboard.voice.CancelReason
import com.rheerkens.androidkeyboard.voice.VoiceConfig
import com.rheerkens.androidkeyboard.voice.VoiceNotes
import com.rheerkens.androidkeyboard.voice.VoiceOutcome
import com.rheerkens.androidkeyboard.voice.VoiceReadiness
import com.rheerkens.androidkeyboard.voice.VoiceSessionController
import com.rheerkens.androidkeyboard.voice.VoiceState
import com.rheerkens.androidkeyboard.voice.VoiceTextSink
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import com.rheerkens.androidkeyboard.keyboard.KeyboardPlacement
import com.rheerkens.androidkeyboard.keyboard.EmojiCatalog
import com.rheerkens.androidkeyboard.keyboard.EmojiAnnotations
import com.rheerkens.androidkeyboard.keyboard.ui.EmojiSearchState
import com.rheerkens.androidkeyboard.keyboard.EmojiHistory
import com.rheerkens.androidkeyboard.keyboard.PlacementProfile
import com.rheerkens.androidkeyboard.keyboard.PlacementMode
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.ai.ImageApi
import com.rheerkens.androidkeyboard.ai.AiClient
import com.rheerkens.androidkeyboard.ai.InputImages
import com.rheerkens.androidkeyboard.ai.PromptImage
import com.rheerkens.androidkeyboard.ai.ImageJob
import com.rheerkens.androidkeyboard.ai.ImageReply
import com.rheerkens.androidkeyboard.ai.imageJob
import com.rheerkens.androidkeyboard.ai.saveToGallery
import com.rheerkens.androidkeyboard.keyboard.ui.AiUi
import com.rheerkens.androidkeyboard.keyboard.ui.AiReady
import com.rheerkens.androidkeyboard.ai.AiAction
import com.rheerkens.androidkeyboard.ai.AiTask
import com.rheerkens.androidkeyboard.keyboard.ui.AiFailure
import com.rheerkens.androidkeyboard.ai.MAX_AI_TASKS
import com.rheerkens.androidkeyboard.ai.AiService
import com.rheerkens.androidkeyboard.ai.AiSetup
import com.rheerkens.androidkeyboard.ai.Secret
import com.rheerkens.androidkeyboard.ai.mayReplaceClip
import com.rheerkens.androidkeyboard.ai.AiComposerHost
import com.rheerkens.androidkeyboard.ai.AiComposerLink
import com.rheerkens.androidkeyboard.ai.AiPromptActivity
import com.rheerkens.androidkeyboard.ai.AiReportActivity
import com.rheerkens.androidkeyboard.ai.AiReportLink
import com.rheerkens.androidkeyboard.ai.ContentGate
import com.rheerkens.androidkeyboard.ai.ContentSafetyPolicy
import com.rheerkens.androidkeyboard.ai.GalleryDelete
import com.rheerkens.androidkeyboard.ai.LocalBlocklist
import com.rheerkens.androidkeyboard.ai.SafetyDecision
import com.rheerkens.androidkeyboard.ai.SafetyStage
import com.rheerkens.androidkeyboard.ai.deleteCreatedImage
import com.rheerkens.androidkeyboard.ai.aiSetupOf

/**
 * Slim Keyboard input method: keys mode with suggestions, and a one-row voice mode with an
 * in-keyboard model picker, backed by the (fake) speech engine. UI is Compose hosted in the
 * IME window.
 */
class SlimKeyboardService : InputMethodService(), KeyboardActions, AiComposerHost {
    private val owner = ImeLifecycleOwner()
    private val scope = MainScope()
    private val settings get() = AppGraph.settings
    private val dictionary get() = AppGraph.dictionary

    private lateinit var bridge: EditorBridge
    private lateinit var keyboard: KeyboardController
    private lateinit var voice: VoiceSessionController

    private val mode = MutableStateFlow(InputMode.KEYS)
    private val strip = MutableStateFlow(StripState())
    private val enterKind = MutableStateFlow(EnterKind.NEWLINE)
    private val pickerOpen = MutableStateFlow(false)
    private val emojiOpen = MutableStateFlow(false)
    private val emojiCatalog = MutableStateFlow<EmojiCatalog?>(null)
    /** The emoji search above the letter keys: what is typed and what it finds; null when not searching. */
    private val emojiSearch = MutableStateFlow<EmojiSearchState?>(null)
    private val searchingEmoji get() = emojiOpen.value && emojiSearch.value != null
    private var backspaceTarget: EditorBridge.Target? = null
    /** An active hold-and-slide Backspace, how much of its text before the cursor is in the field, and its level. */
    private var scrub: BackspaceScrub? = null
    /** What a Backspace slide into the editor points at; null when not aiming. */
    private val wordAim = MutableStateFlow<WordAim?>(null)
    /** A [WordAimSession] (Android 14+), typed loosely so older versions never load the class. */
    private var wordAimSession: Any? = null
    private var scrubKept = 0
    private var scrubLevel = 0
    /** Where the finger is over the editor during a Backspace slide (screen px); NaN when not. */
    private var aimX = Float.NaN
    private var aimY = Float.NaN
    /** The focused editor is a password or private field (no suggestions, learning, or voice). */
    private val sensitiveField = MutableStateFlow(false)
    /** A physical keyboard is connected; minimized state is remembered separately for it. */
    private val physicalKeyboard = MutableStateFlow(false)
    /** Back closes the picker (API 33+, where back reaches the IME through this dispatcher). */
    private var pickerBackCallback: Any? = null
    /** Last active model ID seen by the model-store observer (detects model switches). */
    private var observedActiveId: String? = null
    private var noteJob: Job? = null
    /** Latest editor selection, from [onStartInputView] and [onUpdateSelection]. */
    private var selStart = -1
    private var selEnd = -1
    private var composeView: View? = null
    private var placementBounds: Rect? = null
    private var floatingPlacement = false
    private var layoutBackHandler: (() -> Boolean)? = null
    /** The emoji key's language menu while it stays open; Back closes it first. */
    private var menuBackHandler: (() -> Boolean)? = null
    private var layoutBackCallback: Any? = null
    private var resizeOnOpen = false
    private var lastOwnEditAt = 0L
    private var stripJob: Job? = null
    private var stripGeneration = 0L
    private var stripRequest: StripRequest? = null
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var clipboardReader: ClipboardReader
    private var clipboardJob: Job? = null
    private var clipPruneJob: Job? = null
    private var inputViewActive = false
    private lateinit var clipHistory: ClipboardHistory
    /** Clipboard history for the minimized bar, empty in sensitive fields. */
    private val clipChips = MutableStateFlow<List<ClipChip>>(emptyList())
    /** Bumped each time the keyboard opens, so the clip row starts at the newest item. */
    private val inputSession = MutableStateFlow(0)
    /**
     * Counts fields the keyboard is shown for. Unlike [inputSession], an editor that restarts its
     * input while it stays shown (some apps do after a cut) keeps the keyboard's panels as they
     * were, such as open Keyboard tools.
     */
    private val shownSession = MutableStateFlow(0)
    private var shownEditor: String? = null
    private lateinit var screenshots: RecentScreenshots
    /** Recent screenshots, when that setting is on and the photos permission is granted. */
    private val recentShots = MutableStateFlow<List<ClipEntry.Screenshot>>(emptyList())
    /** Screenshots taken before the clipboard history was last cleared stay hidden. */
    private var clipsClearedAt = 0L
    /** What the last Clear clips removed, for Undo clear until something new is copied; null when there is nothing to undo. */
    private var lastClear: ClearedClips? = null
    /** Full keys for this field only (from the Backspace fan); the next field opens minimized again. */
    private val keyboardOnce = MutableStateFlow(false)
    private var keyboardOnceEditor: String? = null
    /** What the minimized bar's Backspace fan offers in the current editor. */
    private val fanSpec = MutableStateFlow(FanSpec(action = null, newLine = false))
    private var screenshotJob: Job? = null
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (inputViewActive) refreshScreenshots()
        }
    }
    /** The image that just finished, offered with Insert above the strip. */
    private val aiReady = MutableStateFlow<AiReady?>(null)
    /** A key or token the service refused, with the saved hint it had then: until it changes, setup says so. */
    private val aiRefused get() = AppGraph.secrets.refused
    /** An action that failed, offered with Try again in the same place. */
    private val aiFailure = MutableStateFlow<AiFailure?>(null)
    /** Retires the ready or failed card after a while, so it doesn't sit over the app for good. */
    private var aiCardJob: Job? = null
    /** The app's field had text while the card was up: once it empties (the message went), the card goes. */
    private var aiCardFieldHadText = false
    /** How far the AI card above the keyboard reaches over the app (px); the app moves up by it. */
    private var aiOverlayPx = 0
    /** AI actions running in the background, oldest first; each can be stopped from the menu. */
    private val aiTasks = MutableStateFlow<List<AiTask>>(emptyList())
    /** How to stop each running action, by [AiTask.id]. */
    private val aiRuns = HashMap<Long, AiRun>()
    private var nextAiTask = 0L

    /** [editor]: the field the prompt was typed in, where its words come back if it fails or is stopped. */
    /** [secret] and [secretVersion]: the credential the request went with, so a late 401 marks only that one. */
    /** [images]: the pictures sent with the prompt, which come back with it after a failure or Stop. */
    private class AiRun(val client: AiClient, val editor: String?, val secret: Secret?, val secretVersion: Int,
        val images: List<PromptImage>, var job: Job? = null)
    /** When Lowkey last put a created image on the system clipboard ([ClipDescription] time), or 0. */
    private var lastOwnClipAt = 0L
    /** A prompt that failed, was stopped or was closed unsent, offered again by the next prompt. */
    private var retryPrompt: RetryPrompt? = null
    /** The focused field is the image prompt window's own ([AiPromptActivity]), not an app's. */
    private val inAiPrompt = MutableStateFlow(false)
    /** That field is empty: Create (Enter) shows dimmed. */
    private val aiPromptEmpty = MutableStateFlow(true)
    /** Improve is rewriting the prompt window's words: Create (Enter) dims, as for an empty prompt. */
    private val aiPromptImproving = MutableStateFlow(false)
    /** The open prompt window reports whether its words are blank ([promptState]); until it does, the keyboard looks. */
    private var promptStateFromWindow = false
    /** The app's field the prompt window was opened from, where its image goes. */
    private var composerEditor: String? = null
    /**
     * A card (or a note) for [composerEditor] that came while the prompt window had the keys; it shows
     * once that field is back.
     */
    private var pendingAiCard: PendingAiCard? = null
    /** The ready card a report was opened from, put back if the report is cancelled. */
    private var reportHold: ReportHold? = null
    /** Shown once the keyboard returns after a report is filed. */
    private var reportNote: String? = null
    private val discardImageHook: (String) -> Boolean = { discardReportedImage(it) }

    private class PendingAiCard(val editor: String?, val ready: AiReady? = null, val failure: AiFailure? = null,
        val note: String? = null)
    private class ReportHold(val entry: ClipEntry.Image, val ready: AiReady, val editor: String?)

    /** Words (and pictures) to offer again, only in the field ([editor], `EditorBridge.editorKey`) they were typed for. */
    private data class RetryPrompt(val editor: String?, val text: String, val images: List<PromptImage> = emptyList())

    /** Pictures added to the prompt in the window ([AiComposerHost.promptImages]). */
    private val promptImagesFlow = MutableStateFlow<List<PromptImage>>(emptyList())
    /** Images in the clipboard history and recent screenshots, for the window's + tray. */
    private val recentImagesFlow = MutableStateFlow<List<ClipEntry>>(emptyList())
    private var nextPromptImage = 0L
    /** Pictures still being read; they count toward the limit already. */
    private var promptImagesLoading = 0
    /** Counts each new prompt: a picture still being read for an older one is dropped. */
    private var promptImagesEpoch = 0
    /**
     * Counts prompt windows: each gets its own in its intent, so a replaced one (left behind the photo
     * picker, or closing late) can't save its words over, or clear the pictures of, the window now open.
     */
    private var composerSession = 0
    /** The window the wand opened with a ready image ([wandImage]): it leaves the field's saved draft alone. */
    private var wandSession = -1
    private var wandImage = -1L

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        refreshClipboard()
        recordClipboard(changed = true)
    }

    /**
     * Clips copied while a password or private field had focus, by [privateClipKey]. They stay out
     * of the history and show without a preview, like clips an app marks sensitive.
     */
    private val privateClips: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** The clip history, system clip and screenshot cut-off from before a Clear clips. */
    private class ClearedClips(val entries: List<ClipEntry>, val primary: ClipData?, val clearedAtBefore: Long)

    /** What an autocorrection depends on: the word, the keyboard (language and layout), and sentence position. */
    /** [lexiconLoad] tells a result computed on a fallback list from one on the full list. */
    /** [lead] is the language written in ([Suggester.contextLanguage]), which leads suggestions. */
    private data class CorrectionKey(val word: String, val keyboard: String, val atSentenceStart: Boolean,
        val lexiconLoad: Int, val languages: List<String>, val terms: List<PersonalTerm>, val lead: Int = 0)
    private data class CachedCorrection(val key: CorrectionKey, val fix: String?)
    private data class StripRequest(val context: CorrectionKey, val before: String,
        val correcting: Boolean, val enabled: Boolean, val catalog: EmojiCatalog?, val session: Int)

    /** Autocorrection computed with the latest suggestions; a cached null is a valid answer. */
    private var cachedCorrection: CachedCorrection? = null

    /** An autocorrection that an immediate Backspace undoes, only in the same editor and spot. */
    private inner class Undo(val original: String, val corrected: String, val separator: String, val editor: String?, val cursor: Int) {
        fun stillAtCursor(): Boolean {
            if (bridge.editorKey() != editor) return false
            val sel = bridge.currentSelection() ?: return false
            if (sel.first != sel.second || sel.first != cursor) return false
            return bridge.textBeforeCursor(corrected.length + separator.length) == corrected + separator
        }
    }

    private var lastCorrection: Undo? = null
    /** Words the user un-corrected with Backspace; not autocorrected again this session. */
    private val rejectedCorrections = HashSet<String>()

    /** True right after a suggestion pick added a trailing space (for punctuation swap). */
    private var autoSpaced = false

    /** The slide from the pointing stick (the red dot by G) that is under way selects text (Shift was tapped just before it). */
    private var slideSelects = false
    private val doubleSpacePeriod = DoubleSpacePeriod()

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        owner.onCreate()
        AiComposerLink.host = this
        AiReportLink.discardImage = discardImageHook
        bridge = EditorBridge { currentInputConnection }
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardReader = ClipboardReader(contentResolver)
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        clipHistory = ClipboardHistory(this, maxAgeMs = { settings.current.clipExpiry.millis })
        clipHistory.entries.onEach {
            scheduleClipPrune()
            refreshClipChips()
        }.launchIn(scope)
        screenshots = RecentScreenshots(this)
        recentShots.onEach { refreshClipChips() }.launchIn(scope)
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        keyboard = KeyboardController(AppGraph.currentKeyboard())
        scope.launch {
            emojiCatalog.value = withContext(Dispatchers.IO) {
                runCatching {
                    val paint = android.graphics.Paint()
                    // Without a keyword file, search falls back to the Unicode names; the panel still works.
                    val annotations = EMOJI_KEYWORD_LANGUAGES.mapNotNull { language ->
                        runCatching {
                            language to EmojiAnnotations.parse(assets.open("emoji/keywords-$language.txt").bufferedReader().use { it.readText() })
                        }.getOrNull()
                    }.toMap()
                    EmojiCatalog.parse(assets.open("emoji/emoji-test.txt").bufferedReader().use { it.readText() })
                        .withAnnotations(annotations)
                        .supported(paint::hasGlyph)
                        // Index emoji names and search words here, not on the first keystroke.
                        .also { it.nameIndex; it.search.prepare(EMOJI_KEYWORD_LANGUAGES) }
                }.getOrElse { EmojiCatalog(emptyList()) }
            }
            if (inputViewActive && mode.value == InputMode.KEYS) refreshStrip()
        }
        voice = VoiceSessionController(
            scope = scope,
            engine = AppGraph.speechEngine,
            sink = voiceSink,
            config = ::voiceConfig,
            readiness = ::voiceReadiness,
            clock = System::currentTimeMillis,
            firstSessionId = AppGraph.sessionLog.nextSessionId(),
            onSessionFinished = AppGraph.sessionLog::add,
            onDictationCompleted = ::returnToKeysAfterDictation,
        )
        physicalKeyboard.value = hasPhysicalKeyboard(resources.configuration)
        mode.value = initialMode()
        pickerOpen.onEach(::updatePickerBack).launchIn(scope)
        emojiOpen.onEach { open ->
            // Whatever closes emoji also ends a search in it, and gives Shift back to the editor.
            if (!open && emojiSearch.value != null) {
                emojiSearch.value = null
                keyboard.showHome()
                updateShift()
            }
            updateEmojiBack(open)
        }.launchIn(scope)
        // Refresh suggestions when the dictionary changes; readiness when models change.
        dictionary.state.drop(1).onEach { refreshStrip() }.launchIn(scope)
        // Follow keyboards changed in Settings while the keyboard is open (e.g. the current one
        // turned off, or its language deleted), so the keys, the space bar and autocorrect stay
        // on the same keyboard.
        // A word list that finishes loading replaces the small fallback: redo the suggestions.
        AppGraph.lexiconLoads.drop(1).onEach {
            if (inputViewActive && mode.value == InputMode.KEYS) refreshStrip()
        }.launchIn(scope)
        combine(settings.settings, AppGraph.languagePacks.state) { s, _ ->
            val current = AppGraph.currentKeyboard(s)
            current.id to AppGraph.suggestionLanguages(current, s).map { it.id }
        }
            .distinctUntilChanged().onEach {
            val current = AppGraph.currentKeyboard()
            if (keyboard.state.value.keyboard.id != current.id) {
                keyboard.setKeyboard(current)
            }
            cachedCorrection = null
            preloadLanguages()
            if (inputViewActive && mode.value == InputMode.KEYS) refreshStrip()
        }.launchIn(scope)
        observedActiveId = AppGraph.models.state.value.activeId
        AppGraph.models.state.drop(1).onEach { s ->
            // A model switch (picker, companion page, or a finished download) ends any capture
            // cleanly; the controller's session IDs drop late events from the old session.
            if (s.activeId != observedActiveId) {
                observedActiveId = s.activeId
                voice.cancel(CancelReason.MODEL_CHANGED)
            }
            voice.refreshReadiness()
        }.launchIn(scope)
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let(owner::attachTo)
        return ComposeView(this).also { view ->
            owner.attachTo(view)
            composeView = view
            view.setContent {
                val s by settings.settings.collectAsState()
                val m by mode.collectAsState()
                val kb by keyboard.state.collectAsState()
                val st by strip.collectAsState()
                val vs by voice.state.collectAsState()
                val models by AppGraph.models.state.collectAsState()
                val ek by enterKind.collectAsState()
                val aim by wordAim.collectAsState()
                val picker by pickerOpen.collectAsState()
                val emoji by emojiOpen.collectAsState()
                val catalog by emojiCatalog.collectAsState()
                val search by emojiSearch.collectAsState()
                val sensitive by sensitiveField.collectAsState()
                val physical by physicalKeyboard.collectAsState()
                val clips by clipChips.collectAsState()
                val session by shownSession.collectAsState()
                val once by keyboardOnce.collectAsState()
                val fan by fanSpec.collectAsState()
                val inPrompt by inAiPrompt.collectAsState()
                val promptEmpty by aiPromptEmpty.collectAsState()
                val promptImproving by aiPromptImproving.collectAsState()
                val ready by aiReady.collectAsState()
                val failure by aiFailure.collectAsState()
                val savedSecrets by AppGraph.secrets.saved.collectAsState()
                val refused by aiRefused.collectAsState()
                val tasks by aiTasks.collectAsState()
                val packs by AppGraph.languagePacks.state.collectAsState()
                val keyboards = remember(s.enabledKeyboards, packs.installed) { AppGraph.usableKeyboards(s) }
                val keyboardTheme by AppGraph.keyboardTheme.theme.collectAsState()
                val appDark = resolveDark(s.theme, isSystemInDarkTheme())
                val colors = remember(keyboardTheme, appDark) { resolveKeyboardColors(keyboardTheme, appDark) }
                val custom = keyboardTheme.custom?.takeIf { keyboardTheme.enabled }
                val imageName = custom?.image
                // Decoded once per file on an IO thread (and cached); a missing file leaves the colour.
                val loadedImage by produceState<LoadedBackground?>(null, imageName) {
                    value = imageName?.let { AppGraph.keyboardTheme.images.load(it) }
                }
                val image = loadedImage?.takeIf { it.name == imageName }
                val background = remember(image, custom?.dim) {
                    if (image != null && custom != null) KeyboardBackgroundImage(image.bitmap, custom.dim) else null
                }
                val (viewportWidth, viewportHeight) = placementViewport()
                val layout = remember(kb.layer, kb.keyboard, s.numberRow, s.bottomKey, s.shadeNumberRow, kb.pad) {
                    Layouts.forLayer(kb.layer, kb.keyboard, s.numberRow, s.bottomKey, s.shadeNumberRow, kb.pad)
                }
                val profile = PlacementProfile.forSize(viewportWidth, viewportHeight)
                val placement = s.placement.get(profile)
                val floating = placement.mode == PlacementMode.FLOATING
                val navStyle = remember(colors.background, custom, image, floating) {
                    keyboardNavigationBarStyle(
                        colors.background,
                        custom?.imageRange(image?.sample),
                        floating = floating,
                        edgeToEdge = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM,
                    )
                }
                // Keyed on the style, not on light or dark: a custom background can change either way,
                // and a floating keyboard leaves the bar transparent.
                LaunchedEffect(navStyle) { styleNavigationBar(navStyle) }
                KeyboardRoot(
                    KeyboardUiState(
                        mode = m,
                        keyboard = kb,
                        layout = layout,
                        strip = st.copy(enabled = s.suggestionStrip && !sensitive),
                        voice = vs,
                        models = models,
                        pickerOpen = picker && m == InputMode.VOICE,
                        // In the image prompt window, Enter is its Create button.
                        enterKind = if (inPrompt) promptEnterKind(aiSetup(s, savedSecrets, refused), promptEmpty, promptImproving) else ek,
                        rowHeightDp = s.keyboardHeight.rowHeightDp,
                        colors = colors,
                        background = background,
                        numberRow = s.numberRow,
                        pointingStick = s.pointingStick,
                        stickSpeed = s.stickSpeed.scale,
                        emojiOpen = emoji && m == InputMode.KEYS,
                        emojiCatalog = catalog,
                        emojiSearch = search.takeIf { emoji && m == InputMode.KEYS },
                        recentEmoji = if (sensitive) emptyList() else s.recentEmoji,
                        placement = placement,
                        profile = profile,
                        viewportWidthDp = viewportWidth,
                        viewportHeightDp = viewportHeight,
                        resizeOnOpen = resizeOnOpen,
                        // Never hide a running dictation; it returns to keys (and the bar) when done.
                        minimized = isMinimized(s, physical, once) && m == InputMode.KEYS,
                        showExpandHint = !s.compactExpandHintSeen,
                        clipHistory = clips,
                        inputSession = session,
                        fan = fan,
                        wordAim = aim,
                        keyboards = keyboards,
                        ai = run {
                            // Not in the prompt window itself: its own box shows what is running.
                            val available = s.aiKeyboard && !sensitive && kb.pad == null && !inPrompt
                            AiUi(
                                available = available,
                                tasks = tasks,
                                ready = ready.takeIf { available },
                                failure = failure.takeIf { available },
                            )
                        },
                    ),
                    clock = System::currentTimeMillis,
                    actions = this,
                )
            }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun placementViewport(): Pair<Float, Float> {
        val density = resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = getSystemService(WindowManager::class.java).currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return (metrics.bounds.width() - insets.left - insets.right) / density to
                (metrics.bounds.height() - insets.top - insets.bottom) / density
        }
        return resources.configuration.screenWidthDp.toFloat() to resources.configuration.screenHeightDp.toFloat()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val bounds = placementBounds ?: return
        if (floatingPlacement) {
            val bottom = window.window?.decorView?.height ?: bounds.bottom
            outInsets.contentTopInsets = bottom
            outInsets.visibleTopInsets = bottom
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(bounds)
        } else {
            // A card above the keyboard (the AI composer, a ready image) counts as keyboard, so the
            // app moves its text box up above it instead of under it.
            val top = bounds.top - aiOverlayPx
            outInsets.contentTopInsets = top
            outInsets.visibleTopInsets = top
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        }
    }

    override fun onPlacementBounds(left: Int, top: Int, right: Int, bottom: Int, floating: Boolean) {
        val next = Rect(left, top, right, bottom)
        if (placementBounds == next && floatingPlacement == floating) return
        placementBounds = next
        floatingPlacement = floating
        composeView?.requestLayout()
    }

    override fun onSavePlacement(profile: PlacementProfile, placement: KeyboardPlacement) {
        settings.update { it.copy(placement = it.placement.update(profile) { placement }) }
    }

    override fun onSetNumberRow(enabled: Boolean) {
        settings.update { it.copy(numberRow = enabled) }
    }

    override fun onMoveCursor(chars: Int, lines: Int) {
        if (!inputViewActive) return
        clearNote()
        lastCorrection = null
        autoSpaced = false
        doubleSpacePeriod.reset()
        // No tick where the cursor can't go: at the end of the text, or up and down in a one-line field.
        if (!bridge.moveCursor(chars, lines, select = slideSelects)) return
        if (settings.current.vibrateOnKeyPress) composeView?.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        AppGraph.gestures.tryEmit(KeyboardGesture.CursorMoved(chars, lines, slideSelects))
    }

    override fun onCursorSlide(active: Boolean) {
        // Shift tapped just before the slide: select instead of only moving, for this slide.
        slideSelects = active && keyboard.shiftTapped
        if (!active && inputViewActive) {
            updateShift()
            refreshStrip()
        }
    }

    override fun onEditAction(action: EditAction) {
        if (!inputViewActive) return
        clearNote()
        lastCorrection = null
        autoSpaced = false
        doubleSpacePeriod.reset()
        markOwnEdit()
        if (!bridge.performEdit(action)) showNote("This field doesn't support ${action.label.lowercase()}", warn = true)
        updateShift()
        refreshStrip()
    }

    override fun onPrepareResize() {
        voice.cancel(CancelReason.SWITCHED_TO_KEYS)
        mode.value = InputMode.KEYS
        pickerOpen.value = false
        emojiOpen.value = false
        keyboardOnce.value = true
        keyboardOnceEditor = bridge.editorKey()
    }

    override fun onLayoutBackHandler(handler: (() -> Boolean)?) {
        layoutBackHandler = handler
        updateLayoutBack()
    }

    override fun onMenuBackHandler(handler: (() -> Boolean)?) {
        menuBackHandler = handler
        updateLayoutBack()
    }

    /** An open menu takes Back before the layout's own handler; each owner keeps its own slot. */
    private fun updateLayoutBack() {
        val handler = menuBackHandler ?: layoutBackHandler
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val dispatcher = window.onBackInvokedDispatcher
            (layoutBackCallback as? OnBackInvokedCallback)?.let(dispatcher::unregisterOnBackInvokedCallback)
            layoutBackCallback = if (handler != null) OnBackInvokedCallback { handler() }.also {
                dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
            } else null
        }
    }

    /**
     * Android hides the on-screen keyboard while a physical keyboard is connected, unless the
     * system "Show on-screen keyboard" setting is on. Stay visible anyway (as the slim bar, by
     * default), so voice apps that only appear next to a visible keyboard, like Wispr Flow, work.
     */
    override fun onEvaluateInputViewShown(): Boolean =
        super.onEvaluateInputViewShown() || showWithPhysicalKeyboard()

    /** The default refuses implicit show requests (a field gaining focus) with a physical keyboard. */
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean =
        showWithPhysicalKeyboard() || super.onShowInputRequested(flags, configChange)

    private fun showWithPhysicalKeyboard() =
        settings.current.showWithPhysicalKeyboard && hasPhysicalKeyboard(resources.configuration)

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Before super: it restarts the input view, which reads the minimized state.
        physicalKeyboard.value = hasPhysicalKeyboard(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    private fun hasPhysicalKeyboard(config: Configuration) =
        config.keyboard != Configuration.KEYBOARD_NOKEYS &&
            config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

    private fun isMinimized(
        s: Settings = settings.current,
        physical: Boolean = physicalKeyboard.value,
        once: Boolean = keyboardOnce.value,
    ) = !once && if (physical) s.minimizedWithPhysicalKeyboard else s.minimizedWithoutPhysicalKeyboard

    /** Hiding the keyboard ends "keyboard this time". */
    override fun onWindowHidden() {
        keyboardOnce.value = false
        super.onWindowHidden()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        hideSystemImeButtons()
    }

    /**
     * The navigation bar under the keyboard (gesture handle, or back and switcher buttons) takes its
     * icon colour from the input method's window, and before Android 15 also its background. Before
     * Android 15 the bar is painted in the keyboard's background colour; from Android 15 the keyboard,
     * including any background image, shows through; a floating keyboard leaves the bar
     * transparent. [style] comes from [keyboardNavigationBarStyle], which judges an image by its
     * worst case, not an average.
     */
    @Suppress("DEPRECATION")
    private fun styleNavigationBar(style: NavigationBarStyle) {
        val win = window.window ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            win.insetsController?.setSystemBarsAppearance(if (style.lightIcons) 0 else light, light)
        } else {
            val flag = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val flags = win.decorView.systemUiVisibility
            win.decorView.systemUiVisibility = if (style.lightIcons) flags and flag.inv() else flags or flag
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            win.navigationBarColor = style.color.toArgb()
        }
    }

    /**
     * Android 16+ draws its own IME navigation bar (hide-keyboard chevron and keyboard-switcher
     * button) as a caption bar over the bottom of the keyboard. Those buttons overlapped the voice
     * row and were easy to hit by accident, so hide them: Back still closes the keyboard and the
     * globe key (long-press) still switches keyboards. Older versions get bottom padding instead.
     */
    private fun hideSystemImeButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            window.window?.insetsController?.hide(WindowInsets.Type.captionBar())
        }
    }

    /**
     * Called whenever a field gets input, with or without the keyboard shown. When it is the field
     * the image editor was opened from, insert the attached caption and image once.
     */
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        doubleSpacePeriod.reset()
        super.onStartInput(attribute, restarting)
        val attachment = PendingAttachment.take(EditorBridge.keyOf(attribute), System.currentTimeMillis()) ?: return
        bridge.editorInfo = attribute
        // After this callback returns, so the editor's connection is fully set up.
        scope.launch { insertAttachment(attachment) }
    }

    private fun insertAttachment(attachment: Attachment) {
        val target = bridge.captureTarget() ?: return
        markOwnEdit()
        if (attachment.caption.isNotBlank()) {
            val before = bridge.textBeforeCursor(1)
            val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
            bridge.pasteText(target, lead + attachment.caption)
        }
        val attached = try {
            bridge.pasteImage(target, Uri.parse(attachment.imageUri), attachment.mimeType)
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!attached) showNote("This app didn't take the image · it's on the clipboard", warn = true)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        owner.onShow()
        onBackspaceHoldEnded()
        physicalKeyboard.value = hasPhysicalKeyboard(resources.configuration)
        bridge.editorInfo = info
        // "Keyboard this time" lasts while the same field stays focused (a fold or unfold restarts it).
        if (keyboardOnce.value && bridge.editorKey() != keyboardOnceEditor) keyboardOnce.value = false
        fanSpec.value = FanSpec(bridge.fanAction(), bridge.fanNewLine(), undoClear = lastClear != null)
        inputViewActive = true
        emojiOpen.value = false
        sensitiveField.value = bridge.isSensitiveField
        // Before the clip chips: in the prompt window, image chips add the image to the prompt.
        inAiPrompt.value = AiComposerLink.isPromptField(info, packageName)
        if (inAiPrompt.value && !promptStateFromWindow) aiPromptEmpty.value = editorEmpty()
        // Also applies a changed Keep copied clips setting.
        scheduleClipPrune()
        refreshClipChips()
        resizeOnOpen = info.privateImeOptions?.contains("lowkey.resize") == true
        placementBounds = null
        expectedEchoes.clear()
        inputSession.value++
        bridge.editorKey().let { editor ->
            if (editor == null || editor != shownEditor) {
                shownSession.value++
                // Another field: a card was about the last one.
                clearAiCards()
            }
            shownEditor = editor
        }
        // A web page that sends a message may restart the field instead of moving its cursor.
        retireAiCardOnSend()
        // A restart keeps the session only if it's still the same editor at the owned position;
        // otherwise (another field in the same view) the old capture must never write here.
        val keepSession = restarting && voice.currentSessionId != null &&
            voiceSink.ownsEditor(bridge.editorKey(), info.initialSelStart, info.initialSelEnd)
        if (!keepSession && voice.currentSessionId != null) {
            voiceSink.editorGone = true
            voice.cancel(CancelReason.EDITOR_CHANGED)?.let { showVoiceOutcomeNote(it) }
        }
        if (!keepSession) {
            selStart = info.initialSelStart
            selEnd = info.initialSelEnd
            voiceSink.ownership.reset()
            voiceSink.editorGone = false
        }
        enterKind.value = bridge.enterKind()
        restoreReportHold()
        if (keepSession) return
        keyboard.reset(bridge.numberPad)
        keyboard.setKeyboard(AppGraph.currentKeyboard())
        preloadLanguages()
        autoSpaced = false
        // The slim bar opens in keys mode without forgetting the remembered mode.
        if (resizeOnOpen) onPrepareResize()
        mode.value = if (resizeOnOpen || isMinimized()) InputMode.KEYS else initialMode()
        // The microphone is visible in keys mode too; show its readiness before the first tap.
        voice.refreshReadiness()
        if (mode.value == InputMode.VOICE) {
            voice.clearOutcome()
            if (settings.current.autoStartVoice) autoStartVoice()
        } else {
            updateShift()
            refreshStrip()
        }
        refreshClipboard()
        recordClipboard(changed = false)
        refreshScreenshots()
        showPendingAiCard()
        restoreReportHold()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        doubleSpacePeriod.reset()
        AppGraph.personalModel.flush()
        shownEditor = null
        menuBackHandler = null
        onLayoutBackHandler(null)
        inputViewActive = false
        clipboardJob?.cancel()
        invalidateStrip()
        strip.value = strip.value.copy(clipboard = null, slots = listOf(null, null, null))
        // Hiding the keyboard or moving to another editor cancels any capture.
        voice.cancel(if (finishingInput) CancelReason.EDITOR_CHANGED else CancelReason.KEYBOARD_HIDDEN)
        onBackspaceHoldEnded()
        pickerOpen.value = false
        emojiOpen.value = false
        clearPrompt()
        clearAiCards()
        owner.onHide()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (inputViewActive) retireAiCardOnSend()
        if (inputViewActive && inAiPrompt.value && !promptStateFromWindow) aiPromptEmpty.value = editorEmpty()
        if (voice.currentSessionId == null) {
            // In either mode, an update outside a session settles our earlier writes.
            voiceSink.ownership.observedIdle()
        } else if (!voiceSink.ownership.owns(newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
            // The cursor must stay where dictation started (or at the end of its composing text).
            // Any other move cancels, so late results can't land where the user didn't expect.
            voice.cancel(CancelReason.CURSOR_MOVED)
        }
        if (mode.value != InputMode.KEYS) return
        // The echo of a letter we typed, where we expected it: Shift and the strip are up to date.
        expectedEchoes.firstOrNull()?.let { expected ->
            if (newSelStart == expected && newSelEnd == expected) {
                expectedEchoes.removeFirst()
                return
            }
            expectedEchoes.clear()
        }
        // Our own edits update shift synchronously; only react to external cursor moves.
        if (SystemClock.uptimeMillis() - lastOwnEditAt > OWN_EDIT_WINDOW_MS) {
            doubleSpacePeriod.reset()
            autoSpaced = false
            updateShift()
        }
        refreshStrip()
    }

    override fun onDestroy() {
        if (AiComposerLink.host === this) AiComposerLink.host = null
        if (AiReportLink.discardImage === discardImageHook) AiReportLink.discardImage = null
        aiRuns.values.forEach { it.client.cancel() }
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        contentResolver.unregisterContentObserver(mediaObserver)
        voice.cancel(CancelReason.KEYBOARD_CLOSED)
        scope.cancel()
        owner.onDestroy()
        composeView = null
        super.onDestroy()
    }

    // ---- KeyboardActions ----

    override fun onKeyDown() {
        if (settings.current.vibrateOnKeyPress) {
            composeView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    override fun onKey(key: Key) {
        if (searchingEmoji && typeIntoEmojiSearch(key)) return
        if (key !is Key.Space) doubleSpacePeriod.reset()
        // While searching emoji the letters type the search; Shift, ?123 and the globe keep it open.
        if (key !is Key.Emoji && !searchingEmoji) emojiOpen.value = false
        clearPrompt()
        if (key !is Key.Shift && key !is Key.LayerSwitch) clearNote()
        // Any key other than Backspace accepts the last autocorrection (Space may set a new one).
        if (key !is Key.Shift && key !is Key.LayerSwitch && key !is Key.Backspace) lastCorrection = null
        when (key) {
            is Key.Char -> typeChar(key.output)
            is Key.Shift -> keyboard.onShiftTap()
            is Key.LayerSwitch -> {
                keyboard.setLayer(key.target)
                updateShift()
            }
            is Key.Globe -> useKeyboard(keyboard.cycleKeyboard(AppGraph.usableKeyboards()))
            is Key.Emoji -> {
                pickerOpen.value = false
                emojiOpen.value = true
            }
            is Key.Space -> typeSpace()
            is Key.Enter -> {
                markOwnEdit()
                autoSpaced = false
                // Sent: a ready image's card has done its job (the image stays in the clipboard row).
                clearReadyCard()
                bridge.performEnter()
                updateShift()
            }
            is Key.Backspace -> onBackspace()
            is Key.Gap -> Unit
        }
    }

    override fun onBackspace() {
        if (searchingEmoji) {
            deleteFromEmojiSearch()
            return
        }
        doubleSpacePeriod.reset()
        clearNote()
        markOwnEdit()
        autoSpaced = false
        // Backspace right after an autocorrection restores what was typed, and that word is
        // left alone for the rest of this keyboard session.
        val undo = lastCorrection
        lastCorrection = null
        if (undo != null && undo.stillAtCursor()) {
            bridge.replaceBeforeCursor(undo.corrected.length + undo.separator.length, undo.original)
            rejectedCorrections += undo.original.lowercase()
            unlearnCorrection(undo.original, undo.corrected)
            showNote("Kept “${undo.original}”")
        } else {
            bridge.deleteBackward(emojiCatalog.value?.suffixOf(bridge.textBeforeCursor(64)))
        }
        updateShift()
        refreshStrip()
    }

    override fun onBackspaceHoldStarted() {
        backspaceTarget = bridge.captureTarget()
    }

    override fun onBackspaceHoldEnded() {
        backspaceTarget = null
        scrub = null
        endWordAim()
    }

    /** Reads where the editor's words are before the slider deletes anything (Android 14+). */
    private fun startWordAim(target: EditorBridge.Target) {
        endWordAim()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || bridge.isPasswordField) return
        val metrics = resources.displayMetrics
        wordAimSession = WordAimSession(
            target.connection, mainExecutor,
            area = RectF(-WIDE_PX, 0f, WIDE_PX, metrics.heightPixels.toFloat()),
            slopPx = 14 * metrics.density,
            onReady = { session -> if (wordAimSession === session && !aimX.isNaN()) applyWordAim() },
            textChanged = { scrub.let { it == null || scrubKept != it.before.length } },
        )
    }

    private fun endWordAim() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) (wordAimSession as? WordAimSession)?.end()
        wordAimSession = null
        aimX = Float.NaN
        aimY = Float.NaN
        wordAim.value = null
    }

    override fun onWordAim(screenX: Float, screenY: Float) {
        aimX = screenX
        aimY = screenY
        applyWordAim()
    }

    override fun onWordAimLeft() {
        aimX = Float.NaN
        aimY = Float.NaN
        wordAim.value = null
        val s = scrub ?: return
        scrubToKept(s.before.length)
    }

    /** Deletes from the word under the finger up to the cursor, putting back anything after it. */
    private fun applyWordAim() {
        val s = scrub
        val session = wordAimSession
        if (s == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            session !is WordAimSession || session.unsupported
        ) {
            // Nothing to point at: show the text as it was, so releasing deletes nothing.
            if (s != null) scrubToKept(s.before.length)
            wordAim.value = WordAim.Unsupported
            return
        }
        if (!session.ready) {
            wordAim.value = WordAim.Searching
            return
        }
        val kept = session.keptAt(aimX, aimY, s.before.length)
        if (kept != null && kept != scrubKept) {
            val before = scrubKept
            if (!scrubToKept(kept)) {
                wordAim.value = WordAim.Unsupported
                return
            }
            if (kept != before && settings.current.vibrateOnKeyPress) {
                composeView?.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
            }
        }
        // Off the text the finger leaves the cut where it was.
        wordAim.value = WordAim.Cut(wordsFrom(s.before, scrubKept))
    }

    /** Shows [kept] characters of the slider's text before the cursor. */
    private fun scrubToKept(kept: Int): Boolean {
        val s = scrub ?: return false
        val target = backspaceTarget ?: return false
        if (mode.value != InputMode.KEYS) return false
        // Text deleted past the snapshot can't be restored.
        if (s.topIsFinal && scrubLevel == s.top) return false
        if (kept == scrubKept) return true
        markOwnEdit()
        if (!bridge.scrubToKept(target, s, scrubKept, kept)) {
            scrub = null
            return false
        }
        scrubKept = kept
        scrubLevel = AIMED
        return true
    }

    override fun onClearAllText() {
        if (searchingEmoji) return setEmojiQuery("")
        val target = backspaceTarget ?: return
        if (!bridge.isCurrent(target) || mode.value != InputMode.KEYS) return
        lastCorrection = null
        autoSpaced = false
        markOwnEdit()
        if (bridge.clearAll(target)) {
            updateShift()
            refreshStrip()
            showNote("All text cleared")
        } else {
            showNote("This app couldn't clear the field", warn = true)
        }
    }

    override fun onDeleteBeforeCursor() {
        if (searchingEmoji) return setEmojiQuery("")
        val target = backspaceTarget ?: return
        if (!bridge.isCurrent(target) || mode.value != InputMode.KEYS) return
        lastCorrection = null
        autoSpaced = false
        when {
            bridge.hasSelection() -> showNote("Deselect the text first", warn = true)
            bridge.textBeforeCursor(1).isEmpty() -> showNote("Nothing before the cursor")
            else -> {
                markOwnEdit()
                if (bridge.deleteAllBeforeCursor(target)) {
                    updateShift()
                    refreshStrip()
                    showNote("Deleted everything before the cursor")
                } else {
                    showNote("This app couldn't delete the text", warn = true)
                }
            }
        }
    }

    override fun onBackspaceScrubStart(): BackspaceScrub? {
        // The search is not the editor: Backspace there deletes, and slides do nothing.
        if (searchingEmoji) return null
        val target = backspaceTarget ?: return null
        if (!bridge.isCurrent(target) || mode.value != InputMode.KEYS) return null
        bridge.finishComposingText()
        val s = bridge.scrubSnapshot(target) ?: return null
        scrub = s
        scrubKept = s.before.length
        scrubLevel = -1
        lastCorrection = null
        autoSpaced = false
        startWordAim(target)
        return s
    }

    override fun onBackspaceScrubTo(level: Int): Int? {
        val s = scrub ?: return null
        val target = backspaceTarget ?: return null
        if (mode.value != InputMode.KEYS) return null
        // Text deleted past the snapshot can't be restored.
        if (s.topIsFinal && scrubLevel == s.top) return s.top
        markOwnEdit()
        if (!bridge.scrubTo(target, s, scrubKept, level)) {
            scrub = null
            return null
        }
        val first = scrubLevel < 0
        scrubKept = s.kept(level)
        scrubLevel = level
        if (!first && settings.current.vibrateOnKeyPress) {
            composeView?.performHapticFeedback(
                if (level == s.top) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK,
            )
        }
        return level
    }

    override fun onBackspaceScrubEnd() {
        endWordAim()
        val s = scrub ?: return
        scrub = null
        updateShift()
        refreshStrip()
        if (scrubKept < s.before.length) AppGraph.gestures.tryEmit(KeyboardGesture.WordsDeleted(wordsFrom(s.before, scrubKept)))
        if (s.top > 0 && scrubLevel == s.top) {
            showNote(if (s.topClearsAll) "All text cleared" else "Deleted everything before the cursor")
        }
    }

    override fun onFanCommand(command: FanCommand) {
        clearPrompt()
        when (command) {
            FanCommand.WORDS -> Unit
            FanCommand.SPACE -> onKey(Key.Space())
            FanCommand.ACTION, FanCommand.NEW_LINE -> {
                clearNote()
                lastCorrection = null
                autoSpaced = false
                markOwnEdit()
                if (command == FanCommand.ACTION) bridge.performFanAction() else bridge.commitText("\n")
                updateShift()
                refreshStrip()
            }
            FanCommand.CLEAR_CLIPBOARD -> clearClipboardHistory()
            FanCommand.UNDO_CLEAR -> undoClearClipboard()
            FanCommand.KEYBOARD_ONCE, FanCommand.NUMBERS_ONCE -> {
                keyboardOnceEditor = bridge.editorKey()
                keyboardOnce.value = true
                when {
                    command == FanCommand.KEYBOARD_ONCE -> keyboard.showHome()
                    keyboard.state.value.pad != null -> keyboard.setLayer(Layer.NUMBERS)
                    else -> keyboard.setLayer(Layer.SYMBOLS)
                }
                updateShift()
                refreshStrip()
            }
        }
    }

    /**
     * Forgets the clip history and the system clipboard, so the current clip doesn't come back when
     * the keyboard reopens. Undo clear, in Clear clips' place, brings all of it back until
     * something new is copied.
     */
    private fun clearClipboardHistory() {
        val primary = try { clipboardManager.primaryClip } catch (_: RuntimeException) { null }
        if (clipChips.value.isEmpty() && primary == null) {
            showNote("Clipboard history is empty")
            return
        }
        forgetClear()
        lastClear = ClearedClips(clipHistory.clear(), primary, clipsClearedAt)
        clipsClearedAt = System.currentTimeMillis()
        try {
            clipboardManager.clearPrimaryClip()
        } catch (_: RuntimeException) {
            // Some devices refuse; the history is cleared anyway.
        }
        fanSpec.value = fanSpec.value.copy(undoClear = true)
        refreshClipChips()
        refreshClipboard()
        showNote("Clipboard history cleared")
    }

    /** Drops the undo for the last clear and its cached images. */
    private fun forgetClear() {
        val cleared = lastClear ?: return
        lastClear = null
        clipHistory.discard(cleared.entries)
        fanSpec.value = fanSpec.value.copy(undoClear = false)
    }

    private fun undoClearClipboard() {
        val cleared = lastClear ?: return
        lastClear = null
        fanSpec.value = fanSpec.value.copy(undoClear = false)
        clipsClearedAt = cleared.clearedAtBefore
        clipHistory.restore(cleared.entries)
        // Only if nothing was copied since; restoring it records it again as the newest clip.
        if (cleared.primary != null) {
            try {
                if (!clipboardManager.hasPrimaryClip()) clipboardManager.setPrimaryClip(cleared.primary)
            } catch (_: RuntimeException) {
                // The history is back either way.
            }
        }
        refreshClipChips()
        refreshClipboard()
        showNote("Clipboard history restored")
    }

    override fun onSelectKeyboard(id: String) {
        if (!inputViewActive) return
        val next = AppGraph.usableKeyboards().firstOrNull { it.id == id } ?: return
        clearPrompt()
        clearNote()
        lastCorrection = null
        if (next == keyboard.state.value.keyboard) return
        keyboard.setKeyboard(next)
        useKeyboard(next)
    }

    /** The globe key or the emoji key's menu switched to [next]: remember it and reload its words. */
    private fun useKeyboard(next: com.rheerkens.androidkeyboard.keyboard.TypingKeyboard) {
        settings.update { it.copy(currentKeyboard = next.id) }
        preloadLanguages()
        refreshStrip()
        // Another keyboard can bring other search languages.
        emojiSearch.value?.takeIf { searchingEmoji }?.let { setEmojiQuery(it.query) }
    }

    override fun onGlobeLongPress() {
        clearPrompt()
        emojiOpen.value = false
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    override fun onSpaceLongPress() = onGlobeLongPress()

    override fun onCloseEmoji() {
        emojiOpen.value = false
        keyboard.showHome()
    }

    override fun onOpenEmojiSearch() {
        if (!inputViewActive || !emojiOpen.value) return
        clearPrompt()
        clearNote()
        // Letters, lower case (also in a number field): emoji names are found whatever the case.
        keyboard.setLayer(Layer.LETTERS)
        keyboard.setAutoShift(false)
        setEmojiQuery("")
    }

    override fun onCloseEmojiSearch() {
        emojiSearch.value = null
        keyboard.showHome()
        // Searching turned auto-capitals off; the editor's sentence start decides again.
        updateShift()
    }

    override fun onClearEmojiSearch() {
        if (searchingEmoji) setEmojiQuery("")
    }

    private fun setEmojiQuery(query: String) {
        val catalog = emojiCatalog.value
        val languages = AppGraph.suggestionLanguages(keyboard.state.value.keyboard).map { it.id }
        emojiSearch.value = EmojiSearchState(query, catalog?.search?.search(query, languages).orEmpty())
    }

    /** The keys type into the emoji search; false for keys that work as usual (Shift, ?123, globe). */
    private fun typeIntoEmojiSearch(key: Key): Boolean {
        val query = emojiSearch.value?.query ?: return false
        when (key) {
            is Key.Char -> {
                if (query.length < MAX_EMOJI_QUERY) setEmojiQuery(query + key.output.lowercase())
                keyboard.onCharTyped()
            }
            is Key.Space -> if (query.isNotBlank() && !query.endsWith(' ')) setEmojiQuery("$query ")
            is Key.Backspace -> deleteFromEmojiSearch()
            // Enter and the emoji key go back to all emoji.
            is Key.Enter, is Key.Emoji -> onCloseEmojiSearch()
            else -> return false
        }
        return true
    }

    private fun deleteFromEmojiSearch() {
        val query = emojiSearch.value?.query ?: return
        if (query.isEmpty()) return
        val chars = java.text.BreakIterator.getCharacterInstance().apply { setText(query) }
        setEmojiQuery(query.substring(0, chars.preceding(query.length).coerceAtLeast(0)))
    }

    override fun onChooseEmoji(value: String) {
        if (!inputViewActive || !emojiOpen.value || emojiCatalog.value?.find(value) == null) return
        clearPrompt()
        clearNote()
        lastCorrection = null
        typeChar(value)
        if (!bridge.isSensitiveField) {
            settings.update { it.copy(recentEmoji = EmojiHistory.record(it.recentEmoji, value)) }
        }
    }

    override fun onOpenKeyboardSettings() {
        emojiOpen.value = false
        openCompanion(Destination.LANGUAGES)
    }

    override fun onOpenSettings() {
        emojiOpen.value = false
        openCompanion(Destination.SETTINGS)
    }

    override fun onSuggestion(suggestion: Suggestion) {
        doubleSpacePeriod.reset()
        if (suggestion.emojiName != null) {
            if (!inputViewActive || bridge.isSensitiveField || !settings.current.suggestionStrip) return
            val available = EmojiSuggestions.emoticon(bridge.textBeforeCursor(8), emojiCatalog.value)
                ?: EmojiSuggestions.suggest(bridge.currentWordBeforeCursor(),
                    AppGraph.suggestionLanguages(keyboard.state.value.keyboard).map { it.id }, emojiCatalog.value)
            if (available != suggestion) return
        }
        val emoticon = suggestion.emojiName != null && suggestion.replaceChars > 0
        // Replacing before the cursor would leave a selection standing.
        if (emoticon && bridge.hasSelection()) return
        clearNote()
        lastCorrection = null
        markOwnEdit()
        val typed = bridge.currentWordBeforeCursor()
        val phrase = if (!emoticon && suggestion.replaceChars > 0) {
            savedPhrase(suggester(), dictionary.state.value.terms)
        } else {
            null
        }
        if (emoticon) {
            // An emoticon becomes its emoji, a word of its own ("you;)" gives "you 😉").
            val n = suggestion.replaceChars
            val lead = if (bridge.textBeforeCursor(n + 1).let { it.length > n && !it[0].isWhitespace() }) " " else ""
            bridge.replaceBeforeCursor(n, lead + suggestion.text + " ")
        } else if (phrase != null && phrase == suggestion.replaceChars to suggestion.text) {
            bridge.replaceBeforeCursor(phrase.first, phrase.second + " ")
        } else if (typed.isEmpty()) {
            val before = bridge.textBeforeCursor(1)
            val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
            bridge.commitText(lead + suggestion.text + " ")
        } else {
            bridge.replaceCurrentWord(suggestion.text + " ")
        }
        autoSpaced = true
        if (suggestion.emojiName != null) {
            settings.update { it.copy(recentEmoji = EmojiHistory.record(it.recentEmoji, suggestion.text)) }
        } else {
            learnLastWord()
        }
        keyboard.leaveSymbols()
        keyboard.onCharTyped()
        updateShift()
        refreshStrip()
    }

    override fun onPasteClipboard(content: ClipboardContent) {
        if (!inputViewActive || mode.value != InputMode.KEYS || bridge.isSensitiveField ||
            !settings.current.suggestionStrip || bridge.currentWordBeforeCursor().isNotEmpty() ||
            strip.value.clipboard != content) return
        val target = bridge.captureTarget() ?: return
        clearNote()
        clearPrompt()
        lastCorrection = null
        autoSpaced = false
        markOwnEdit()
        val pasted = try {
            when (content) {
                is ClipboardContent.Text -> bridge.pasteText(target, content.text)
                is ClipboardContent.Image -> {
                    if (inAiPrompt.value) {
                        // Kept out of the history, so not sent to the AI either.
                        if (content.sensitive) showNote("Sensitive images aren't added to the prompt", warn = true)
                        else attachPromptImage(content.uri)
                        return
                    }
                    if (!bridge.acceptsImage(content.mimeType)) {
                        showNote("This field doesn't accept images", warn = true)
                        return
                    }
                    bridge.pasteImage(target, content.uri, content.mimeType)
                }
            }
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!pasted) showNote("This clip couldn't be pasted", warn = true)
        updateShift()
        refreshStrip()
    }

    override fun onPasteHistory(entry: ClipEntry) {
        if (!inputViewActive || bridge.isSensitiveField || clipChips.value.none { it.entry == entry }) return
        // In the prompt window an image goes with the prompt, like a picture added to a chat message.
        if (inAiPrompt.value && entry !is ClipEntry.Text) {
            addPromptImage(entry)
            return
        }
        val target = bridge.captureTarget() ?: return
        if (entry is ClipEntry.Screenshot) {
            // Paste a copy kept in the history: the editor gets a read grant on this app's file,
            // not on the media library.
            scope.launch {
                val image = withContext(Dispatchers.IO) { clipHistory.recordScreenshot(entry) }
                if (image != null && bridge.isCurrent(target)) pasteClip(target, image)
                else if (image == null) showNote("This screenshot couldn't be pasted", warn = true)
            }
            return
        }
        pasteClip(target, entry)
    }

    override fun loadPreview(entry: ClipEntry): Bitmap? = when (entry) {
        is ClipEntry.Text -> null
        is ClipEntry.Image -> loadThumbnail(contentResolver, entry.uri, PREVIEW_PX)
        is ClipEntry.Screenshot -> loadThumbnail(contentResolver, entry.mediaUri, PREVIEW_PX)
    }

    override fun onEditClip(entry: ClipEntry) {
        val uri = when (entry) {
            is ClipEntry.Text -> return
            is ClipEntry.Image -> entry.uri
            is ClipEntry.Screenshot -> entry.mediaUri
        }
        pickerOpen.value = false
        voice.cancel(CancelReason.KEYBOARD_HIDDEN)
        // Attach in the editor inserts the result back into this field (see onStartInput).
        PendingAttachment.sourceEditor = bridge.editorKey()
        requestHideSelf(0)
        startActivity(
            Intent(this, ImageEditorActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    private fun pasteClip(target: EditorBridge.Target, entry: ClipEntry) {
        lastCorrection = null
        autoSpaced = false
        markOwnEdit()
        clipHistory.markUsed(entry)
        val pasted = try {
            when (entry) {
                is ClipEntry.Text -> bridge.pasteText(target, entry.text)
                is ClipEntry.Image -> bridge.pasteImage(target, entry.uri, entry.mimeType)
                is ClipEntry.Screenshot -> false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!pasted) showNote("This clip couldn't be pasted", warn = true)
        updateShift()
        refreshStrip()
    }

    override fun onSuggestionLongPress(suggestion: Suggestion) {
        if (suggestion.emojiName != null) return
        if (dictionary.contains(suggestion.text)) {
            showNote("“${suggestion.text}” is already in your dictionary")
        } else {
            strip.value = strip.value.copy(addPrompt = suggestion.text, note = null)
        }
    }

    override fun onAddToDictionary(word: String) {
        val msg = when (dictionary.addTerm(word)) {
            is DictionaryStore.AddResult.Added -> "Saved “$word” to your dictionary"
            DictionaryStore.AddResult.AlreadyExists -> "“$word” is already in your dictionary"
            DictionaryStore.AddResult.Invalid -> "Can't save that word"
        }
        strip.value = strip.value.copy(addPrompt = null)
        showNote(msg)
    }

    override fun onDismissStripPrompt() = clearPrompt()

    override fun onToggleMode() {
        clearPrompt()
        pickerOpen.value = false
        emojiOpen.value = false
        if (mode.value == InputMode.KEYS) {
            if (bridge.isSensitiveField) {
                showNote(SENSITIVE_NOTE, warn = true)
                return
            }
            setMode(InputMode.VOICE)
            voice.clearOutcome()
            voice.refreshReadiness()
            if (settings.current.keyboardFirstDictation) onTapToTalk()
            else if (settings.current.autoStartVoice) autoStartVoice()
        } else {
            val outcome = voice.cancel(CancelReason.SWITCHED_TO_KEYS)
            voice.clearOutcome()
            setMode(InputMode.KEYS)
            if (outcome != null) showVoiceOutcomeNote(outcome)
            updateShift()
            refreshStrip()
        }
    }

    override fun onSetMinimized(minimized: Boolean) {
        keyboardOnce.value = false
        emojiOpen.value = false
        clearPrompt()
        clearNote()
        lastCorrection = null
        settings.update {
            val next = if (physicalKeyboard.value) it.copy(minimizedWithPhysicalKeyboard = minimized)
            else it.copy(minimizedWithoutPhysicalKeyboard = minimized)
            if (minimized) next else next.copy(compactExpandHintSeen = true)
        }
        if (!minimized) {
            updateShift()
            refreshStrip()
        }
    }

    override fun onOpenDictionary() = openCompanion(Destination.DICTIONARY)
    override fun onOpenVoiceSettings() = openCompanion(Destination.SETTINGS_VOICE)
    override fun onOpenModels() = openCompanion(Destination.SPEECH_MODELS)
    override fun onOpenSetup() = openCompanion(Destination.HOME)

    override fun onDismissExpandHint() {
        if (!settings.current.compactExpandHintSeen) settings.update { it.copy(compactExpandHintSeen = true) }
    }

    override fun onOpenVoicePicker() {
        if (bridge.isSensitiveField) {
            showNote(SENSITIVE_NOTE, warn = true)
            return
        }
        clearPrompt()
        emojiOpen.value = false
        setMode(InputMode.VOICE)
        voice.refreshReadiness()
        if (!pickerOpen.value) onTogglePicker()
    }

    override fun onStop() {
        voice.stop()
    }

    override fun onTapToTalk() {
        pickerOpen.value = false
        // Without a model, Talk opens the picker so the user can choose or download one.
        if (voice.state.value == VoiceState.NoModel) {
            onTogglePicker()
        } else {
            // A manual Talk retries a model that crashed last time.
            AppGraph.crashLog.clearSuspect()
            voice.start()
        }
    }

    // ---- Model picker ----

    override fun onTogglePicker() {
        if (pickerOpen.value) {
            pickerOpen.value = false
            return
        }
        // Opening the picker cancels an active capture, like switching to keys.
        voice.cancel(CancelReason.MODEL_PICKER)
        pickerOpen.value = true
    }

    override fun onClosePicker() {
        pickerOpen.value = false
    }

    override fun onSelectModel(id: String) {
        pickerOpen.value = false
        voice.cancel(CancelReason.MODEL_CHANGED)
        if (!AppGraph.models.setActive(id)) return
        // Already handled here; keep the store observer from cancelling the new capture.
        observedActiveId = id
        voice.clearOutcome()
        voice.refreshReadiness()
        if (settings.current.autoStartVoice) autoStartVoice()
    }

    override fun onDownloadModel(id: String) = AppGraph.models.download(id)

    override fun onCancelDownload(id: String) = AppGraph.models.cancelDownload(id)

    /**
     * While the picker is open, Back closes it instead of hiding the keyboard. On API 33+ the
     * system routes Back through the IME window's OnBackInvokedDispatcher (and may animate the
     * IME away without calling onKeyDown), so register a callback there; older versions use
     * the key events below.
     */
    private var emojiBackCallback: Any? = null

    /** Back closes emoji instead of the keyboard (Android 13+, like the model picker). */
    private fun updateEmojiBack(open: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = window.onBackInvokedDispatcher
        val current = emojiBackCallback as OnBackInvokedCallback?
        if (open && current == null) {
            // Back leaves a search for all emoji first, then closes emoji.
            val cb = OnBackInvokedCallback { if (searchingEmoji) onCloseEmojiSearch() else emojiOpen.value = false }
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            emojiBackCallback = cb
        } else if (!open && current != null) {
            dispatcher.unregisterOnBackInvokedCallback(current)
            emojiBackCallback = null
        }
    }

    private fun updatePickerBack(open: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = window.onBackInvokedDispatcher
        val current = pickerBackCallback as OnBackInvokedCallback?
        if (open && current == null) {
            val cb = OnBackInvokedCallback {
                pickerOpen.value = false
            }
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            pickerBackCallback = cb
        } else if (!open && current != null) {
            dispatcher.unregisterOnBackInvokedCallback(current)
            pickerBackCallback = null
        }
    }

    /** Back closes our panels before it closes the keyboard; Android owns its keyboard picker. */
    private fun backClosesPanel() =
        (pickerOpen.value || emojiOpen.value || layoutBackHandler != null || menuBackHandler != null) && isInputViewShown

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && backClosesPanel()) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && backClosesPanel()) {
            if (!event.isCanceled) {
                menuBackHandler?.let { it(); return true }
                layoutBackHandler?.invoke()
                pickerOpen.value = false
                if (searchingEmoji) onCloseEmojiSearch() else emojiOpen.value = false
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ---- Typing helpers ----

    private fun typeChar(output: String) {
        doubleSpacePeriod.reset()
        markOwnEdit()
        val punctuation = output.length == 1 && output[0] in ".,!?;:"
        if (punctuation && autoSpaced && bridge.textBeforeCursor(1) == " ") {
            // "word |" + "." → "word. |"
            bridge.deleteBackward()
            bridge.commitText("$output ")
        } else if (punctuation) {
            commitWordBoundary(output)
        } else {
            val text = keyboard.applyCase(output)
            expectEcho(text.length)
            bridge.commitText(text)
        }
        if (punctuation) learnLastWord()
        autoSpaced = false
        keyboard.onCharTyped()
        // One read of the text before the cursor serves both Shift and the strip.
        val snapshot = bridge.readContext()
        updateShift(snapshot.before)
        refreshStrip(snapshot)
    }

    /**
     * Where the editor will report the cursor after each letter just typed, oldest first. Its
     * report (the selection echo) then carries nothing new, so it reads nothing.
     */
    private val expectedEchoes = ArrayDeque<Int>()

    /** A letter of [length] chars is about to be typed at the cursor. */
    private fun expectEcho(length: Int) {
        val base = expectedEchoes.lastOrNull() ?: selStart.takeIf { it >= 0 && it == selEnd }
        if (base == null) expectedEchoes.clear() else expectedEchoes.addLast(base + length)
    }

    private fun typeSpace() {
        markOwnEdit()
        val shortcuts = bridge.allowsTextShortcuts && !bridge.hasSelection()
        if (!shortcuts) doubleSpacePeriod.reset()
        if (shortcuts && doubleSpacePeriod.onSpace(bridge.textBeforeCursor(DoubleSpacePeriod.CONTEXT_LIMIT), SystemClock.uptimeMillis())) {
            bridge.replaceBeforeCursor(1, ". ")
            val cursor = bridge.currentSelection()?.takeIf { it.first == it.second }?.first
            lastCorrection = cursor?.let { Undo(" ", ".", " ", bridge.editorKey(), it) }
        } else {
            commitWordBoundary(" ")
            if (shortcuts) doubleSpacePeriod.spaceCommitted(bridge.textBeforeCursor(DoubleSpacePeriod.CONTEXT_LIMIT))
        }
        learnLastWord()
        autoSpaced = false
        keyboard.leaveSymbols()
        updateShift()
        refreshStrip()
    }

    /** Correct the completed word and append Space or punctuation, keeping one-step undo. */
    private fun commitWordBoundary(separator: String) {
        val s = settings.current
        if (s.autocorrect && !bridge.isSensitiveField) {
            val terms = dictionary.state.value.terms
            val snapshot = bridge.readContext()
            val phrase = savedPhrase(suggester(), terms, snapshot.before)
            val word = snapshot.word
            val key = correctionKey(snapshot, suggestionKey(snapshot))
            val fix = when {
                phrase != null || key == null -> null
                cachedCorrection?.key == key -> cachedCorrection?.fix
                // A fast Space must not run a dictionary scan on the input thread. On a cache
                // miss apply only what lookups settle (`dont` → `don't`), else keep the literal
                // word; never correct it after later edits.
                else -> suggester().leading(key.lead).quickAutocorrection(word, terms, key.atSentenceStart)
            }
            var applied: Pair<String, String>? = null
            if (phrase != null) {
                val original = snapshot.before.takeLast(phrase.first)
                bridge.replaceBeforeCursor(phrase.first, phrase.second)
                applied = original to phrase.second
            } else if (fix != null) {
                bridge.replaceBeforeCursor(word.length, fix)
                applied = word to fix
            }
            bridge.commitText(separator)
            if (applied != null) {
                // Undo only where the editor reports an exact cursor; otherwise a matching word
                // elsewhere could be changed.
                val cursor = bridge.currentSelection()?.takeIf { it.first == it.second }?.first
                lastCorrection = cursor?.let { Undo(applied.first, applied.second, separator, bridge.editorKey(), it) }
                showNote(if (cursor != null) "Autocorrected to “${applied.second}” · Backspace to undo" else "Autocorrected to “${applied.second}”")
            }
        } else {
            bridge.commitText(separator)
        }
    }

    /**
     * The key to autocorrect the word before the cursor, or null when it must be left alone:
     * nothing typed, a word the user un-corrected, or part of a URL, email, path, or hashtag.
     */
    private fun correctionKey(snapshot: EditorContext, context: CorrectionKey): CorrectionKey? {
        val word = snapshot.word
        if (word.isEmpty() || rejected(word)) return null
        if (!Corrector.isStandaloneWord(snapshot.token, word)) return null
        return context
    }

    private fun suggestionKey(snapshot: EditorContext): CorrectionKey {
        val current = keyboard.state.value.keyboard
        return CorrectionKey(snapshot.word, current.id, snapshot.wordStartsSentence, AppGraph.lexiconLoads.value,
            AppGraph.suggestionLanguages(current).map { it.id }, dictionary.state.value.terms,
            lead = suggester().contextLanguage(snapshot.before))
    }

    /** The saved multi-word term to recase before the cursor, unless the user undid it here. */
    private fun savedPhrase(suggester: Suggester, terms: List<PersonalTerm>,
        before: String = bridge.textBeforeCursor(MAX_TERM_CHARS)): Pair<Int, String>? =
        suggester.protectedPhrase(before.takeLast(MAX_TERM_CHARS), terms)
            ?.takeIf { !rejected(before.takeLast(it.first)) }

    /** The user undid this word's autocorrection: in this session, or ever when learning is on. */
    private fun rejected(word: String): Boolean = word.lowercase() in rejectedCorrections ||
        (settings.current.learnFromTyping && AppGraph.personalModel.model()?.isRejected(word) == true)

    /** The learned model, when learning is on and this field is ordinary text; else null. */
    private fun learnedModel(): WordContextModel? =
        if (settings.current.learnFromTyping && bridge.allowsTextShortcuts) AppGraph.personalModel.model() else null

    /** Where the last learned word ended, so a second boundary after it (`word.` then Space) adds nothing. */
    private var lastLearned: String? = null

    /** Teaches the learned model the word just completed before the cursor, with the words before it. */
    private fun learnLastWord() {
        val model = learnedModel() ?: return
        val before = bridge.textBeforeCursor(PersonalPredictions.CONTEXT_CHARS)
        val (previous, word) = PersonalPredictions.justTyped(before) ?: return
        val trailing = before.length - 1 - before.indexOfLast { it.isLetter() || it == '\'' || it == '’' }
        val end = bridge.currentSelection()?.first?.minus(trailing) ?: return
        val at = "${bridge.editorKey()}|$end|$word"
        if (at == lastLearned) return
        lastLearned = at
        model.learn(previous, word)
        AppGraph.personalModel.changed()
    }

    /** An undone autocorrection: forget the learned [corrected] word, and never correct [original] again. */
    private fun unlearnCorrection(original: String, corrected: String) {
        if (!settings.current.learnFromTyping) return
        val model = AppGraph.personalModel.model() ?: return
        model.reject(original)
        if (learnedModel() != null) {
            model.unlearn(PersonalPredictions.sentenceWords(bridge.textBeforeCursor(PersonalPredictions.CONTEXT_CHARS)).takeLast(2), corrected)
        }
        lastLearned = null
        AppGraph.personalModel.changed()
    }

    /** [before]: the text before the cursor if just read, to spare the editor another call. */
    private fun updateShift(before: String? = null) {
        // The search stays lower case; Shift follows the editor again when it closes.
        if (searchingEmoji) return
        keyboard.setAutoShift(settings.current.autoCapitalize && bridge.capsModeAtCursor(before))
    }

    /** Recomputes suggestion slots. Notes stay until they expire or the user types. */
    private fun refreshStrip(read: EditorContext? = null) {
        if (mode.value != InputMode.KEYS || isMinimized()) return
        val snapshot = read ?: bridge.readContext()
        val typed = snapshot.word
        if (typed.isEmpty() || bridge.isSensitiveField) {
            invalidateStrip()
            cachedCorrection = null
            val selection = typed.isEmpty() && !bridge.isSensitiveField && settings.current.suggestionStrip && bridge.hasSelection()
            val predictions = if (typed.isEmpty() && bridge.allowsTextShortcuts &&
                settings.current.suggestionStrip && !selection) {
                PersonalPredictions.nextWords(bridge.textBeforeCursor(NextWordPredictor.CONTEXT_LIMIT), dictionary.state.value.terms,
                    learnedModel(), capitalize = keyboard.state.value.shift != ShiftState.OFF)
            } else emptyList()
            // An emoticon just typed (";)") offers its emoji on the right, to replace it.
            val emoticon = if (typed.isEmpty() && !bridge.isSensitiveField && settings.current.suggestionStrip &&
                !selection) EmojiSuggestions.emoticon(snapshot.before, emojiCatalog.value) else null
            val slots = Suggester.arrangeForStrip("", predictions)
            val image = strip.value.clipboard as? ClipboardContent.Image
            strip.value = strip.value.copy(
                slots = if (emoticon != null) slots.take(2) + emoticon else slots,
                typingWord = predictions.isNotEmpty() || emoticon != null,
                imagePasteSupported = image != null && takesImage(image.mimeType),
            )
            return
        }
        val context = suggestionKey(snapshot)
        val correcting = settings.current.autocorrect && !bridge.isSensitiveField
        val request = StripRequest(context, snapshot.before, correcting, settings.current.suggestionStrip,
            emojiCatalog.value, inputSession.value)
        // The editor's selection echo usually reports the exact request already in flight.
        if (stripRequest == request) return
        invalidateStrip()
        stripRequest = request
        val generation = stripGeneration
        // Hide the clipboard immediately, but keep word chips mounted while recomputing.
        // Removing them here cancels a finger press even if the result is unchanged.
        strip.value = strip.value.copy(typingWord = true)
        val terms = dictionary.state.value.terms
        // The language written in leads, whichever language the keyboard lists first.
        val suggester = suggester().leading(context.lead)
        val session = inputSession.value
        // ":D" ends in a letter, so it is a word too: its emoji still beats a guess from "D".
        val emoji = EmojiSuggestions.emoticon(snapshot.before, emojiCatalog.value)
            ?: EmojiSuggestions.suggest(typed, context.languages, emojiCatalog.value)
        val learned = learnedModel()
        // Space recases a saved multi-word term first (see typeSpace); show that in the middle.
        val phrase = if (correcting) savedPhrase(suggester, terms, snapshot.before) else null
        val key = if (correcting && phrase == null) correctionKey(snapshot, context) else null
        // Spelling search over the word list takes tens of milliseconds on a phone: run it off
        // the main thread, keep only the latest result, and cache the Space-bar autocorrection.
        stripJob = scope.launch {
            val fix = withContext(Dispatchers.Default) {
                key?.let { suggester.autocorrection(typed, terms, it.atSentenceStart) { ensureActive() } }
            }
            if (inputSession.value != session || stripGeneration != generation) return@launch
            if (key != null) cachedCorrection = CachedCorrection(key, fix)
            val ranked = withContext(Dispatchers.Default) {
                if (request.enabled) {
                    PersonalPredictions.rank(typed, suggester.suggest(typed, terms, checkActive = { ensureActive() }), snapshot.before, learned)
                } else emptyList()
            }
            if (inputSession.value != session || stripGeneration != generation) return@launch
            val middle = phrase?.let { Suggestion(it.second, isPersonal = true, replaceChars = it.first) } ?: fix?.let { Suggestion(it) }
            strip.value = strip.value.copy(slots = Suggester.arrangeForStrip(typed, ranked, middle, emoji))
        }
    }

    private fun refreshClipboard() {
        clipboardJob?.cancel()
        strip.value = strip.value.copy(clipboard = null, imagePasteSupported = false)
        if (!inputViewActive || mode.value != InputMode.KEYS || bridge.isSensitiveField ||
            !settings.current.suggestionStrip) return
        val target = bridge.captureTarget() ?: return
        clipboardJob = scope.launch {
            val content = withContext(Dispatchers.IO) { readClip() }?.takeUnless { clipExpired(it.copiedAtMs) }
            if (!inputViewActive || !bridge.isCurrent(target) || bridge.isSensitiveField) return@launch
            strip.value = strip.value.copy(
                clipboard = content,
                imagePasteSupported = content is ClipboardContent.Image && takesImage(content.mimeType),
            )
        }
    }

    /** A clip copied at [copiedAtMs] is older than the history keeps clips. */
    private fun clipExpired(copiedAtMs: Long): Boolean {
        val age = settings.current.clipExpiry.millis ?: return false
        return copiedAtMs > 0 && copiedAtMs <= System.currentTimeMillis() - age
    }

    /** Forgets expired clips now, and again each time the next one expires. */
    private fun scheduleClipPrune() {
        clipPruneJob?.cancel()
        clipPruneJob = scope.launch {
            while (true) {
                val next = withContext(Dispatchers.IO) { clipHistory.prune() } ?: break
                delay((next - System.currentTimeMillis()).coerceAtLeast(0) + CLIP_PRUNE_SLACK_MS)
            }
        }
    }

    /** The current system clip, marked sensitive if it was copied in a password or private field. Call on IO. */
    private fun readClip(): ClipboardContent? {
        val content = try { clipboardReader.read(clipboardManager.primaryClip) } catch (_: RuntimeException) { null }
        return if (content != null && privateClipKey(content) in privateClips) content.asSensitive() else content
    }

    private fun privateClipKey(content: ClipboardContent) = "${content.copiedAtMs}:" + when (content) {
        is ClipboardContent.Text -> content.text.hashCode()
        is ClipboardContent.Image -> content.uri
    }

    /**
     * Adds the current system clip to the history (also while the keyboard is hidden). A clip
     * that [changed] while a password or private field has focus is remembered as private instead,
     * so it stays out of the history when a normal field opens later.
     */
    private fun recordClipboard(changed: Boolean) {
        val privateFocus = currentInputStarted && EditorBridge.isSensitive(currentInputEditorInfo)
        if (privateFocus && !changed) return
        scope.launch(Dispatchers.IO) {
            val content = readClip() ?: return@launch
            if (privateFocus) {
                if (privateClips.size >= MAX_PRIVATE_CLIPS) privateClips.clear()
                privateClips += privateClipKey(content)
            } else {
                clipHistory.record(content)
            }
            // Something new on the clipboard: Clear clips takes Undo clear's place again.
            withContext(Dispatchers.Main) { forgetClear() }
        }
    }

    private fun refreshScreenshots() {
        screenshotJob?.cancel()
        if (!settings.current.recentScreenshots || !screenshots.hasPermission()) {
            recentShots.value = emptyList()
            return
        }
        screenshotJob = scope.launch {
            recentShots.value = withContext(Dispatchers.IO) { screenshots.query(System.currentTimeMillis()) }
        }
    }

    private fun refreshClipChips() {
        if (bridge.isSensitiveField) {
            clipChips.value = emptyList()
            return
        }
        val shots = recentShots.value.filter { it.timeMs > clipsClearedAt }
        val merged = ClipboardHistory.merge(clipHistory.current(), shots, MAX_CLIP_CHIPS)
        recentImagesFlow.value = merged.filterNot { it is ClipEntry.Text }
        clipChips.value = merged.map {
            val mime = when (it) {
                is ClipEntry.Text -> null
                is ClipEntry.Image -> it.mimeType
                is ClipEntry.Screenshot -> it.mimeType
            }
            ClipChip(it, mime == null || takesImage(mime))
        }
    }

    // ---- AI actions (Settings › Experimental) ----

    /** ✨ opens the prompt window just above the keyboard. */
    override fun onAiButton() {
        if (!inputViewActive) return
        openAiComposer()
    }

    /**
     * The prompt window ([AiPromptActivity]) just above the keyboard: a real text box with the focus,
     * so the keys (and other voice tools) type into it like into any app, and the app below stays where
     * it is. Its image is for the field it was opened from.
     */
    /** [fresh]: an empty prompt, leaving this field's saved draft for the next ✨. */
    private fun openAiComposer(fresh: Boolean = false): Boolean {
        if (!settings.current.aiKeyboard || bridge.isSensitiveField || inAiPrompt.value) return false
        emojiOpen.value = false
        pickerOpen.value = false
        clearPrompt()
        clearNote()
        lastCorrection = null
        clearAiCards()
        // A prompt that failed, was stopped or was closed unsent comes back, in its own field only.
        // Text selected in the chat is not taken: the prompt is the user's own, typed there.
        // Another field's words stay saved for that field.
        val editor = bridge.editorKey()
        val retry = if (fresh) null else retryPrompt?.takeIf { it.editor == editor }
        if (retry != null) retryPrompt = null
        composerEditor = editor
        pendingAiCard = null
        promptImagesEpoch++
        promptImagesLoading = 0
        promptImagesFlow.value = retry?.images.orEmpty()
        val session = ++composerSession
        aiPromptImproving.value = false
        promptStateFromWindow = false
        return try {
            startActivity(AiPromptActivity.intent(this, retry?.text.orEmpty().take(ImageApi.MAX_PROMPT_CHARS), session))
            true
        } catch (_: RuntimeException) {
            if (retry != null) retryPrompt = retry
            promptImagesFlow.value = emptyList()
            showNote("Couldn't open the prompt", warn = true)
            false
        }
    }

    /** In the prompt window every image chip can be added to the prompt; elsewhere the app must take images. */
    private fun takesImage(mimeType: String): Boolean = inAiPrompt.value || bridge.acceptsImage(mimeType)

    /** Room left for pictures in the prompt, counting those still being read. */
    override fun promptImageRoom(): Int = ImageApi.MAX_INPUT_IMAGES - promptImagesFlow.value.size - promptImagesLoading

    /**
     * Reads [uri] into a picture for the prompt, shrunk for upload; a note says why when it can't.
     * False when there was no room for it.
     */
    private fun attachPromptImage(uri: Uri): Boolean {
        if (promptImageRoom() <= 0) {
            showNote("Up to ${ImageApi.MAX_INPUT_IMAGES} pictures per prompt", warn = true)
            return false
        }
        val epoch = promptImagesEpoch
        val id = ++nextPromptImage
        promptImagesLoading++
        scope.launch {
            val image = try {
                withContext(Dispatchers.IO) { InputImages.prepare(contentResolver, uri, id) }
            } finally {
                if (epoch == promptImagesEpoch) promptImagesLoading--
            }
            // Sent or closed meanwhile: it was for an older prompt.
            if (epoch != promptImagesEpoch) return@launch
            // In the order they were added (ids count up), however long each took to read.
            if (image == null) showNote("This picture couldn't be added", warn = true)
            else promptImagesFlow.value = (promptImagesFlow.value + image).sortedBy { it.id }
        }
        return true
    }

    // ---- The prompt window (AiComposerHost) ----

    override val runningImages: StateFlow<List<AiTask>> get() = aiTasks
    override val promptImages: StateFlow<List<PromptImage>> get() = promptImagesFlow
    override val recentImages: StateFlow<List<ClipEntry>> get() = recentImagesFlow

    override fun addPromptImage(entry: ClipEntry) {
        val added = when (entry) {
            is ClipEntry.Text -> false
            is ClipEntry.Image -> attachPromptImage(entry.uri)
            is ClipEntry.Screenshot -> attachPromptImage(entry.mediaUri)
        }
        if (added) clipHistory.markUsed(entry)
    }

    override fun addPromptPhotos(uris: List<Uri>, session: Int) {
        if (session != composerSession) return
        val room = promptImageRoom()
        uris.take(room).forEach(::attachPromptImage)
        if (uris.size > room) showNote("Up to ${ImageApi.MAX_INPUT_IMAGES} pictures per prompt", warn = true)
    }

    override fun removePromptImage(id: Long) {
        promptImagesFlow.value = promptImagesFlow.value.filterNot { it.id == id }
    }

    override fun promptState(empty: Boolean, improving: Boolean, session: Int) {
        // A window the keyboard has since replaced changes nothing.
        if (session != composerSession) return
        // The window sees all its words; the keyboard sees only a few around the cursor.
        promptStateFromWindow = true
        aiPromptEmpty.value = empty
        aiPromptImproving.value = improving
    }

    /** Create in the prompt window: starts the image for the field the window was opened from. */
    override fun submitImagePrompt(prompt: String, session: Int): String? {
        if (session != composerSession) return "Open the prompt again with ✨"
        val text = prompt.trim()
        if (text.isEmpty()) return "Describe the image first"
        LocalBlocklist.check(text, ContentSafetyPolicy.strict(settings.current.aiSafetyStrict), SafetyStage.INPUT)
            ?.let { return it.message }
        if (!settings.current.aiKeyboard) return "AI on the keyboard is off"
        if (aiTasks.value.size >= MAX_AI_TASKS) return "Wait until one finishes"
        if (promptImagesLoading > 0) return "Wait a moment, a picture is still being added"
        val images = promptImagesFlow.value
        val check = imageJob(settings.current, text, AppGraph.secrets::get, allowLocalHttp = BuildConfig.DEBUG, images.map { it.image })
        val job = check.job ?: return check.problem ?: "AI isn't set up"
        // The wand's window didn't take the field's saved draft, so that stays.
        if (session != wandSession) retryPrompt = null
        val editor = composerEditor
        promptImagesEpoch++
        promptImagesFlow.value = emptyList()
        startImageTask(text, job, editor, images)
        // Back in the app's field, the strip says where the image is being made.
        pendingAiCard = PendingAiCard(editor, note = "Creating your image · tap the ring for progress")
        return null
    }

    override fun stopImage(id: Long) {
        stopAiTask(id)
    }

    /** The window closed without Create: its words come back with the next prompt for the same field. */
    override fun composerClosed(draft: String, session: Int) {
        if (session != composerSession) return
        val images = promptImagesFlow.value
        promptImagesEpoch++
        promptImagesLoading = 0
        promptImagesFlow.value = emptyList()
        // The wand's image alone isn't a draft: it is still in the clipboard row, and the saved one stays.
        if (session == wandSession && draft.isBlank() && images.all { it.id == wandImage }) return
        if (draft.isNotBlank() || images.isNotEmpty()) retryPrompt = RetryPrompt(composerEditor, draft.trim(), images)
    }

    /** A card or note that waited for the prompt window to close, in the field it is about only. */
    private fun showPendingAiCard() {
        val pending = pendingAiCard ?: return
        if (inAiPrompt.value) return
        pendingAiCard = null
        if (pending.editor == null || pending.editor != bridge.editorKey() || bridge.isSensitiveField) return
        val ready = pending.ready?.let { it.copy(pasteable = bridge.acceptsImage(it.entry.mimeType)) }
        if (ready != null || pending.failure != null) showAiCard(ready, pending.failure)
        else if (pending.note != null && aiTasks.value.isNotEmpty()) showNote(pending.note)
    }

    override fun onInsertAiResult(key: String) {
        // The card the tap was on: a newer image that replaced it meanwhile isn't inserted.
        val ready = aiReady.value?.takeIf { it.entry.key == key } ?: return
        clearAiCards()
        if (!inputViewActive || bridge.isSensitiveField) return
        // The history's own entry: putting the image on the system clipboard refreshed it.
        val entry = clipHistory.current().firstOrNull { it.key == ready.entry.key } as? ClipEntry.Image
        if (entry == null) {
            showNote("This image is no longer kept", warn = true)
            return
        }
        if (!bridge.acceptsImage(entry.mimeType)) {
            showNote("Copied · touch and hold the text box, then tap Paste", warn = true)
            return
        }
        val target = bridge.captureTarget() ?: return
        pasteClip(target, entry)
        updateShift()
        refreshStrip()
    }

    /** ✎ on a ready image: the prompt window opens with that image added, to say what to change. */
    override fun onEditAiResult(key: String) {
        val ready = aiReady.value?.takeIf { it.entry.key == key } ?: return
        if (!inputViewActive || bridge.isSensitiveField) return
        val entry = clipHistory.current().firstOrNull { it.key == ready.entry.key } as? ClipEntry.Image
        if (entry == null) {
            clearAiCards()
            showNote("This image is no longer kept", warn = true)
            return
        }
        clearAiCards()
        // Just this image: a saved draft for this field (words, other pictures) stays for the next ✨.
        if (openAiComposer(fresh = true)) {
            wandSession = composerSession
            if (attachPromptImage(entry.uri)) wandImage = nextPromptImage
        }
    }

    /** Flag on a ready image: the report screen. The card comes back if that screen is cancelled. */
    override fun onReportAiResult(key: String) {
        val ready = aiReady.value?.takeIf { it.entry.key == key } ?: return
        val entry = clipHistory.current().firstOrNull { it.key == key } as? ClipEntry.Image
        if (entry == null) {
            showNote("This image is no longer kept", warn = true)
            return
        }
        reportHold = ReportHold(entry, ready, bridge.editorKey())
        reportNote = null
        AiReportLink.reporting = true
        try {
            startActivity(AiReportActivity.imageIntent(this, entry.key, entry.file.absolutePath, entry.mimeType, ready.prompt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) {
            AiReportLink.reporting = false
            reportHold = null
            showNote("Couldn't open the report", warn = true)
        }
    }

    /**
     * The report was filed. Removes the image from the clipboard history and, when it is still the
     * system clip, from there too. Returns whether a Pictures/Lowkey copy may still be there.
     */
    private fun discardReportedImage(clipKey: String): Boolean {
        reportHold = null
        clearAiCards()
        val entry = clipHistory.current().firstOrNull { it.key == clipKey } as? ClipEntry.Image
        clipHistory.remove(clipKey)
        if (entry != null) clearSystemClipIf(entry.uri)
        val leftover = entry != null && settings.current.saveImagesToGallery &&
            deleteCreatedImage(this, entry.timeMs, entry.mimeType) == GalleryDelete.FAILED
        reportNote = if (leftover) {
            "Reported and removed. A copy in Pictures/Lowkey may still be there."
        } else {
            "Reported and removed"
        }
        return leftover
    }

    /** Puts the card back after a cancelled report, or shows the note after one that was filed. */
    private fun restoreReportHold() {
        if (inReportField() || AiReportLink.reporting) return
        val note = reportNote
        reportNote = null
        if (note != null) showNote(note, warn = note.contains("Pictures/Lowkey"))
        val hold = reportHold ?: return
        reportHold = null
        if (!inputViewActive || bridge.isSensitiveField) return
        if (hold.editor == null || hold.editor != bridge.editorKey()) return
        if (clipHistory.current().none { it.key == hold.entry.key }) return
        showAiCard(hold.ready.copy(pasteable = bridge.acceptsImage(hold.entry.mimeType)), null)
    }

    private fun inReportField(): Boolean =
        currentInputEditorInfo?.privateImeOptions?.contains(AiReportActivity.FIELD) == true

    private fun clearSystemClipIf(uri: android.net.Uri) {
        val clip = try { clipboardManager.primaryClip } catch (_: RuntimeException) { return } ?: return
        if (clip.itemCount == 0 || clip.getItemAt(0).uri != uri) return
        try { clipboardManager.clearPrimaryClip() } catch (_: RuntimeException) { }
    }

    override fun onDismissAiResult(card: String) {
        if (aiReady.value?.entry?.key == card || aiFailure.value?.cardId == card) clearAiCards()
    }

    /** Try again on a failed image: the composer, with its prompt, in this field. */
    override fun onRetryAiFailure(id: Long) {
        val failure = aiFailure.value?.takeIf { it.id == id } ?: return
        clearAiCards()
        retryPrompt = RetryPrompt(bridge.editorKey(), failure.prompt, failure.images)
        // A refused key: its editor, and the prompt comes back with the next ✨ here.
        if (failure.fixSetting != null) {
            openAiSettings(failure.fixSetting)
            return
        }
        openAiComposer()
    }

    private fun editorEmpty(): Boolean {
        val ic = currentInputConnection ?: return false
        // Blank counts as empty: a web page's emptied text box may keep a line break. Selected words
        // (Select all) count too: they sit between the text before and after the cursor.
        return ic.getTextBeforeCursor(8, 0).isNullOrBlank() && ic.getTextAfterCursor(8, 0).isNullOrBlank() &&
            ic.getSelectedText(0).isNullOrBlank()
    }

    /** After the app's own Send: its field emptied while a card was up, so the card has done its job. */
    private fun retireAiCardOnSend() {
        if (aiReady.value == null) return
        val empty = editorEmpty()
        if (empty && aiCardFieldHadText) clearReadyCard() else if (!empty) aiCardFieldHadText = true
    }

    private fun clearAiCards() {
        aiCardJob?.cancel()
        aiReady.value = null
        aiFailure.value = null
    }

    /**
     * Shows a ready image or a failure above the strip; the newer replaces the older. A ready image's
     * card retires after a while (the image stays in the clipboard row); a failure stays until it is
     * dismissed or tried again, since its prompt would be lost with it.
     */
    private fun showAiCard(ready: AiReady?, failure: AiFailure?) {
        clearAiCards()
        aiReady.value = ready
        aiFailure.value = failure
        aiCardFieldHadText = !editorEmpty()
        if (ready != null) retireReadyLater()
    }

    private fun retireReadyLater() {
        aiCardJob?.cancel()
        aiCardJob = scope.launch {
            delay(AI_CARD_MS)
            aiReady.value = null
        }
    }

    override fun onAiCardHold(holding: Boolean) {
        if (aiReady.value == null) return
        if (holding) aiCardJob?.cancel() else retireReadyLater()
    }

    /** The ready card only (a failure keeps its prompt and stays). */
    private fun clearReadyCard() {
        aiCardJob?.cancel()
        aiReady.value = null
    }

    /** Settings › Experimental, with [setting]'s editor open when given (the key that was refused). */
    private fun openAiSettings(setting: String?) {
        openCompanion(Destination.SETTINGS_EXPERIMENTAL, setting)
    }

    private fun aiSetup(s: Settings, saved: Map<Secret, String?>, refused: Set<Secret> = aiRefused.value): AiSetup? =
        aiSetupOf(s, saved) { AppGraph.secrets.isRefused(it, saved, refused) }

    /** Enter in the prompt window: what its button says, dimmed while there is nothing to send. */
    private fun promptEnterKind(setup: AiSetup?, empty: Boolean, improving: Boolean): EnterKind = if (improving) {
        // As the window's Create: dimmed until Improve ends, even if the service was switched meanwhile.
        EnterKind.CREATE_EMPTY
    } else when (setup) {
        AiSetup.NO_KEY -> EnterKind.ADD_KEY
        AiSetup.NO_SERVER -> EnterKind.ADD_SERVER
        AiSetup.BAD_SERVER -> EnterKind.FIX_SERVER
        AiSetup.BAD_KEY -> EnterKind.FIX_KEY
        AiSetup.BAD_TOKEN -> EnterKind.FIX_TOKEN
        null -> if (empty) EnterKind.CREATE_EMPTY else EnterKind.CREATE
    }

    override fun onAiOverlayHeight(px: Int) {
        if (px == aiOverlayPx) return
        aiOverlayPx = px
        // A new layout pass asks for the insets again (onComputeInsets).
        composeView?.requestLayout()
    }

    /** Creates an image in the background; other actions can start or be stopped meanwhile. */
    private fun startImageTask(prompt: String, job: ImageJob, editor: String?, images: List<PromptImage>) {
        val id = ++nextAiTask
        val secret = if (settings.current.aiService == AiService.OPENAI) Secret.OPENAI_API_KEY else Secret.SERVER_TOKEN
        val run = AiRun(AiClient(), editor, secret, AppGraph.secrets.version(secret), images)
        aiRuns[id] = run
        val action = if (images.isEmpty()) AiAction.CREATE_IMAGE else AiAction.EDIT_IMAGE
        aiTasks.value = aiTasks.value + AiTask(id, action, prompt, System.currentTimeMillis())
        val clipAtSubmit = clipTime()
        val strict = ContentSafetyPolicy.strict(settings.current.aiSafetyStrict)
        val gate = ContentGate { endpoint, token, body -> run.client.moderate(endpoint, token, body) }
        run.job = scope.launch {
            try {
                val input = withContext(Dispatchers.IO) {
                    gate.checkText(prompt, job.endpoint, job.token, strict, SafetyStage.INPUT)
                }
                // Stopped meanwhile: drop the check, and don't say it was held back.
                if (aiRuns[id] !== run) return@launch
                if (input is SafetyDecision.Blocked) {
                    failed(prompt, run, input.message, refused = input.refused)
                    return@launch
                }
                val reply = withContext(Dispatchers.IO) { run.client.generate(job) }
                if (aiRuns[id] !== run) return@launch
                if (reply is ImageReply.Image) {
                    val checked = withContext(Dispatchers.IO) {
                        gate.checkImage(reply.bytes, reply.mimeType, reply.revisedPrompt, job.endpoint, job.token, strict)
                    }
                    if (aiRuns[id] !== run) return@launch
                    if (checked is SafetyDecision.Blocked) {
                        failed(prompt, run, checked.message, refused = checked.refused)
                        return@launch
                    }
                }
                // The result is here: from now on it is kept, so it leaves the list (and can't be stopped).
                finishAiTask(id)
                when (reply) {
                    is ImageReply.Image ->
                        if (!deliverImage(reply, clipAtSubmit, editor, prompt)) retryPrompt = RetryPrompt(editor, prompt, images)
                    is ImageReply.Failed -> failed(prompt, run, reply.message, refused = reply.refused)
                    is ImageReply.Link -> failed(prompt, run, "The AI service sent no image")
                }
            } finally {
                if (aiRuns[id] === run) finishAiTask(id)
            }
        }
    }

    /** A failed action: a card with the reason and Try again, or a toast while the keyboard is hidden. */
    private fun failed(prompt: String, run: AiRun, message: String, refused: Boolean = false) {
        val editor = run.editor
        retryPrompt = RetryPrompt(editor, prompt, run.images)
        val openAi = run.secret == Secret.OPENAI_API_KEY
        // A refused key keeps the composer saying so ("Fix key") until it is changed; only the key this
        // request went with, if it is still the saved one (not one replaced or switched away from meanwhile).
        if (refused) run.secret?.let { AppGraph.secrets.markRefused(it, run.secretVersion) }
        val noToken = !openAi && AppGraph.secrets.saved.value[Secret.SERVER_TOKEN] == null
        val reason = when {
            !refused -> message
            openAi -> "OpenAI refused the API key"
            noToken -> "Your server asks for an access token"
            else -> "Your server refused the access token"
        }
        // The fix is for the credential this request went with; if the service was switched since,
        // that one isn't in use any more, so Try again (with the current one) is offered instead.
        val sameService = (settings.current.aiService == AiService.OPENAI) == openAi
        val fix = when {
            !refused || !sameService -> null
            openAi -> AiSetup.BAD_KEY.action
            noToken -> "Add token"
            else -> AiSetup.BAD_TOKEN.action
        }
        val fixSetting = if (fix == null) null else if (openAi) AiSetup.BAD_KEY.setting else AiSetup.BAD_TOKEN.setting
        val card = AiFailure(prompt, reason, fix, fixSetting, images = run.images)
        when {
            // The prompt window has the keys: the card waits for the field the prompt was written for.
            inputViewActive && inAiPrompt.value && editor != null && editor == composerEditor -> {
                pendingAiCard = PendingAiCard(editor, failure = card)
                showNote(reason, warn = true)
            }
            // The card (with Try again) only in the field the prompt was written for; elsewhere a note says why.
            inputViewActive && !bridge.isSensitiveField && editor == bridge.editorKey() -> {
                showAiCard(null, card)
                if (settings.current.vibrateOnKeyPress) composeView?.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
            else -> announce(reason, warn = true)
        }
    }

    private fun finishAiTask(id: Long) {
        aiRuns.remove(id)
        aiTasks.value = aiTasks.value.filterNot { it.id == id }
    }

    /** Stops a running action; its prompt comes back with the next prompt. False if it had finished. */
    private fun stopAiTask(id: Long): Boolean {
        val run = aiRuns[id] ?: return false
        aiTasks.value.firstOrNull { it.id == id }?.let { retryPrompt = RetryPrompt(run.editor, it.prompt, run.images) }
        finishAiTask(id)
        run.client.cancel()
        run.job?.cancel()
        return true
    }

    /**
     * A created image goes first in the clipboard history, so a tap sends it, and onto the system
     * clipboard, so long-press Paste works where the keyboard can't insert images; to the gallery too
     * when that setting is on. Nothing else shows: the image is simply there in the clipboard row.
     */
    private suspend fun deliverImage(image: ImageReply.Image, clipAtSubmit: Long, editor: String?, prompt: String): Boolean {
        val entry = withContext(Dispatchers.IO) {
            clipHistory.recordCreated(image.bytes, image.mimeType) { loadThumbnail(contentResolver, it) }
        }
        if (entry == null) {
            announce("The image couldn't be kept", warn = true)
            return false
        }
        // Something copied while the image was being made stays on the clipboard; the image is
        // still first in the history.
        val onClipboard = mayReplaceClip(clipTime(), clipAtSubmit, lastOwnClipAt) && try {
            clipboardManager.setPrimaryClip(ClipData.newUri(contentResolver, "Created image", entry.uri))
            lastOwnClipAt = clipTime()
            true
        } catch (_: RuntimeException) {
            false
        }
        val gallery = settings.current.saveImagesToGallery
        val saved = gallery && withContext(Dispatchers.IO) {
            saveToGallery(this@SlimKeyboardService, image.bytes, image.mimeType, entry.timeMs)
        }
        if (!inputViewActive) {
            announce(when {
                gallery && !saved -> "Image ready · couldn't save it to Gallery"
                onClipboard -> "Image ready · it's on the clipboard"
                else -> "Image ready · it's in Lowkey's clipboard history"
            }, warn = gallery && !saved)
            return true
        }
        if (inAiPrompt.value) {
            // The prompt window has the keys: the card waits for the field the image was made for.
            if (editor != null && editor == composerEditor) {
                pendingAiCard = PendingAiCard(editor, ready = AiReady(entry, false, onClipboard, prompt))
            }
            showNote(if (gallery && !saved) "Image ready · couldn't save it to Gallery" else "Image ready", warn = gallery && !saved)
            if (settings.current.vibrateOnKeyPress) composeView?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            return true
        }
        if (!bridge.isSensitiveField) {
            // Insert only in the field it was made for: another chat gets the paste hint instead.
            val sameField = editor != null && editor == bridge.editorKey()
            showAiCard(AiReady(entry, sameField && bridge.acceptsImage(entry.mimeType), onClipboard, prompt), null)
        }
        if (gallery && !saved) showNote("Image ready · couldn't save it to Gallery", warn = true)
        if (settings.current.vibrateOnKeyPress) composeView?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        return true
    }

    /** When the current system clip was copied, or 0 when unknown or empty. */
    private fun clipTime(): Long = try {
        clipboardManager.primaryClipDescription?.timestamp ?: 0L
    } catch (_: RuntimeException) {
        0L
    }

    /** A note in the strip, or a toast while the keyboard is hidden (the image took a while). */
    private fun announce(text: String, warn: Boolean = false) {
        if (inputViewActive) showNote(text, warn)
        else android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun clearNote() {
        noteJob?.cancel()
        if (strip.value.note != null) strip.value = strip.value.copy(note = null)
    }

    private fun suggester() = AppGraph.suggester(keyboard.state.value.keyboard)

    /** Load exactly the participating languages, within the full-lexicon memory budget. */
    private fun preloadLanguages() {
        AppGraph.suggestionLanguages(keyboard.state.value.keyboard).forEach(AppGraph::preloadLexicon)
    }

    private fun showVoiceOutcomeNote(outcome: VoiceOutcome.Cancelled) =
        showNote(VoiceNotes.outcome(outcome), warn = true)

    private fun showNote(text: String, warn: Boolean = false) {
        strip.value = strip.value.copy(note = StripNote(text, warn), addPrompt = null)
        noteJob?.cancel()
        noteJob = scope.launch {
            delay(NOTE_MS)
            strip.value = strip.value.copy(note = null)
        }
    }

    private fun clearPrompt() {
        if (strip.value.addPrompt != null) strip.value = strip.value.copy(addPrompt = null)
    }

    private fun markOwnEdit() {
        lastOwnEditAt = SystemClock.uptimeMillis()
        invalidateStrip()
    }

    private fun invalidateStrip() {
        stripGeneration++
        stripRequest = null
        stripJob?.cancel()
        stripJob = null
    }

    private fun setMode(m: InputMode) {
        mode.value = m
        settings.update { it.copy(lastMode = m) }
        refreshClipboard()
    }

    /** Remembered mode, except that sensitive fields always open in keys mode (without forgetting it). */
    private fun initialMode(): InputMode = when {
        settings.current.keyboardFirstDictation -> InputMode.KEYS
        bridge.isSensitiveField -> InputMode.KEYS
        // A number, phone or date field opens on its number pad.
        bridge.numberPad != null -> InputMode.KEYS
        settings.current.rememberLastMode -> settings.current.lastMode
        else -> InputMode.KEYS
    }

    private fun returnToKeysAfterDictation(outcome: VoiceOutcome) {
        if (!settings.current.keyboardFirstDictation || mode.value != InputMode.VOICE) return
        pickerOpen.value = false
        setMode(InputMode.KEYS)
        updateShift()
        refreshStrip()
        showNote(when (outcome) {
            is VoiceOutcome.Inserted -> "${outcome.words} words inserted"
            VoiceOutcome.NoSpeech -> "No speech detected"
            else -> "Ready to type"
        })
    }

    private fun openCompanion(destination: Destination, setting: String? = null) {
        pickerOpen.value = false
        voice.cancel(CancelReason.KEYBOARD_HIDDEN)
        requestHideSelf(0)
        startActivity(CompanionLinks.intent(this, destination, setting))
    }

    // ---- Voice wiring ----

    /**
     * Auto-start, unless the active model crashed the keyboard last time: then show why and wait
     * for a manual Talk, so a crashing model can't put the keyboard into a crash loop.
     */
    private fun autoStartVoice() {
        val suspect = AppGraph.crashLog.suspectModelKey
        if (suspect != null && suspect == AppGraph.activeModelKey()) {
            val name = AppGraph.models.state.value.activeModel?.name ?: "This model"
            voice.showNotice("$name stopped unexpectedly last time · tap Talk to retry")
            return
        }
        voice.start()
    }

    private fun voiceConfig(): VoiceConfig {
        val s = settings.current
        return VoiceConfig(
            liveText = s.liveTextInEditor,
            continuous = s.continuousDictation && !s.keyboardFirstDictation,
            applyPhraseRules = s.applyPhraseRules,
            autoCapitalize = s.autoCapitalize,
            matchTermCase = s.matchTermCase,
            terms = dictionary.state.value.terms.map { it.text },
            autoStopMs = s.autoStop.millis,
            rules = dictionary.state.value.rules,
        )
    }

    private fun voiceReadiness(): VoiceReadiness = when {
        !AppGraph.usesTestClips() && !AppGraph.hasMicPermission(this) -> VoiceReadiness.NO_PERMISSION
        AppGraph.models.state.value.activeModel == null -> VoiceReadiness.NO_MODEL
        else -> VoiceReadiness.READY
    }

    /** Dictation text goes into the host editor as composing (underlined) text. */
    private val voiceSink = object : VoiceTextSink {
        private var prefix = ""
        /** Caps mode at the insertion point, read before any composing text is shown. */
        private var capsAtStart = false
        /** Editor the session started in; writes anywhere else are refused. */
        private var sessionEditor: String? = null
        /** Set when the session's editor was replaced; the old one can no longer be edited. */
        var editorGone = false
        val ownership = InsertionOwnership()

        override fun sessionStarted() {
            ownership.begin(selStart, selEnd)
            sessionEditor = bridge.editorKey()
            editorGone = false
        }

        /** True if a restarted input is still this session's editor, at the position it owns. */
        fun ownsEditor(key: String?, initialSelStart: Int, initialSelEnd: Int): Boolean =
            key != null && key == sessionEditor &&
                (initialSelStart to initialSelEnd) == ownership.expectedSelection()

        private fun sameEditor() = !editorGone && bridge.editorKey() == sessionEditor

        private fun computePrefix(): String {
            val before = bridge.textBeforeCursor(1)
            return if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        }

        override fun showProvisional(text: String) {
            if (!sameEditor()) return
            // Over a selection, show no live text: the editor would commit provisional words
            // over the selection if focus moved, destroying the original. Stop replaces it.
            if (!ownership.composing && selStart != selEnd) return
            if (!ownership.composing) {
                prefix = computePrefix()
                capsAtStart = bridge.capsModeAtCursor()
            }
            markOwnEdit()
            if (bridge.setComposingText(prefix + text)) ownership.composed(prefix.length + text.length)
        }

        override fun capitalizeAtInsertion(): Boolean =
            if (ownership.composing) capsAtStart else bridge.capsModeAtCursor()

        override fun clearProvisional(): Boolean {
            if (!ownership.composing) return true
            if (editorGone) return false
            markOwnEdit()
            val ok = bridge.clearComposingText()
            ownership.clearedComposing()
            return ok
        }

        override fun commitFinal(text: String): Boolean {
            if (!sameEditor()) return false
            // Ask the editor where the cursor is right now; refuse if the session lost it.
            val current = bridge.currentSelection()
            if (current != null && current != ownership.expectedSelection()) return false
            if (current == null && !ownership.composing && (selStart to selEnd) != ownership.expectedSelection()) return false
            markOwnEdit()
            val lead = if (ownership.composing) prefix else computePrefix()
            // Trailing space so typing can continue; a following "." or "," swaps with it.
            val inserted = "$lead$text "
            bridge.commitText(inserted)
            ownership.committed(inserted.length)
            autoSpaced = true
            markOwnEdit()
            return true
        }
    }

    private companion object {
        const val NOTE_MS = 3_500L
        /** How long a ready image's (or a failure's) card stays above the strip untouched. */
        const val AI_CARD_MS = 30_000L
        /** [scrubLevel] once a slide into the editor set the text instead of the slider. */
        const val AIMED = -2
        const val WIDE_PX = 100_000f
        const val MAX_CLIP_CHIPS = 12
        /** Prune a moment after a clip expires, so one pass catches it. */
        private const val CLIP_PRUNE_SLACK_MS = 1_000L
        /** Languages with CLDR emoji names and keywords in `assets/emoji/keywords-<code>.txt`. */
        private val EMOJI_KEYWORD_LANGUAGES = listOf("en", "nl")
        private const val MAX_EMOJI_QUERY = 40
        /** Private clips remembered; only the current system clip matters, so a few suffice. */
        private const val MAX_PRIVATE_CLIPS = 32
        const val PREVIEW_PX = 720
        const val OWN_EDIT_WINDOW_MS = 250L
        const val MAX_TERM_CHARS = 48
        const val SENSITIVE_NOTE = "Voice is off in password and private fields"
    }
}
