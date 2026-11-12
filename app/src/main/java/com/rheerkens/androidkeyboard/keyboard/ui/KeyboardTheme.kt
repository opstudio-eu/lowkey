package com.rheerkens.androidkeyboard.keyboard.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.rheerkens.androidkeyboard.companion.theme.AA_NON_TEXT
import com.rheerkens.androidkeyboard.companion.theme.AA_TEXT
import com.rheerkens.androidkeyboard.companion.theme.bestInk
import com.rheerkens.androidkeyboard.companion.theme.contrast
import com.rheerkens.androidkeyboard.companion.theme.prefersLightInk
import kotlin.math.min
import kotlin.math.roundToInt

/** The five colours a custom keyboard theme is made of. */
enum class SeedRole(val label: String) {
    BACKGROUND("Background"),
    KEYS("Keys"),
    FUNCTION_KEYS("Function keys"),
    TEXT("Text"),
    ACCENT("Accent"),
}

/** A custom keyboard palette's five opaque colours; [deriveKeyboardColors] fills in the rest. */
@Immutable
data class KeyboardSeed(
    val background: Color,
    val keys: Color,
    val functionKeys: Color,
    val text: Color,
    val accent: Color,
) {
    init {
        require(SeedRole.entries.all { this[it].alpha == 1f }) { "keyboard colours must be opaque" }
    }

    operator fun get(role: SeedRole): Color = when (role) {
        SeedRole.BACKGROUND -> background
        SeedRole.KEYS -> keys
        SeedRole.FUNCTION_KEYS -> functionKeys
        SeedRole.TEXT -> text
        SeedRole.ACCENT -> accent
    }

    fun with(role: SeedRole, color: Color): KeyboardSeed = when (role) {
        SeedRole.BACKGROUND -> copy(background = color)
        SeedRole.KEYS -> copy(keys = color)
        SeedRole.FUNCTION_KEYS -> copy(functionKeys = color)
        SeedRole.TEXT -> copy(text = color)
        SeedRole.ACCENT -> copy(accent = color)
    }

    /** "RRGGBB,RRGGBB,…" in [SeedRole] order. */
    fun encode(): String = SeedRole.entries.joinToString(",") { this[it].toHex() }

    companion object {
        /** The seed colours of a whole palette, such as a standard one. */
        fun of(colors: KeyboardColors) =
            KeyboardSeed(colors.background, colors.key, colors.keyMod, colors.keyText, colors.enter)

        /** Reads [encode]'s form; null for anything else. */
        fun decode(stored: String?): KeyboardSeed? {
            val colors = stored?.split(',')?.map { parseHexColor(it) ?: return null } ?: return null
            if (colors.size != SeedRole.entries.size) return null
            return KeyboardSeed(colors[0], colors[1], colors[2], colors[3], colors[4])
        }
    }
}

/**
 * The custom theme: five [seed] colours, optional [overrides] for every other role, and an optional
 * background [image] (a file name in the theme store's folder) dimmed by [dim] percent.
 */
@Immutable
data class CustomKeyboardTheme(
    val seed: KeyboardSeed,
    val overrides: Map<ColorRole, Color> = emptyMap(),
    val image: String? = null,
    val dim: Int = DEFAULT_DIM,
) {
    init {
        require(overrides.keys.all { it.seed == null }) { "seed roles are changed through the seed" }
        require(overrides.values.all { it.alpha == 1f }) { "keyboard colours must be opaque" }
        require(dim in MIN_DIM..MAX_DIM) { "dimming out of range: $dim" }
        require(image == null || isBackgroundFileName(image)) { "not a background file name" }
    }

    /** The palette the keyboard draws: derived from the seeds, then each override in place of its role. */
    fun effectiveColors(): KeyboardColors = deriveKeyboardColors(seed).withOverrides(overrides)

    /** This theme with [role] set to [color], or back to automatic when [color] is null. */
    fun with(role: ColorRole, color: Color?): CustomKeyboardTheme {
        val s = role.seed
        return when {
            s != null -> copy(seed = seed.with(s, color ?: seed[s]))
            color == null -> copy(overrides = overrides - role)
            else -> copy(overrides = overrides + (role to color))
        }
    }

    /** The luminance range of the dimmed image as drawn, or null without an image or its [sample]. */
    fun imageRange(sample: ImageSample?): LuminanceRange? =
        if (image != null && sample != null) sample.range(seed.background, dim) else null

    /** Contrast failures of the effective palette, with text on the background judged over the image. */
    fun contrastFailures(sample: ImageSample?): List<ContrastCheck> =
        keyboardContrastFailures(effectiveColors(), imageRange(sample))
}

/** Whether the custom theme is on, and the custom theme itself (kept while it is off). */
data class KeyboardTheme(val enabled: Boolean = false, val custom: CustomKeyboardTheme? = null) {
    /** The custom seed colours, if any were saved. */
    val seed: KeyboardSeed? get() = custom?.seed
}

/** File names the theme store writes for background images: `bg-` and 32 hex digits. */
fun isBackgroundFileName(name: String): Boolean = BACKGROUND_FILE.matches(name)

private val BACKGROUND_FILE = Regex("bg-[0-9a-f]{32}\\.(jpg|png)")

/** The standard palette for the app's theme. */
fun standardKeyboardColors(appDark: Boolean): KeyboardColors = if (appDark) DarkKeyboardColors else LightKeyboardColors

/** The palette the keyboard draws with: the custom one while it is on, else the standard one. */
fun resolveKeyboardColors(theme: KeyboardTheme, appDark: Boolean): KeyboardColors {
    val custom = theme.custom
    return if (theme.enabled && custom != null) custom.effectiveColors() else standardKeyboardColors(appDark)
}

/** Six hex digits, optionally after `#`. Alpha is not accepted: keyboard colours are opaque. */
fun parseHexColor(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6 || !digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or digits.toLong(16))
}

/** "RRGGBB", upper case, without `#`. */
fun Color.toHex(): String = "%02X%02X%02X".format(red8(), green8(), blue8())

fun Color.red8(): Int = (red * 255).roundToInt()
fun Color.green8(): Int = (green * 255).roundToInt()
fun Color.blue8(): Int = (blue * 255).roundToInt()

/** [a] moved [t] (0 to 1) of the way to [b], per sRGB channel and rounded to 8 bits. */
internal fun mix(a: Color, b: Color, t: Float): Color {
    fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
    return Color(ch(a.red8(), b.red8()), ch(a.green8(), b.green8()), ch(a.blue8(), b.blue8()))
}

/** The ink's ratio on a derived surface may drop to [minimum], or to what it has on the base surface if that is lower. */
private fun floorOf(ink: Color, base: Color, minimum: Double) = min(minimum, contrast(ink, base))

/**
 * [surface] moved toward [target] as far as possible, up to [maxT], while [ink] stays readable on
 * it (see [floorOf]). Returns [surface] itself when there is no room.
 */
private fun toward(surface: Color, target: Color, ink: Color, maxT: Float, minimum: Double = AA_TEXT): Color {
    val floor = floorOf(ink, surface, minimum)
    var t = maxT
    while (t > 0.001f) {
        val c = mix(surface, target, t)
        if (contrast(ink, c) >= floor) return c
        t -= 0.02f
    }
    return surface
}

/**
 * A pressed or active fill for [surface] carrying [ink]: toward the ink like the standard palettes,
 * or away from it when that would leave the ink unreadable.
 */
private fun pressedOf(surface: Color, ink: Color, target: Color = ink, amount: Float = 0.22f): Color {
    val t = toward(surface, target, ink, amount)
    if (t != surface && contrast(t, surface) >= 1.12) return t
    val away = if (prefersLightInk(ink)) Color.White else Color.Black
    return mix(surface, away, 0.2f)
}

/**
 * [text] faded toward [toward] by up to [maxT] for a quieter secondary look, but only as far as it
 * stays readable on every one of [surfaces].
 */
private fun faded(text: Color, toward: Color, maxT: Float, surfaces: List<Color>): Color {
    var t = maxT
    while (t > 0.001f) {
        val c = mix(text, toward, t)
        if (surfaces.all { contrast(c, it) >= floorOf(text, it, AA_TEXT) }) return c
        t -= 0.02f
    }
    return text
}

/** Black or white, whichever reads better on both [a] and [b]. */
private fun bestInkOn(a: Color, b: Color): Color =
    listOf(Color.White, Color.Black).maxBy { min(contrast(it, a), contrast(it, b)) }

/**
 * Every keyboard colour role from the five [seed] colours. The seeds themselves are used as they
 * are; the derived roles stay readable where the seeds allow it. A standard palette's seeds give
 * that palette back unchanged.
 */
fun deriveKeyboardColors(seed: KeyboardSeed): KeyboardColors {
    for (standard in listOf(DarkKeyboardColors, LightKeyboardColors)) {
        if (KeyboardSeed.of(standard) == seed) return standard
    }
    val bg = seed.background
    val key = seed.keys
    val mod = seed.functionKeys
    val text = seed.text
    val accent = seed.accent

    val keyPressed = pressedOf(key, text)
    val keyOn = pressedOf(mod, text, target = accent, amount = 0.5f)
    val pill = mix(bg, key, 0.5f)
    val popup = mix(bg, key, 0.4f)
    val busy = mix(bg, text, 0.14f)
    val enterInk = bestInk(accent)
    val enterPressed = pressedOf(accent, enterInk)
    val stopInk = if (contrast(bg, text) >= AA_TEXT) bg else bestInk(text)
    val stopPressed = pressedOf(text, stopInk)
    val muted = faded(text, bg, 0.3f, listOf(bg, key, mod, pill, popup, busy, keyPressed))
    val status = statusColors(bg, pill, popup)

    return KeyboardColors(
        background = bg,
        divider = mix(bg, text, 0.12f),
        key = key,
        keyMod = mod,
        keyPressed = keyPressed,
        keyOn = keyOn,
        keyText = text,
        spaceText = faded(text, key, 0.3f, listOf(key, keyPressed)),
        muted = muted,
        chipText = faded(text, bg, 0.12f, listOf(bg, popup, keyPressed, mod)),
        chipSaved = text,
        chipDivider = mix(bg, text, 0.15f),
        enter = accent,
        enterPressed = enterPressed,
        enterInk = if (contrast(enterInk, enterPressed) >= AA_TEXT) enterInk else bestInkOn(accent, enterPressed),
        green = status.green,
        amber = status.amber,
        toggleVoiceBg = pill,
        toggleVoiceRing = mix(bg, text, 0.3f),
        stop = text,
        stopPressed = stopPressed,
        stopInk = stopInk,
        busy = busy,
        pill = pill,
        popup = popup,
        popupBorder = mix(popup, text, 0.22f),
        danger = status.danger,
        dangerInk = status.dangerInk,
    )
}

/**
 * Green, amber and danger from the standard dark or light palette, whichever reads better on the
 * custom background, model button and popups. Their meaning is kept; only the variant is chosen.
 */
private fun statusColors(bg: Color, pill: Color, popup: Color): KeyboardColors {
    fun score(p: KeyboardColors): Double = listOf(
        contrast(p.amber, bg) / AA_TEXT,
        contrast(p.amber, pill) / AA_NON_TEXT,
        contrast(p.green, bg) / AA_NON_TEXT,
        contrast(p.green, pill) / AA_NON_TEXT,
        contrast(p.danger, popup) / AA_NON_TEXT,
    ).min()
    val preferred = if (prefersLightInk(bg)) DarkKeyboardColors else LightKeyboardColors
    val other = if (preferred === DarkKeyboardColors) LightKeyboardColors else DarkKeyboardColors
    return if (score(other) > score(preferred) + 1e-9) other else preferred
}

/** The result of [fixContrast]: the adjusted theme and the pairs it still fails. */
data class ContrastFix(val theme: CustomKeyboardTheme, val remaining: List<ContrastCheck>) {
    val seed: KeyboardSeed get() = theme.seed
    val resolvesAll: Boolean get() = remaining.isEmpty()
}

/** [fixContrast] for seeds alone: no overrides, no image. */
fun fixContrast(seed: KeyboardSeed): ContrastFix = fixContrast(CustomKeyboardTheme(seed), null)

/**
 * Moves seed colours toward black or white, text first, until every pair of the effective theme
 * passes (overrides and, with [sample], text over the image included) or nothing improves further.
 * Each step takes the smallest move that gives the best result, so colours that are fine stay as
 * they are. Overrides and the image are never changed, so failures that involve them, or conflicting
 * seeds, may remain; [ContrastFix.remaining] lists them.
 */
fun fixContrast(theme: CustomKeyboardTheme, sample: ImageSample?): ContrastFix {
    // Only the background seed changes the dimmed image's range, so remember it per background.
    val ranges = HashMap<Color, LuminanceRange?>()
    fun failures(t: CustomKeyboardTheme) =
        keyboardContrastFailures(t.effectiveColors(), ranges.getOrPut(t.seed.background) { t.imageRange(sample) })
    fun score(f: List<ContrastCheck>) = f.size * 100.0 + f.sumOf { it.deficit }

    var current = theme
    var currentFailures = failures(theme)
    val order = listOf(SeedRole.TEXT, SeedRole.ACCENT, SeedRole.FUNCTION_KEYS, SeedRole.KEYS, SeedRole.BACKGROUND)
    repeat(8) {
        if (currentFailures.isEmpty()) return ContrastFix(current, currentFailures)
        var best: Pair<CustomKeyboardTheme, List<ContrastCheck>>? = null
        var bestScore = score(currentFailures)
        for (role in order) for (target in listOf(Color.White, Color.Black)) {
            for (step in 1..25) {
                val candidate = current.copy(seed = current.seed.with(role, mix(current.seed[role], target, step / 25f)))
                val f = failures(candidate)
                val s = score(f)
                if (s < bestScore - 1e-6) {
                    bestScore = s
                    best = candidate to f
                }
                if (f.isEmpty()) break
            }
        }
        val next = best ?: return ContrastFix(current, currentFailures)
        current = next.first
        currentFailures = next.second
    }
    return ContrastFix(current, currentFailures)
}

/**
 * The smallest dimming above the theme's own that makes every pair over the image pass, or null when
 * none fails over the image or even [MAX_DIM] is not enough.
 */
fun dimmingForImage(theme: CustomKeyboardTheme, sample: ImageSample?): Int? {
    if (theme.image == null || sample == null) return null
    val colors = theme.effectiveColors()
    fun overImageFails(dim: Int) =
        keyboardContrastFailures(colors, sample.range(theme.seed.background, dim)).any { it.overImage }
    if (!overImageFails(theme.dim)) return null
    return (theme.dim + 1..MAX_DIM).firstOrNull { !overImageFails(it) }
}
