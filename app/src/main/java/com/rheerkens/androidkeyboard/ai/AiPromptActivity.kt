package com.rheerkens.androidkeyboard.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        val start = intent.getStringExtra(EXTRA_PROMPT).orEmpty().take(ImageApi.MAX_PROMPT_CHARS)
        prompt = TextFieldValue(start, TextRange(start.length))
        setContent {
            val settings by AppGraph.settings.settings.collectAsState()
            val keyboardTheme by AppGraph.keyboardTheme.theme.collectAsState()
            val saved by AppGraph.secrets.saved.collectAsState()
            val refused by AppGraph.secrets.refused.collectAsState()
            val tasks by host.runningImages.collectAsState()
            val dark = resolveDark(settings.theme, isSystemInDarkTheme())
            val colors = remember(keyboardTheme, dark) { resolveKeyboardColors(keyboardTheme, dark) }
            val focus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            val view = LocalView.current
            LaunchedEffect(Unit) {
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
            )
            val actions = remember(host) {
                object : AiComposerActions {
                    override fun onSubmit() = submit(host) {
                        if (AppGraph.settings.current.vibrateOnKeyPress) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    override fun onClose() = finish()
                    override fun onStop(id: Long) = host.stopImage(id)
                    override fun onToggleOptions() { optionsOpen = !optionsOpen }
                    override fun onOpenSettings(setting: String?) = openSettings(setting)
                    override fun onSelectService(service: AiService) = AppGraph.settings.update { it.copy(aiService = service) }
                    override fun onSelectModel(id: String) = AppGraph.settings.update { it.copy(imageModel = id) }
                    override fun onSelectQuality(quality: ImageQuality) = AppGraph.settings.update { it.copy(imageQuality = quality) }
                }
            }
            CompositionLocalProvider(LocalKeyboardColors provides colors) {
                // The window covers the screen, invisibly: the card sits on the keyboard's top edge, over
                // whatever the app shows there, and follows the keyboard as it moves. A tap anywhere
                // else closes it.
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { finish() } }
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.systemBars))
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AiPromptCard(ai, prompt, { prompt = it; note = null }, focus, System::currentTimeMillis, actions,
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
            else if (imeSeen && hasWindowFocus() && !isFinishing) finish()
            ViewCompat.onApplyWindowInsets(view, insets)
        }
    }

    /** The keyboard has shown for the prompt; once it hides again, the window closes. */
    private var imeSeen = false

    /** Create: with nothing set up, the setting that is missing; otherwise the image starts and the window closes. */
    private fun submit(host: AiComposerHost, confirm: () -> Unit) {
        if (submitted) return
        val settings = AppGraph.settings.current
        aiSetupOf(settings, AppGraph.secrets.saved.value) { AppGraph.secrets.isRefused(it) }?.let {
            openSettings(it.setting)
            return
        }
        if (prompt.text.isBlank()) return
        val problem = host.submitImagePrompt(prompt.text)
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

    override fun onDestroy() {
        // Closed without Create (✕, Back, a tap outside, Home): the words come back next time.
        if (isFinishing && !submitted) AiComposerLink.host?.composerClosed(prompt.text)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PROMPT = "prompt"

        /** Opens the window from the keyboard (its own task), with [prompt] already typed. */
        fun intent(context: Context, prompt: String): Intent =
            Intent(context, AiPromptActivity::class.java)
                .putExtra(EXTRA_PROMPT, prompt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
