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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.theme.SlimTheme
import com.rheerkens.androidkeyboard.companion.theme.isDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow

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
            }
            SlimTheme(settings.theme) {
                SlimApp(
                    pendingDestination = pending,
                    onDestinationConsumed = { pendingDestination.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        CompanionLinks.destinationOf(intent)?.let { pendingDestination.value = it }
    }
}
