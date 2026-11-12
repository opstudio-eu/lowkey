package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.min

enum class PlacementMode { NORMAL, ONE_HANDED, FLOATING, SPLIT }

enum class PlacementProfile(val large: Boolean) {
    COMPACT_PORTRAIT(false), COMPACT_LANDSCAPE(false), LARGE_PORTRAIT(true), LARGE_LANDSCAPE(true);
    companion object {
        fun forSize(width: Float, height: Float): PlacementProfile = when {
            min(width, height) >= 600f -> if (width > height) LARGE_LANDSCAPE else LARGE_PORTRAIT
            else -> if (width > height) COMPACT_LANDSCAPE else COMPACT_PORTRAIT
        }
    }
}

/** Persistent dimensions are dp; floating coordinates are fractions of remaining travel. */
data class KeyboardPlacement(
    val mode: PlacementMode = PlacementMode.NORMAL,
    val automatic: Boolean = false,
    val rowHeight: Float = 48f,
    val bottomSpace: Float = 0f,
    val oneHandWidth: Float = .8f,
    val rightHand: Boolean = true,
    val splitGap: Float = -1f,
    val floatingWidth: Float = 320f,
    val floatingRowHeight: Float = 48f,
    val floatingX: Float = .5f,
    val floatingY: Float = .7f,
) {
    fun frame(width: Float, height: Float, compactHeight: Float? = null): PlacementFrame {
        val w = width.safe(360f).coerceAtLeast(1f)
        val h = height.safe(600f).coerceAtLeast(1f)
        val actualMode = if (mode == PlacementMode.SPLIT && w < 480f) PlacementMode.NORMAL else mode
        val floating = actualMode == PlacementMode.FLOATING
        val row = (if (floating) floatingRowHeight else rowHeight).safe(48f)
            .coerceIn(32f, min(88f, ((h * .7f - 49f) / 4f).coerceAtLeast(32f)))
        val panelHeight = (compactHeight ?: (49f + row * 4f)).coerceIn(1f, h)
        val panelWidth = when (actualMode) {
            PlacementMode.ONE_HANDED -> (w * oneHandWidth.safe(.8f)).coerceIn(min(260f, w), (w - 44f).coerceAtLeast(min(260f, w)))
            PlacementMode.FLOATING -> floatingWidth.safe(320f).coerceIn(min(260f, w), w)
            else -> w
        }
        val gap = if (actualMode == PlacementMode.SPLIT)
            splitGap.takeIf { it.isFinite() && it >= 0f }?.coerceIn(16f, w * .4f)
                ?: (w - 560f).coerceIn(24f, w * .4f)
            else 0f
        val left = when (actualMode) {
            PlacementMode.ONE_HANDED -> if (rightHand) w - panelWidth else 0f
            PlacementMode.FLOATING -> (w - panelWidth) * floatingX.safe(.5f).coerceIn(0f, 1f)
            else -> 0f
        }
        val top = if (floating) (h - panelHeight) * floatingY.safe(.7f).coerceIn(0f, 1f)
            else h - panelHeight - bottomSpace.safe(0f).coerceIn(0f, h - panelHeight)
        return PlacementFrame(left, top, panelWidth, panelHeight, row, gap, actualMode)
    }
}

data class PlacementFrame(val left: Float, val top: Float, val width: Float, val height: Float,
    val rowHeight: Float, val splitGap: Float, val mode: PlacementMode)

data class PlacementPreferences(
    val automaticSplit: Boolean = false,
    val profiles: Map<PlacementProfile, KeyboardPlacement> = emptyMap(),
    val legacyRowHeight: Float = 48f,
) {
    fun get(profile: PlacementProfile): KeyboardPlacement = profiles[profile] ?: KeyboardPlacement(
        mode = if (automaticSplit && profile.large) PlacementMode.SPLIT else PlacementMode.NORMAL,
        automatic = automaticSplit && profile.large, rowHeight = legacyRowHeight,
        floatingRowHeight = legacyRowHeight,
    )
    fun update(profile: PlacementProfile, change: (KeyboardPlacement) -> KeyboardPlacement) =
        copy(profiles = profiles + (profile to change(get(profile))))
    fun select(profile: PlacementProfile, mode: PlacementMode) = update(profile) {
        it.copy(mode = mode, automatic = false)
    }
    fun withAutomaticSplit(enabled: Boolean): PlacementPreferences {
        if (enabled == automaticSplit) return this
        val updated = profiles.mapValues { (profile, value) ->
            when {
                enabled && profile.large && value.mode == PlacementMode.NORMAL -> value.copy(mode = PlacementMode.SPLIT, automatic = true)
                !enabled && value.automatic -> value.copy(mode = PlacementMode.NORMAL, automatic = false)
                else -> value
            }
        }
        return copy(automaticSplit = enabled, profiles = updated)
    }
    fun resetGeometry() = copy(profiles = profiles.mapValues { (_, p) ->
        KeyboardPlacement(mode = p.mode, automatic = p.automatic)
    }, legacyRowHeight = 48f)

    fun encode(): String = PlacementCodec.encode(this)
    companion object {
        fun decode(value: String?, legacyRowHeight: Float) = PlacementCodec.decode(value, legacyRowHeight)
    }
}

internal fun Float.safe(fallback: Float): Float = if (isFinite()) this else fallback
