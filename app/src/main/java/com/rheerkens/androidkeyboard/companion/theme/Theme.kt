package com.rheerkens.androidkeyboard.companion.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.settings.ThemeMode

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C7FA),
    onPrimary = Color(0xFF0C1F3D),
    primaryContainer = Color(0xFF2C4A6C),
    onPrimaryContainer = Color(0xFFE6F0FB),
    secondary = Color(0xFF5CD193),
    onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF1E3A2C),
    onSecondaryContainer = Color(0xFFBFF3D6),
    tertiary = Color(0xFFF2C46E),
    onTertiary = Color(0xFF3D2A00),
    tertiaryContainer = Color(0xFF4A3A16),
    onTertiaryContainer = Color(0xFFFFE3A8),
    background = Color(0xFF0E141B),
    onBackground = Color(0xFFE9EEF3),
    surface = Color(0xFF0E141B),
    onSurface = Color(0xFFE9EEF3),
    surfaceVariant = Color(0xFF222C38),
    onSurfaceVariant = Color(0xFF9EACBA),
    surfaceContainerLowest = Color(0xFF0A0F14),
    surfaceContainerLow = Color(0xFF141B23),
    surfaceContainer = Color(0xFF18212B),
    surfaceContainerHigh = Color(0xFF1E2833),
    surfaceContainerHighest = Color(0xFF26313D),
    outline = Color(0xFF4A5A6B),
    outlineVariant = Color(0xFF28333F),
    error = Color(0xFFF2A5A5),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2F5FA7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E5FB),
    onPrimaryContainer = Color(0xFF0C1F3D),
    secondary = Color(0xFF1C8A55),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEFDC),
    onSecondaryContainer = Color(0xFF00391F),
    tertiary = Color(0xFF8A5E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3A8),
    onTertiaryContainer = Color(0xFF3D2A00),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF192630),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF192630),
    surfaceVariant = Color(0xFFE1E6EC),
    onSurfaceVariant = Color(0xFF485862),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F2F6),
    surfaceContainer = Color(0xFFEAEDF2),
    surfaceContainerHigh = Color(0xFFE4E8ED),
    surfaceContainerHighest = Color(0xFFDDE2E8),
    outline = Color(0xFF7A8794),
    outlineVariant = Color(0xFFC9D0D8),
)

@Composable
fun SlimTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
