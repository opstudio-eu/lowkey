package com.rheerkens.androidkeyboard.companion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.R
import com.rheerkens.androidkeyboard.companion.screens.*
import com.rheerkens.androidkeyboard.companion.theme.Brand
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.ui.SlimIcons

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
    var onboarding by remember { mutableStateOf(false) }
    var setupSkipped by rememberSaveable { mutableStateOf(false) }
    var entered by rememberSaveable { mutableStateOf(false) }
    val entry = remember { Animatable(if (entered) 1f else 0f) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dark = MaterialTheme.colorScheme.isDark
    val fontScale = LocalDensity.current.fontScale
    val surround = if (dark) MaterialTheme.colorScheme.surfaceContainerLowest else Brand.Blue

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
    fun navigate(destination: Destination) {
        if (testOpen) closeTest()
        stackNames = destinationPath(destination).map { it.name }
    }
    LaunchedEffect(pendingDestination) {
        pendingDestination?.let { navigate(it); onDestinationConsumed() }
    }
    BackHandler(enabled = stack.size > 1) { stackNames = stackNames.dropLast(1) }
    val tabs = listOf(
        Triple(Destination.HOME, "Home", SlimIcons.Home),
        Triple(Destination.MODELS, "Voice", SlimIcons.Mic),
        Triple(Destination.DICTIONARY, "Dictionary", SlimIcons.Book),
        Triple(Destination.SETTINGS, "Settings", SlimIcons.Settings),
    )
    val canTest = current in setOf(
        Destination.MODELS, Destination.SETTINGS_KEYBOARD, Destination.SETTINGS_TYPING,
        Destination.SETTINGS_CUSTOMIZE, Destination.SETTINGS_KEYBOARDS, Destination.SETTINGS_VOICE,
        Destination.LANGUAGES, Destination.TRY_IT, Destination.KEYBOARD_THEME,
    )

    Column(Modifier.fillMaxSize().background(surround).statusBarsPadding().navigationBarsPadding()) {
        if (current == Destination.HOME) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp)
                    .graphicsLayer { alpha = .5f + entry.value * .5f },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.opstudio_logo_blue), null, Modifier.size(36.dp, 30.dp), tint = Color.White)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("OpStudio", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("LOWKEY", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Surface(
            Modifier.weight(1f).padding(horizontal = 6.dp).graphicsLayer {
                alpha = .5f + entry.value * .5f
                translationY = (1f - entry.value) * 8.dp.toPx()
            },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    if (current != Destination.HOME) TopAppBar(
                        title = { Text(current.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 28.sp)) },
                        expandedHeight = (64 * fontScale.coerceAtLeast(1f)).dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        navigationIcon = {
                            if (stack.size > 1) IconButton(onClick = { stackNames = stackNames.dropLast(1) }) {
                                Icon(SlimIcons.Back, "Back to ${stack[stack.lastIndex - 1].title}")
                            }
                        },
                        actions = {
                            if (canTest) IconButton(onClick = ::openTest) { Icon(SlimIcons.Keyboard, "Test keyboard") }
                        },
                    )
                },
                bottomBar = {
                    if (!(current == Destination.HOME && onboarding)) Column {
                        // Give full labels room at large system text sizes instead of splitting words.
                        val rows = if (fontScale >= 1.5f) tabs.chunked(2) else listOf(tabs)
                        rows.forEach { row ->
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                            ) {
                                row.forEach { (destination, label, icon) ->
                                    NavigationBarItem(
                                        selected = stack.first() == destination,
                                        onClick = { navigate(destination) },
                                        icon = { Icon(icon, null) },
                                        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                val modifier = Modifier.padding(padding).fillMaxSize()
                when (current) {
                    Destination.HOME -> HomeScreen(modifier, ::navigate, ::openTest, setupSkipped, { setupSkipped = true }) { onboarding = it }
                    Destination.MODELS -> ModelsScreen(modifier, ::navigate)
                    Destination.DICTIONARY -> DictionaryScreen(modifier)
                    Destination.SETTINGS -> SettingsScreen(modifier, SettingsFocus.TOP, ::navigate, ::openResize)
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
                    Destination.KEYBOARD_THEME -> KeyboardThemeScreen(modifier)
                }
            }
        }
    }
    if (testOpen) KeyboardTestSheet(current.title, testDraft, { testDraft = it }, ::closeTest, resizeKeyboard)
}
