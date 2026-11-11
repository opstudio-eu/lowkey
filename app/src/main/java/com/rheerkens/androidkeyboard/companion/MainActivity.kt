package com.rheerkens.androidkeyboard.companion

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.theme.DarkScheme
import com.rheerkens.androidkeyboard.companion.theme.LightScheme
import com.rheerkens.androidkeyboard.companion.theme.SlimTheme
import com.rheerkens.androidkeyboard.companion.theme.isDarkTheme
import com.rheerkens.androidkeyboard.updates.Updates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Companion app host. Also the input method's settings activity. */
class MainActivity : ComponentActivity() {
    /** Destination requested by an intent (launch or onNewIntent), consumed by the UI. */
    private val pendingDestination = MutableStateFlow<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        if (savedInstanceState == null) pendingDestination.value = CompanionLinks.destinationOf(intent)
        setContent {
            val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
            val pending by pendingDestination.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings.theme)
            LaunchedEffect(dark) {
                // Match status/navigation bar icons to the in-app theme, not only the system one.
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                // The window background follows the system night mode (values-night); match the
                // in-app choice so Light or Dark never shows the other theme behind the content.
                window.setBackgroundDrawable((if (dark) DarkScheme else LightScheme).background.toArgb().toDrawable())
            }
            SlimTheme(settings.theme) {
                SlimApp(
                    pendingDestination = pending,
                    onDestinationConsumed = { pendingDestination.value = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app also checks when the daily background check hasn't run lately; the
        // result shows on Home, so no notification.
        if (AppGraph.settings.current.checkForUpdates && AppGraph.updates.checkDue(Updates.APP_OPEN_CHECK_AFTER_MILLIS)) {
            AppGraph.appScope.launch { AppGraph.updates.check(userStarted = false, notify = false) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        CompanionLinks.destinationOf(intent)?.let { pendingDestination.value = it }
    }
}
