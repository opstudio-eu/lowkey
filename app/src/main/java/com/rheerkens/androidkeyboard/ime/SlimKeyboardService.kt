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
import com.rheerkens.androidkeyboard.companion.CompanionLinks
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.dictionary.DictionaryStore
import com.rheerkens.androidkeyboard.dictionary.PersonalTerm
import com.rheerkens.androidkeyboard.dictionary.Suggester
import com.rheerkens.androidkeyboard.dictionary.Corrector
import com.rheerkens.androidkeyboard.dictionary.Suggestion
import com.rheerkens.androidkeyboard.dictionary.NextWordPredictor
import com.rheerkens.androidkeyboard.keyboard.BackspaceScrub
import com.rheerkens.androidkeyboard.keyboard.FanCommand
import com.rheerkens.androidkeyboard.keyboard.Key
import com.rheerkens.androidkeyboard.keyboard.KeyboardController
import com.rheerkens.androidkeyboard.keyboard.Layer
import com.rheerkens.androidkeyboard.keyboard.EmojiSuggestions
import com.rheerkens.androidkeyboard.keyboard.Layouts
import com.rheerkens.androidkeyboard.keyboard.ui.ClipChip
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
import com.rheerkens.androidkeyboard.keyboard.EmojiHistory
import com.rheerkens.androidkeyboard.keyboard.PlacementProfile
import com.rheerkens.androidkeyboard.keyboard.PlacementMode

/**
 * Slim Keyboard input method: keys mode with suggestions, and a one-row voice mode with an
 * in-keyboard model picker, backed by the (fake) speech engine. UI is Compose hosted in the
 * IME window.
 */
class SlimKeyboardService : InputMethodService(), KeyboardActions {
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
    private var inputViewActive = false
    private lateinit var clipHistory: ClipboardHistory
    /** Clipboard history for the minimized bar, empty in sensitive fields. */
    private val clipChips = MutableStateFlow<List<ClipChip>>(emptyList())
    /** Bumped each time the keyboard opens, so the clip row starts at the newest item. */
    private val inputSession = MutableStateFlow(0)
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
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        refreshClipboard()
        recordClipboard()
    }

    /** The clip history, system clip and screenshot cut-off from before a Clear clips. */
    private class ClearedClips(val entries: List<ClipEntry>, val primary: ClipData?, val clearedAtBefore: Long)

    /** What an autocorrection depends on: the word, the keyboard (language and layout), and sentence position. */
    /** [lexiconLoad] tells a result computed on a fallback list from one on the full list. */
    private data class CorrectionKey(val word: String, val keyboard: String, val atSentenceStart: Boolean,
        val lexiconLoad: Int, val languages: List<String>, val terms: List<PersonalTerm>)
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
    private val doubleSpacePeriod = DoubleSpacePeriod()

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        owner.onCreate()
        bridge = EditorBridge { currentInputConnection }
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardReader = ClipboardReader(contentResolver)
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        clipHistory = ClipboardHistory(this)
        clipHistory.entries.onEach { refreshClipChips() }.launchIn(scope)
        screenshots = RecentScreenshots(this)
        recentShots.onEach { refreshClipChips() }.launchIn(scope)
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        keyboard = KeyboardController(AppGraph.currentKeyboard())
        scope.launch {
            emojiCatalog.value = withContext(Dispatchers.IO) {
                runCatching {
                    val paint = android.graphics.Paint()
                    EmojiCatalog.parse(assets.open("emoji/emoji-test.txt").bufferedReader().use { it.readText() })
                        .supported(paint::hasGlyph)
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
        emojiOpen.onEach(::updateEmojiBack).launchIn(scope)
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
                val sensitive by sensitiveField.collectAsState()
                val physical by physicalKeyboard.collectAsState()
                val clips by clipChips.collectAsState()
                val session by inputSession.collectAsState()
                val once by keyboardOnce.collectAsState()
                val fan by fanSpec.collectAsState()
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
                val layout = remember(kb.layer, kb.keyboard, s.numberRow, s.bottomKey) {
                    Layouts.forLayer(kb.layer, kb.keyboard, s.numberRow, s.bottomKey)
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
                        enterKind = ek,
                        rowHeightDp = s.keyboardHeight.rowHeightDp,
                        colors = colors,
                        background = background,
                        numberRow = s.numberRow,
                        emojiOpen = emoji && m == InputMode.KEYS,
                        emojiCatalog = catalog,
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
            outInsets.contentTopInsets = bounds.top
            outInsets.visibleTopInsets = bounds.top
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
        refreshClipChips()
        resizeOnOpen = info.privateImeOptions?.contains("lowkey.resize") == true
        placementBounds = null
        inputSession.value++
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
        if (keepSession) return
        keyboard.reset()
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
        recordClipboard()
        refreshScreenshots()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        doubleSpacePeriod.reset()
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
        if (voice.currentSessionId == null) {
            // In either mode, an update outside a session settles our earlier writes.
            voiceSink.ownership.observedIdle()
        } else if (!voiceSink.ownership.owns(newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
            // The cursor must stay where dictation started (or at the end of its composing text).
            // Any other move cancels, so late results can't land where the user didn't expect.
            voice.cancel(CancelReason.CURSOR_MOVED)
        }
        if (mode.value != InputMode.KEYS) return
        // Our own edits update shift synchronously; only react to external cursor moves.
        if (SystemClock.uptimeMillis() - lastOwnEditAt > OWN_EDIT_WINDOW_MS) {
            doubleSpacePeriod.reset()
            autoSpaced = false
            updateShift()
        }
        refreshStrip()
    }

    override fun onDestroy() {
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
        if (key !is Key.Space) doubleSpacePeriod.reset()
        if (key !is Key.Emoji) emojiOpen.value = false
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
                bridge.performEnter()
                updateShift()
            }
            is Key.Backspace -> onBackspace()
            is Key.Gap -> Unit
        }
    }

    override fun onBackspace() {
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
                keyboard.setLayer(if (command == FanCommand.NUMBERS_ONCE) Layer.SYMBOLS else Layer.LETTERS)
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
    }

    override fun onGlobeLongPress() {
        clearPrompt()
        emojiOpen.value = false
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    override fun onSpaceLongPress() = onGlobeLongPress()

    override fun onCloseEmoji() {
        emojiOpen.value = false
        keyboard.setLayer(Layer.LETTERS)
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
        openCompanion(Destination.SETTINGS_KEYBOARDS)
    }

    override fun onSuggestion(suggestion: Suggestion) {
        doubleSpacePeriod.reset()
        if (suggestion.emojiName != null) {
            if (!inputViewActive || bridge.isSensitiveField || !settings.current.suggestionStrip) return
            val available = EmojiSuggestions.suggest(bridge.currentWordBeforeCursor(),
                AppGraph.suggestionLanguages(keyboard.state.value.keyboard).map { it.id }, emojiCatalog.value)
            if (available != suggestion) return
        }
        clearNote()
        lastCorrection = null
        markOwnEdit()
        val typed = bridge.currentWordBeforeCursor()
        val phrase = if (suggestion.replaceChars > 0) {
            savedPhrase(suggester(), dictionary.state.value.terms)
        } else {
            null
        }
        if (phrase != null && phrase == suggestion.replaceChars to suggestion.text) {
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
        }
        keyboard.setLayer(Layer.LETTERS)
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
    override fun onOpenModels() = openCompanion(Destination.MODELS)
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
            val cb = OnBackInvokedCallback { emojiOpen.value = false }
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
                emojiOpen.value = false
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
            bridge.commitText(keyboard.applyCase(output))
        }
        autoSpaced = false
        keyboard.onCharTyped()
        updateShift()
        refreshStrip()
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
        autoSpaced = false
        keyboard.setLayer(Layer.LETTERS)
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
                // A fast Space must not run a dictionary scan on the input thread. On a
                // cache miss keep the literal word; never correct it after later edits.
                else -> null
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
        if (word.isEmpty() || word.lowercase() in rejectedCorrections) return null
        if (!Corrector.isStandaloneWord(snapshot.token, word)) return null
        return context
    }

    private fun suggestionKey(snapshot: EditorContext): CorrectionKey {
        val current = keyboard.state.value.keyboard
        return CorrectionKey(snapshot.word, current.id, snapshot.wordStartsSentence, AppGraph.lexiconLoads.value,
            AppGraph.suggestionLanguages(current).map { it.id }, dictionary.state.value.terms)
    }

    /** The saved multi-word term to recase before the cursor, unless the user undid it here. */
    private fun savedPhrase(suggester: Suggester, terms: List<PersonalTerm>,
        before: String = bridge.textBeforeCursor(MAX_TERM_CHARS)): Pair<Int, String>? =
        suggester.protectedPhrase(before.takeLast(MAX_TERM_CHARS), terms)
            ?.takeIf { before.takeLast(it.first).lowercase() !in rejectedCorrections }

    private fun updateShift() {
        keyboard.setAutoShift(settings.current.autoCapitalize && bridge.capsModeAtCursor())
    }

    /** Recomputes suggestion slots. Notes stay until they expire or the user types. */
    private fun refreshStrip() {
        if (mode.value != InputMode.KEYS || isMinimized()) return
        val snapshot = bridge.readContext()
        val typed = snapshot.word
        if (typed.isEmpty() || bridge.isSensitiveField) {
            invalidateStrip()
            cachedCorrection = null
            val predictions = if (typed.isEmpty() && bridge.allowsTextShortcuts &&
                settings.current.suggestionStrip && !bridge.hasSelection()) {
                NextWordPredictor.suggest(bridge.textBeforeCursor(NextWordPredictor.CONTEXT_LIMIT), dictionary.state.value.terms)
            } else emptyList()
            val image = strip.value.clipboard as? ClipboardContent.Image
            strip.value = strip.value.copy(
                slots = Suggester.arrangeForStrip("", predictions),
                typingWord = predictions.isNotEmpty(),
                imagePasteSupported = image != null && bridge.acceptsImage(image.mimeType),
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
        val suggester = suggester()
        val session = inputSession.value
        val emoji = EmojiSuggestions.suggest(typed, context.languages, emojiCatalog.value)
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
                if (request.enabled) suggester.suggest(typed, terms, checkActive = { ensureActive() }) else emptyList()
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
            val content = withContext(Dispatchers.IO) {
                try { clipboardReader.read(clipboardManager.primaryClip) } catch (_: RuntimeException) { null }
            }
            if (!inputViewActive || !bridge.isCurrent(target) || bridge.isSensitiveField) return@launch
            strip.value = strip.value.copy(
                clipboard = content,
                imagePasteSupported = content is ClipboardContent.Image && bridge.acceptsImage(content.mimeType),
            )
        }
    }

    /** Adds the current system clip to the history (also while the keyboard is hidden). */
    private fun recordClipboard() {
        scope.launch(Dispatchers.IO) {
            val content = try { clipboardReader.read(clipboardManager.primaryClip) } catch (_: RuntimeException) { null }
            if (content != null) {
                clipHistory.record(content)
                // Something new on the clipboard: Clear clips takes Undo clear's place again.
                withContext(Dispatchers.Main) { forgetClear() }
            }
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
        clipChips.value = ClipboardHistory.merge(clipHistory.entries.value, shots, MAX_CLIP_CHIPS).map {
            val mime = when (it) {
                is ClipEntry.Text -> null
                is ClipEntry.Image -> it.mimeType
                is ClipEntry.Screenshot -> it.mimeType
            }
            ClipChip(it, mime == null || bridge.acceptsImage(mime))
        }
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

    private fun openCompanion(destination: Destination) {
        pickerOpen.value = false
        voice.cancel(CancelReason.KEYBOARD_HIDDEN)
        requestHideSelf(0)
        startActivity(CompanionLinks.intent(this, destination))
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
        /** [scrubLevel] once a slide into the editor set the text instead of the slider. */
        const val AIMED = -2
        const val WIDE_PX = 100_000f
        const val MAX_CLIP_CHIPS = 12
        const val PREVIEW_PX = 720
        const val OWN_EDIT_WINDOW_MS = 250L
        const val MAX_TERM_CHARS = 48
        const val SENSITIVE_NOTE = "Voice is off in password and private fields"
    }
}
