package com.rheerkens.androidkeyboard.companion.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable

/**
 * Matches the status and navigation bar icons and the window background to the in-app theme, not
 * only the system one: the window background otherwise follows the system night mode
 * (values-night), so Light or Dark would show the other theme behind the content.
 */
fun ComponentActivity.applyThemeToWindow(dark: Boolean) {
    val style = if (dark) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    window.setBackgroundDrawable((if (dark) DarkScheme else LightScheme).background.toArgb().toDrawable())
}
