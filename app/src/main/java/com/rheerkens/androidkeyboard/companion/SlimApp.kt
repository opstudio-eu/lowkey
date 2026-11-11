package com.rheerkens.androidkeyboard.companion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.rheerkens.androidkeyboard.companion.screens.AboutScreen
import com.rheerkens.androidkeyboard.companion.screens.DiagnosticsScreen
import com.rheerkens.androidkeyboard.companion.screens.DictionaryScreen
import com.rheerkens.androidkeyboard.companion.screens.HomeScreen
import com.rheerkens.androidkeyboard.companion.screens.ModelsScreen
import com.rheerkens.androidkeyboard.companion.screens.SettingsScreen
import com.rheerkens.androidkeyboard.companion.screens.TryItScreen
import com.rheerkens.androidkeyboard.companion.screens.KeyboardTestSheet
import com.rheerkens.androidkeyboard.companion.screens.LanguagesScreen
import com.rheerkens.androidkeyboard.ui.SlimIcons

private val tabs = listOf(
    Triple(Destination.HOME, "Home", SlimIcons.Home),
    Triple(Destination.MODELS, "Models", SlimIcons.Models),
    Triple(Destination.DICTIONARY, "Dictionary", SlimIcons.Book),
    Triple(Destination.SETTINGS, "Settings", SlimIcons.Settings),
)

/**
 * Companion app shell: bottom navigation for Home, Models, Dictionary, and Settings, with
 * Try it, Diagnostics, and About pushed on a small back stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlimApp(pendingDestination: Destination?, onDestinationConsumed: () -> Unit) {
    var stackNames by rememberSaveable { mutableStateOf(listOf(Destination.HOME.name)) }
    val stack = stackNames.map { Destination.valueOf(it) }
    val current = stack.last()
    var testOpen by rememberSaveable { mutableStateOf(false) }
    var testDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun closeTest() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        testOpen = false
    }

    fun navigate(dest: Destination) {
        if (testOpen) closeTest()
        stackNames = when {
            dest.isTab -> listOf(dest.name)
            dest == Destination.SETTINGS_VOICE -> listOf(dest.name)
            else -> stackNames + dest.name
        }
    }

    LaunchedEffect(pendingDestination) {
        if (pendingDestination != null) {
            navigate(pendingDestination)
            onDestinationConsumed()
        }
    }
    BackHandler(enabled = stack.size > 1) { stackNames = stackNames.dropLast(1) }

    val rootTab = when (stack.first()) {
        Destination.SETTINGS_VOICE -> Destination.SETTINGS
        else -> stack.first()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    TextButton(onClick = {
                        focusManager.clearFocus(force = true)
                        testOpen = true
                    }) {
                        Icon(SlimIcons.Keyboard, null, Modifier.padding(end = 6.dp))
                        Text("Test keyboard")
                    }
                },
                navigationIcon = {
                    if (stack.size > 1) {
                        IconButton(onClick = { stackNames = stackNames.dropLast(1) }) {
                            Icon(SlimIcons.Back, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (dest, label, icon) ->
                    NavItem(selected = rootTab == dest, label = label, icon = icon) { navigate(dest) }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (current) {
            Destination.HOME -> HomeScreen(modifier, ::navigate)
            Destination.MODELS -> ModelsScreen(modifier)
            Destination.DICTIONARY -> DictionaryScreen(modifier)
            Destination.SETTINGS -> SettingsScreen(modifier, focusVoice = false, navigate = ::navigate)
            Destination.SETTINGS_VOICE -> SettingsScreen(modifier, focusVoice = true, navigate = ::navigate)
            Destination.TRY_IT -> TryItScreen(modifier)
            Destination.DIAGNOSTICS -> DiagnosticsScreen(modifier)
            Destination.ABOUT -> AboutScreen(modifier)
            Destination.LANGUAGES -> LanguagesScreen(modifier)
        }
    }
    if (testOpen) {
        val returnTo = when (current) {
            Destination.HOME -> "Home"
            Destination.MODELS -> "Models"
            Destination.ABOUT -> "About"
            else -> current.title
        }
        KeyboardTestSheet(returnTo, testDraft, { testDraft = it }, ::closeTest)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}
