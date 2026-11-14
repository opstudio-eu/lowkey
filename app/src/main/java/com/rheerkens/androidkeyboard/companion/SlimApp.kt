package com.rheerkens.androidkeyboard.companion

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rheerkens.androidkeyboard.companion.screens.*
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.companion.theme.applyThemeToWindow
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.delay

/** Native companion shell. Stored preference keys and IME destinations remain unchanged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimApp(pendingDestination: Destination?, onDestinationConsumed: () -> Unit) {
    var stackNames by rememberSaveable { mutableStateOf(listOf(Destination.HOME.name)) }
    val stack = stackNames.map { Destination.valueOf(it) }
    val current = stack.last()
    var testOpen by rememberSaveable { mutableStateOf(false) }
    var resizeKeyboard by rememberSaveable { mutableStateOf(false) }
    var testDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var setupSkipped by rememberSaveable { mutableStateOf(false) }
    var themeHasChanges by remember { mutableStateOf(false) }
    var pendingRoute by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var pendingClearsSearch by rememberSaveable { mutableStateOf(false) }
    // Kept while the user opens results, so Back returns to them; a tab tap starts afresh.
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val highlighter = remember { SettingHighlighter() }
    val ime = rememberImeStatus()
    val setup = keyboardSetupState(ime.enabled, ime.selected)
    val onboarding = current == Destination.HOME && setup != KeyboardSetupState.READY && !setupSkipped
    var entered by rememberSaveable { mutableStateOf(false) }
    val entry = remember { Animatable(if (entered) 1f else 0f) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dark = MaterialTheme.colorScheme.isDark
    val home = current == Destination.HOME
    val fontScale = LocalDensity.current.fontScale
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val activity = LocalActivity.current as? androidx.activity.ComponentActivity

    LaunchedEffect(activity, dark, home) {
        activity?.applyThemeToWindow(dark, brandSurround = true, navigationOnSurface = true)
    }

    LaunchedEffect(Unit) {
        if (!entered) {
            entered = true
            // Compose respects the system animator duration scale, including animations disabled.
            entry.animateTo(1f, tween(500))
        }
    }
    fun closeTest() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        testOpen = false
    }
    fun openTest() { focus.clearFocus(force = true); resizeKeyboard = false; testOpen = true }
    fun openResize() { focus.clearFocus(force = true); resizeKeyboard = true; testOpen = true }
    fun applyRoute(route: List<String>, clearSearch: Boolean = false) {
        if (testOpen) closeTest()
        highlighter.clear()
        if (clearSearch) settingsQuery = ""
        stackNames = route
    }
    fun requestRoute(route: List<String>, clearSearch: Boolean = false) {
        if (current == Destination.KEYBOARD_THEME && themeHasChanges && route != stackNames) {
            pendingRoute = route
            pendingClearsSearch = clearSearch
        } else {
            applyRoute(route, clearSearch)
        }
    }
    fun navigate(destination: Destination) = requestRoute(destinationPath(destination).map { it.name })
    fun openSetting(entry: SettingEntry) {
        focus.clearFocus(force = true)
        keyboard?.hide()
        // Opened over the results, even for a Voice or Dictionary page, so Back returns to them.
        requestRoute(searchResultRoute(entry.destination).map { it.name })
        if (entry.highlight) highlighter.show(entry.title)
    }
    // A row that isn't on screen right now (e.g. hidden by another setting) mustn't flash later.
    LaunchedEffect(highlighter.request) {
        val request = highlighter.request
        if (highlighter.target != null) {
            delay(3_000)
            if (!highlighter.found) highlighter.finish(request)
        }
    }
    LaunchedEffect(pendingDestination) {
        pendingDestination?.let { navigate(it); onDestinationConsumed() }
    }
    BackHandler(enabled = stack.size > 1) { requestRoute(stackNames.dropLast(1)) }
    if (onboarding) {
        WelcomeScreen(setup = setup, onSkip = { setupSkipped = true })
        return
    }
    val tabs = listOf(
        Triple(Destination.HOME, "Home", SlimIcons.Home),
        Triple(Destination.MODELS, "Voice", SlimIcons.Mic),
        Triple(Destination.DICTIONARY, "Dictionary", SlimIcons.Book),
        Triple(Destination.SETTINGS, "Settings", SlimIcons.Settings),
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Surface(
            Modifier.weight(1f).graphicsLayer {
                alpha = .5f + entry.value * .5f
                translationY = (1f - entry.value) * 8.dp.toPx()
            },
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    if (!home) CompactLowkeyHeader(
                        backLabel = if (stack.size > 1) "Back to ${stack[stack.lastIndex - 1].title}" else null,
                        onBack = { requestRoute(stackNames.dropLast(1)) },
                    )
                },
                bottomBar = {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // Give full labels room at large system text sizes instead of splitting words.
                        val rows = if (fontScale >= 1.5f && screenWidth < 600) tabs.chunked(2) else listOf(tabs)
                        rows.forEach { row ->
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                            ) {
                                row.forEach { (destination, label, icon) ->
                                    NavigationBarItem(
                                        selected = stack.first() == destination,
                                        // A tab starts afresh, but only once the switch happens (not on Keep editing).
                                        onClick = { requestRoute(destinationPath(destination).map { it.name }, clearSearch = true) },
                                        icon = { Icon(icon, null) },
                                        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (!home && current != Destination.MODELS) Text(
                        current.title, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
                    )
                    val modifier = Modifier.weight(1f).fillMaxSize()
                    CompositionLocalProvider(LocalSettingHighlighter provides highlighter) { when (current) {
                        Destination.HOME -> HomeScreen(modifier, ::navigate, ::openTest, ime)
                        Destination.MODELS -> ModelsScreen(modifier, ::navigate, ::openTest)
                        Destination.DICTIONARY -> DictionaryScreen(modifier)
                        Destination.SETTINGS -> SettingsScreen(
                            modifier, SettingsFocus.TOP, ::navigate, ::openResize,
                            search = SettingsSearch(settingsQuery, { settingsQuery = it }, ::openSetting),
                        )
                        Destination.SETTINGS_VOICE -> SettingsScreen(modifier, SettingsFocus.VOICE, ::navigate, ::openResize)
                        Destination.SETTINGS_KEYBOARDS -> SettingsScreen(modifier, SettingsFocus.KEYBOARDS, ::navigate, ::openResize)
                        Destination.SETTINGS_KEYBOARD -> SettingsScreen(modifier, SettingsFocus.KEYBOARD, ::navigate, ::openResize)
                        Destination.SETTINGS_TYPING -> SettingsScreen(modifier, SettingsFocus.TYPING, ::navigate)
                        Destination.SETTINGS_CUSTOMIZE -> SettingsScreen(modifier, SettingsFocus.CUSTOMIZE, ::navigate)
                        Destination.SETTINGS_CLIPBOARD -> SettingsScreen(modifier, SettingsFocus.CLIPBOARD, ::navigate)
                        Destination.SETTINGS_APPEARANCE -> SettingsScreen(modifier, SettingsFocus.APPEARANCE, ::navigate)
                        Destination.SETTINGS_STORAGE -> SettingsScreen(modifier, SettingsFocus.STORAGE, ::navigate)
                        Destination.SETTINGS_UPDATES -> SettingsScreen(modifier, SettingsFocus.UPDATES, ::navigate)
                        Destination.TRY_IT -> TryItScreen(modifier)
                        Destination.DIAGNOSTICS -> DiagnosticsScreen(modifier)
                        Destination.ABOUT -> AboutScreen(modifier)
                        Destination.LANGUAGES -> LanguagesScreen(modifier, ::navigate)
                        Destination.KEYBOARD_THEME -> KeyboardThemeScreen(modifier, onDirtyChange = { themeHasChanges = it })
                    } }
                }
            }
        }
    }
    if (testOpen) KeyboardTestSheet(testDraft, { testDraft = it }, ::closeTest,
        onSetupVoice = { navigate(Destination.MODELS) }, resizeKeyboard = resizeKeyboard)
    pendingRoute?.let { route ->
        DiscardThemeChangesDialog(
            onDiscard = {
                pendingRoute = null
                themeHasChanges = false
                applyRoute(route, pendingClearsSearch)
            },
            onKeepEditing = { pendingRoute = null },
        )
    }
}
