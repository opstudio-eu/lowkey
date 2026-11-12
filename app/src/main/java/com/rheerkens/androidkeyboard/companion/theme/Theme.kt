package com.rheerkens.androidkeyboard.companion.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.R
import com.rheerkens.androidkeyboard.settings.ThemeMode

/**
 * OpStudio's brand colours. The companion app and keyboard palettes are derived from these:
 * dark on light ([Dark] on [Light]) for the light theme, light blue on dark for the dark theme.
 * Dynamic colour stays off so the brand shows.
 */
object Brand {
    val Blue = Color(0xFF17659C)
    val LightBlue = Color(0xFF83C9F4)
    val Sky = Color(0xFF2C99D6)
    val AccentRed = Color(0xFFEB2F33)
    val Dark = Color(0xFF101E2B)
    val Light = Color(0xFFF4F8FB)
    val White = Color(0xFFFFFFFF)
}

/** Whether [mode] means the dark theme, given whether the system is in dark mode. */
fun resolveDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** The OpStudio logo variant for the theme: blue on light backgrounds, light blue on dark ones. */
@DrawableRes
fun opStudioLogo(dark: Boolean): Int = if (dark) R.drawable.opstudio_logo_light_blue else R.drawable.opstudio_logo_blue

/** True for a dark scheme, read from its background so it follows whichever scheme is applied. */
val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Brand.LightBlue,
    onPrimary = Brand.Dark,
    primaryContainer = Brand.Blue,
    onPrimaryContainer = Brand.White,
    inversePrimary = Brand.Blue,
    // Sky #2C99D6, lightened: navigation indicator, tonal buttons, done and active marks.
    secondary = Color(0xFF9AD3F5),
    onSecondary = Brand.Dark,
    secondaryContainer = Color(0xFF1F4A6B),
    onSecondaryContainer = Color(0xFFE3F2FC),
    // Warning amber (not a brand colour; kept so warnings stay distinct from errors).
    tertiary = Color(0xFFF2C46E),
    onTertiary = Color(0xFF3D2A00),
    tertiaryContainer = Color(0xFF473A1B),
    onTertiaryContainer = Color(0xFFFFE3A8),
    background = Brand.Dark,
    onBackground = Color(0xFFEAF2F8),
    surface = Brand.Dark,
    onSurface = Color(0xFFEAF2F8),
    surfaceVariant = Color(0xFF223547),
    onSurfaceVariant = Color(0xFFA7BCCD),
    surfaceTint = Brand.LightBlue,
    inverseSurface = Color(0xFFEAF2F8),
    inverseOnSurface = Brand.Dark,
    surfaceContainerLowest = Color(0xFF0A1621),
    surfaceContainerLow = Color(0xFF142433),
    surfaceContainer = Color(0xFF182A3A),
    surfaceContainerHigh = Color(0xFF1E3243),
    surfaceContainerHighest = Color(0xFF253A4D),
    outline = Color(0xFF6B8296),
    outlineVariant = Color(0xFF2B4053),
    // The accent red, lightened for text on the dark surfaces.
    error = Color(0xFFFF8A8C),
    onError = Color(0xFF5C0A0C),
    errorContainer = Color(0xFF7A1418),
    onErrorContainer = Color(0xFFFFDAD9),
)

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Brand.White,
    primaryContainer = Color(0xFFD3E8F7),
    onPrimaryContainer = Brand.Dark,
    inversePrimary = Brand.LightBlue,
    // Sky #2C99D6, darkened for text on light surfaces.
    secondary = Color(0xFF185F8B),
    onSecondary = Brand.White,
    secondaryContainer = Color(0xFFD6ECF8),
    onSecondaryContainer = Color(0xFF0B3A5C),
    // Warning amber (not a brand colour; kept so warnings stay distinct from errors).
    tertiary = Color(0xFF7A5300),
    onTertiary = Brand.White,
    tertiaryContainer = Color(0xFFFFE3A8),
    onTertiaryContainer = Color(0xFF3D2A00),
    background = Brand.Light,
    onBackground = Brand.Dark,
    surface = Brand.Light,
    onSurface = Brand.Dark,
    surfaceVariant = Color(0xFFDCE6EE),
    onSurfaceVariant = Color(0xFF44576A),
    surfaceTint = Brand.Blue,
    inverseSurface = Brand.Dark,
    inverseOnSurface = Color(0xFFEAF2F8),
    surfaceContainerLowest = Brand.White,
    surfaceContainerLow = Color(0xFFEDF3F8),
    surfaceContainer = Color(0xFFE6EEF4),
    surfaceContainerHigh = Color(0xFFE0E9F0),
    surfaceContainerHighest = Color(0xFFD8E2EA),
    outline = Color(0xFF6E8091),
    outlineVariant = Color(0xFFC5D2DD),
    // The accent red, darkened for text on the light surfaces.
    error = Color(0xFFC2161D),
    onError = Brand.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF5C0A0C),
)

@Composable
fun SlimTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    val base = if (dark) DarkScheme else LightScheme
    val headline = FontFamily(Font(R.font.bricolage_semibold, FontWeight.SemiBold))
    val defaults = Typography()
    MaterialTheme(
        colorScheme = base.copy(
            surface = if (dark) Brand.Dark else Brand.White,
            surfaceTint = Color.Transparent,
            surfaceContainer = if (dark) base.surfaceContainer else Color(0xFFF6F9FC),
        ),
        typography = defaults.copy(
            headlineMedium = defaults.headlineMedium.copy(fontFamily = headline, fontWeight = FontWeight.SemiBold),
            titleLarge = defaults.titleLarge.copy(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
        ),
        content = content,
    )
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = resolveDark(mode, isSystemInDarkTheme())
