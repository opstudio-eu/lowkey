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
fun ComponentActivity.applyThemeToWindow(dark: Boolean, brandSurround: Boolean = false) {
    val style = if (dark || brandSurround) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    val background = if (brandSurround && !dark) Brand.Blue else (if (dark) DarkScheme else LightScheme).background
    window.setBackgroundDrawable(background.toArgb().toDrawable())
}
