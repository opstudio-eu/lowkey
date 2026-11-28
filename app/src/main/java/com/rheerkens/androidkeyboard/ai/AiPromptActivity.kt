package com.rheerkens.androidkeyboard.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.clipboard.ClipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.CompanionLinks
import com.rheerkens.androidkeyboard.companion.Destination
import com.rheerkens.androidkeyboard.companion.theme.resolveDark
import com.rheerkens.androidkeyboard.keyboard.ui.AiComposerActions
import com.rheerkens.androidkeyboard.keyboard.ui.AiPromptCard
import com.rheerkens.androidkeyboard.keyboard.ui.AiSendNoticeCard
import com.rheerkens.androidkeyboard.keyboard.ui.AiUi
import com.rheerkens.androidkeyboard.keyboard.ui.LocalKeyboardColors
import com.rheerkens.androidkeyboard.keyboard.ui.StripNote
import com.rheerkens.androidkeyboard.keyboard.ui.resolveKeyboardColors

/**
 * The image prompt, in a card just above the keyboard (over the app, which doesn't move), opened by ✨. Its box
 * is a real text field with the focus, so the keyboard types into it like into any app's (Backspace,
 * the mic, the clipboard row and other voice tools all work), and the app below doesn't move. Create
 * hands the prompt to the keyboard ([AiComposerHost]), which makes the image for the field the window
 * was opened from; the window closes and that field gets the focus back.
 */
class AiPromptActivity : ComponentActivity() {
    private var prompt by mutableStateOf(TextFieldValue(""))
    private var optionsOpen by mutableStateOf(false)
    private var note by mutableStateOf<StripNote?>(null)
    /** Create started an image: the words don't come back as a draft. */
    private var submitted = false
    /** Which prompt the keyboard opened this window for ([AiComposerHost.submitImagePrompt]). */
    private var session = -1
    private var trayOpen by mutableStateOf(false)

    /** The system's photo picker is open over the window, which stays (and keeps its words) meanwhile. */
    private var picking = false
    /** A report of the improved prompt is open; the window stays until that screen closes. */
    private val discardImprovedHook: (Int) -> Unit = { discardImprovedPrompt(it) }
    /** Counts returns from the photo picker: each one gives the box the focus and the keyboard back. */
    private var refocus by mutableStateOf(0)

    /** The first send to this service is waiting for Continue. Cancel leaves the prompt unsent. */
    private var pendingSend by mutableStateOf<AiSendKind?>(null)
    /** Improve's request while it runs. */
    private var improver: AiClient? = null
    private var improving by mutableStateOf(false)
    /** The words before Improve, offered by Undo until the improved prompt ([improved]) is changed. */
    private var beforeImprove by mutableStateOf<String?>(null)
    private var improved: String? = null

    // Registered up front, as activity results must be; the photo picker needs no permission.
    private val pickOne = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        picked(listOfNotNull(uri))
    }
    private val pickSome = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ImageApi.MAX_INPUT_IMAGES)) {
        picked(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        val host = AiComposerLink.host
        if (host == null) {
            // The keyboard isn't running (another keyboard was picked meanwhile): nothing could make the image.
            finish()
            return
        }
        // It appears and goes at once: no window sliding over the app it hands the keyboard back to.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        AiReportLink.discardImproved = discardImprovedHook
        session = intent.getIntExtra(EXTRA_SESSION, -1)
        val start = intent.getStringExtra(EXTRA_PROMPT).orEmpty().take(ImageApi.MAX_PROMPT_CHARS)
        prompt = TextFieldValue(start, TextRange(start.length))
        setContent {
            val settings by AppGraph.settings.settings.collectAsState()
            val keyboardTheme by AppGraph.keyboardTheme.theme.collectAsState()
            val saved by AppGraph.secrets.saved.collectAsState()
            val refused by AppGraph.secrets.refused.collectAsState()
            val tasks by host.runningImages.collectAsState()
            val images by host.promptImages.collectAsState()
            val recent by host.recentImages.collectAsState()
            val dark = resolveDark(settings.theme, isSystemInDarkTheme())
            val colors = remember(keyboardTheme, dark) { resolveKeyboardColors(keyboardTheme, dark) }
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            val view = LocalView.current
            // The keyboard's Enter (Create) dims as the card's Create does: no words yet, or Improve rewriting them.
            val blank = prompt.text.isBlank()
            LaunchedEffect(blank, improving) { host.promptState(blank, improving, session) }
            LaunchedEffect(refocus) {
                focus.requestFocus()
                keyboard?.show()
            }
            val ai = AiUi(
                available = true,
                optionsOpen = optionsOpen,
                tasks = tasks,
                note = note,
                service = settings.aiService,
                imageModel = settings.imageModel,
                imageQuality = settings.imageQuality,
                serverModel = settings.imageServerModel,
                setup = aiSetupOf(settings, saved) { AppGraph.secrets.isRefused(it, saved, refused) },
                serverHost = serverHostOf(settings),
                images = images,
                trayOpen = trayOpen,
                recentImages = recent,
                improving = improving,
                beforeImprove = beforeImprove,
            )
            val actions = remember(host) {
                object : AiComposerActions {
                    // Not while Improve rewrites the words (Create shows dimmed): the old words would go.
                    override fun onSubmit() = if (improving) Unit else submit(host) {
                        if (AppGraph.settings.current.vibrateOnKeyPress) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    override fun onClose() = finish()
                    override fun onStop(id: Long) = host.stopImage(id)
                    // One panel at a time: the choices or the pictures tray, so the card stays short.
                    override fun onToggleOptions() {
                        optionsOpen = !optionsOpen
                        if (optionsOpen) trayOpen = false
                    }
                    override fun onOpenSettings(setting: String?) = openSettings(setting)
                    override fun onSelectService(service: AiService) = AppGraph.settings.update { it.copy(aiService = service) }
                    override fun onSelectModel(id: String) = AppGraph.settings.update { it.copy(imageModel = id) }
                    override fun onSelectQuality(quality: ImageQuality) = AppGraph.settings.update { it.copy(imageQuality = quality) }
                    override fun onToggleTray() {
                        trayOpen = !trayOpen
                        if (trayOpen) optionsOpen = false
                    }
                    override fun onPickPhotos() = pickPhotos(host)
                    override fun onAddRecent(entry: ClipEntry) = host.addPromptImage(entry)
                    override fun onRemoveImage(id: Long) = host.removePromptImage(id)
                    override fun onImprove() = improve(host)
                    override fun onUndoImprove() {
                        val words = beforeImprove ?: return
                        stopImproving()
                        beforeImprove = null
                        prompt = TextFieldValue(words, TextRange(words.length))
                    }
                    override fun onReportImproved() = reportImproved()
                }
            }
            CompositionLocalProvider(LocalKeyboardColors provides colors) {
                // The window covers the screen, invisibly: the card sits on the keyboard's top edge, over
                // whatever the app shows there, and follows the keyboard as it moves. A tap anywhere
                // else closes it.
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(pendingSend) { detectTapGestures { if (pendingSend == null) finish() } }
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.systemBars))
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val waiting = pendingSend
                    if (waiting != null) {
                        val notice = AiDisclosure.notice(waiting, images.size, settings.aiService, serverHostOf(settings))
                        AiSendNoticeCard(
                            notice,
                            onContinue = { acceptSendNotice(host) },
                            onCancel = { pendingSend = null },
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        )
                    } else AiPromptCard(ai, prompt, { next ->
                        // Typed into: Improve stops (the words typed win), and its Undo goes once the improved words change.
                        if (next.text != prompt.text && improving) stopImproving()
                        if (next.text != prompt.text && next.text != improved) beforeImprove = null
                        // A note stays while only the cursor moves; new words clear it.
                        if (next.text != prompt.text) note = null
                        prompt = next
                    }, focus, System::currentTimeMillis, actions,
                        Modifier.widthIn(max = 720.dp).fillMaxWidth()
                            // Taps on the card's own empty space stay on the card.
                            .pointerInput(Unit) { detectTapGestures { } })
                }
            }
        }
        // After setContent: a floating window's content sets it to wrap its content in both directions.
        window.apply {
            // A transparent layer over the whole app, which stays as it is, undimmed; the card places
            // itself above the keyboard from the window insets.
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        // Back (or the keyboard's own hide) closes the window with the keyboard: without its keys the
        // prompt has nothing to type with, and a second Back shouldn't be needed.
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            val shown = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (shown) imeSeen = true
            else if (imeSeen && hasWindowFocus() && !isFinishing && !picking && !AiReportLink.reporting) finish()
            ViewCompat.onApplyWindowInsets(view, insets)
        }
    }

    /** The keyboard has shown for the prompt; once it hides again, the window closes. */
    private var imeSeen = false

    /** Photos in the tray: the system's photo picker, for as many pictures as there is room for. */
    private fun pickPhotos(host: AiComposerHost) {
        val room = host.promptImageRoom()
        if (room <= 0 || picking) return
        picking = true
        try {
            if (room == 1) {
                pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                pickSome.launch(PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).setMaxItems(room).build())
            }
        } catch (_: RuntimeException) {
            // No photo picker on this phone (ActivityNotFoundException).
            picking = false
            note = StripNote("This phone has no photo picker", warn = true)
        }
    }

    private fun picked(uris: List<Uri>) {
        picking = false
        // The window closes when the keyboard hides only once it has shown again.
        imeSeen = false
        refocus++
        if (uris.isEmpty()) return
        AiComposerLink.host?.addPromptPhotos(uris, session)
        trayOpen = false
    }

    private fun sendNoticeAccepted(): Boolean {
        val settings = AppGraph.settings.current
        return AiDisclosure.accepted(settings.aiSendNotice, settings.aiService, settings.aiServerUrl)
    }

    /** Continue on the first-send notice: remember it, then send what was waiting. */
    private fun acceptSendNotice(host: AiComposerHost) {
        val settings = AppGraph.settings.current
        val key = AiDisclosure.acceptanceKey(settings.aiService, settings.aiServerUrl)
        AppGraph.settings.update { it.copy(aiSendNotice = key) }
        val pending = pendingSend
        pendingSend = null
        when (pending) {
            AiSendKind.CREATE -> submit(host) {
                if (AppGraph.settings.current.vibrateOnKeyPress) {
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            }
            AiSendKind.IMPROVE -> improve(host)
            null -> Unit
        }
    }

    /**
     * Improve: the service's text model rewrites the prompt into a clearer, more vivid one, which
     * replaces the words (Undo brings them back). Again while it works stops it.
     */
    private fun improve(host: AiComposerHost) {
        if (improving) {
            stopImproving()
            return
        }
        val settings = AppGraph.settings.current
        aiSetupOf(settings, AppGraph.secrets.saved.value) { AppGraph.secrets.isRefused(it) }?.let {
            openSettings(it.setting)
            return
        }
        val words = prompt.text
        val strict = ContentSafetyPolicy.strict(settings.aiSafetyStrict)
        LocalBlocklist.check(words, strict, SafetyStage.INPUT)?.let {
            note = StripNote(it.message, warn = true)
            return
        }
        val check = promptJob(settings, words, AppGraph.secrets::get, BuildConfig.DEBUG, host.promptImages.value.size)
        val job = check.job ?: run {
            note = StripNote(check.problem ?: "AI isn't set up", warn = true)
            return
        }
        if (!sendNoticeAccepted()) {
            pendingSend = AiSendKind.IMPROVE
            return
        }
        val client = AiClient()
        improver = client
        improving = true
        note = null
        val gate = ContentGate { endpoint, token, body -> client.moderate(endpoint, token, body) }
        lifecycleScope.launch {
            val input = withContext(Dispatchers.IO) {
                gate.checkText(words, job.endpoint, job.token, strict, SafetyStage.INPUT)
            }
            // Stopped, undone or closed meanwhile: a cancelled check must not leave a note.
            if (improver !== client) return@launch
            if (input is SafetyDecision.Blocked) {
                improver = null
                improving = false
                note = StripNote(safetyNote(input), warn = true)
                return@launch
            }
            val reply = withContext(Dispatchers.IO) { client.improve(job) }
            if (improver !== client) return@launch
            when (reply) {
                is PromptReply.Text -> {
                    val output = withContext(Dispatchers.IO) {
                        gate.checkText(reply.prompt, job.endpoint, job.token, strict, SafetyStage.OUTPUT)
                    }
                    if (improver !== client) return@launch
                    improver = null
                    improving = false
                    if (output is SafetyDecision.Blocked) {
                        note = StripNote(safetyNote(output), warn = true)
                        return@launch
                    }
                    improved = reply.prompt
                    beforeImprove = words
                    prompt = TextFieldValue(reply.prompt, TextRange(reply.prompt.length))
                }
                // Only said here: a refusal at this endpoint doesn't mark the key refused for Create too.
                is PromptReply.Failed -> {
                    improver = null
                    improving = false
                    note = StripNote(
                        if (reply.safety) reply.message
                        else "Couldn't improve the prompt: " + reply.message.replaceFirstChar { it.lowercase() },
                        warn = true,
                    )
                }
            }
        }
    }

    /** A held-back Improve. A refused key keeps the existing "couldn't improve" wording. */
    private fun safetyNote(blocked: SafetyDecision.Blocked): String = if (blocked.refused) {
        "Couldn't improve the prompt: " + blocked.message.replaceFirstChar { it.lowercase() }
    } else {
        blocked.message
    }

    /** Flag beside Undo: report the improved words. The window stays open behind the report. */
    private fun reportImproved() {
        if (beforeImprove == null) return
        val words = improved ?: prompt.text
        AiReportLink.reporting = true
        try {
            startActivity(AiReportActivity.promptIntent(this, session, words))
        } catch (_: RuntimeException) {
            AiReportLink.reporting = false
            note = StripNote("Couldn't open the report", warn = true)
        }
    }

    /** The report was filed: the improved words go, and the earlier ones come back. */
    private fun discardImprovedPrompt(sessionId: Int) {
        if (sessionId != session || isFinishing) return
        val words = beforeImprove ?: return
        stopImproving()
        beforeImprove = null
        improved = null
        prompt = TextFieldValue(words, TextRange(words.length))
        note = StripNote("Reported and removed")
    }

    private fun stopImproving() {
        improver?.cancel()
        improver = null
        improving = false
    }

    /** Create: with nothing set up, the setting that is missing; otherwise the image starts and the window closes. */
    private fun submit(host: AiComposerHost, confirm: () -> Unit) {
        if (submitted) return
        stopImproving()
        val settings = AppGraph.settings.current
        aiSetupOf(settings, AppGraph.secrets.saved.value) { AppGraph.secrets.isRefused(it) }?.let {
            openSettings(it.setting)
            return
        }
        if (prompt.text.isBlank()) return
        LocalBlocklist.check(
            prompt.text,
            ContentSafetyPolicy.strict(settings.aiSafetyStrict),
            SafetyStage.INPUT,
        )?.let {
            note = StripNote(it.message, warn = true)
            return
        }
        if (!sendNoticeAccepted()) {
            pendingSend = AiSendKind.CREATE
            return
        }
        val problem = host.submitImagePrompt(prompt.text, session)
        if (problem != null) {
            note = StripNote(problem, warn = true)
            return
        }
        submitted = true
        confirm()
        finish()
    }

    /** Settings › Experimental (with [setting]'s editor open); the words come back with the next prompt. */
    private fun openSettings(setting: String?) {
        startActivity(CompanionLinks.intent(this, Destination.SETTINGS_EXPERIMENTAL, setting))
        finish()
    }

    /**
     * Gone at once: the window is cleared first, so the system's close animation for its task has
     * nothing to slide over the app it hands the keyboard back to.
     */
    override fun finish() {
        val decor = window.peekDecorView()
        if (decor == null || !decor.isAttachedToWindow || decor.alpha == 0f) {
            finishNow()
            return
        }
        decor.alpha = 0f
        decor.postOnAnimation { finishNow() }
    }

    private fun finishNow() {
        if (isFinishing) return
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /** Away from the window (Home, another app): it closes, as the prompt is for the app below. The photo picker keeps it. */
    override fun onStop() {
        super.onStop()
        if (!picking && !AiReportLink.reporting && !isChangingConfigurations && !isFinishing) finishNow()
    }

    override fun onDestroy() {
        if (AiReportLink.discardImproved === discardImprovedHook) AiReportLink.discardImproved = null
        stopImproving()
        AiComposerLink.host?.promptState(prompt.text.isBlank(), false, session)
        // Closed without Create (✕, Back, a tap outside, Home): the words come back next time.
        if (isFinishing && !submitted) AiComposerLink.host?.composerClosed(prompt.text, session)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PROMPT = "prompt"
        private const val EXTRA_SESSION = "session"

        /** Opens the window from the keyboard (its own task), with [prompt] already typed, for prompt [session]. */
        fun intent(context: Context, prompt: String, session: Int): Intent =
            Intent(context, AiPromptActivity::class.java)
                .putExtra(EXTRA_PROMPT, prompt)
                .putExtra(EXTRA_SESSION, session)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
