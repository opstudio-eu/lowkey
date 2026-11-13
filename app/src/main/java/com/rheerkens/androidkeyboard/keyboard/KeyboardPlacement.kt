package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.min

/** Space above the keyboard bar controls for grabbing the expand/minimize grip. */
const val KEYBOARD_GRIP_SPACE_DP = 8

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
    val dockedWidth: Float = 1f,
    val dockedX: Float = .5f,
) {
    /** Resize one edge while keeping the opposite edge fixed, including at the limits. */
    fun resizeVertical(width: Float, height: Float, delta: Float, bottom: Boolean, rows: Int = 4): KeyboardPlacement {
        val rowCount = rows.coerceIn(4, 5)
        val before = frame(width, height, rows = rowCount)
        val floating = before.mode == PlacementMode.FLOATING
        val room = if (bottom) height - before.top - before.height else before.top
        val requestedRow = (before.rowHeight + (if (bottom) delta else -delta) / rowCount)
            .coerceAtMost(before.rowHeight + room.coerceAtLeast(0f) / rowCount)
        val resized = if (floating) copy(floatingRowHeight = requestedRow) else copy(rowHeight = requestedRow)
        val after = resized.frame(width, height, rows = rowCount)
        val top = if (bottom) before.top else before.top + before.height - after.height
        return if (floating) resized.copy(floatingRowHeight = after.rowHeight,
            floatingY = (top / (height - after.height).coerceAtLeast(1f)).coerceIn(0f, 1f))
        else resized.copy(rowHeight = after.rowHeight, bottomSpace = (height - top - after.height).coerceAtLeast(0f))
    }

    fun move(width: Float, height: Float, dx: Float, dy: Float, rows: Int = 4): KeyboardPlacement {
        val before = frame(width, height, rows = rows)
        fun horizontal() = relativePosition(before.left + dx, width - before.width)
        return if (before.mode == PlacementMode.FLOATING) copy(
            floatingX = horizontal(),
            floatingY = relativePosition(before.top + dy, height - before.height),
        ) else copy(
            dockedX = if (before.mode == PlacementMode.NORMAL) horizontal() else dockedX,
            bottomSpace = (height - before.top - before.height - dy).coerceIn(0f, (height - before.height).coerceAtLeast(0f)),
        )
    }

    fun resizeWidth(width: Float, height: Float, delta: Float, fromLeft: Boolean = false): KeyboardPlacement {
        val before = frame(width, height)
        return when (before.mode) {
            PlacementMode.NORMAL, PlacementMode.FLOATING -> {
                val right = before.left + before.width
                val limit = if (fromLeft) right else width - before.left
                val nextWidth = (before.width + delta * if (fromLeft) -1f else 1f).coerceIn(min(260f, width), limit)
                val nextLeft = if (fromLeft) right - nextWidth else before.left
                val position = relativePosition(nextLeft, width - nextWidth)
                if (before.mode == PlacementMode.FLOATING) copy(floatingWidth = nextWidth, floatingX = position)
                else copy(dockedWidth = nextWidth / width, dockedX = position)
            }
            PlacementMode.ONE_HANDED -> copy(oneHandWidth =
                ((before.width + delta * if (rightHand) -1f else 1f) / width).coerceIn(.4f, .95f))
            else -> this
        }
    }

    /** Minimized bars use the viewport bottom without changing the saved expanded placement. */
    fun frame(width: Float, height: Float, compactHeight: Float? = null, rows: Int = 4, minimized: Boolean = false): PlacementFrame {
        val w = width.safe(360f).coerceAtLeast(1f)
        val h = height.safe(600f).coerceAtLeast(1f)
        val actualMode = if (mode == PlacementMode.SPLIT && w < 480f) PlacementMode.NORMAL else mode
        val floating = actualMode == PlacementMode.FLOATING
        val extra = if (floating && !minimized) 24f else 0f
        val rowCount = rows.coerceIn(4, 5)
        val barHeight = 49f + KEYBOARD_GRIP_SPACE_DP
        val maxRow = ((h * .7f - barHeight - extra) / rowCount).coerceIn(1f, 88f)
        val row = (if (floating) floatingRowHeight else rowHeight).safe(48f).coerceIn(min(32f, maxRow), maxRow)
        val expandedHeight = (barHeight + row * rowCount + extra).coerceAtMost(h)
        val panelHeight = (if (minimized) barHeight else compactHeight?.plus(extra) ?: expandedHeight).coerceIn(1f, h)
        val panelWidth = when (actualMode) {
            PlacementMode.NORMAL -> (w * dockedWidth.safe(1f)).coerceIn(min(260f, w), w)
            PlacementMode.ONE_HANDED -> (w * oneHandWidth.safe(.8f)).coerceIn(min(260f, w), (w - 44f).coerceAtLeast(min(260f, w)))
            PlacementMode.FLOATING -> floatingWidth.safe(320f).coerceIn(min(260f, w), w)
            else -> w
        }
        val gap = if (actualMode == PlacementMode.SPLIT)
            splitGap.takeIf { it.isFinite() && it >= 0f }?.coerceIn(16f, (w - 360f).coerceAtLeast(16f))
                ?: (w - 594f).coerceIn(24f, (w - 360f).coerceAtLeast(24f))
            else 0f
        val left = when (actualMode) {
            PlacementMode.NORMAL -> (w - panelWidth) * dockedX.safe(.5f).coerceIn(0f, 1f)
            PlacementMode.ONE_HANDED -> if (rightHand) w - panelWidth else 0f
            PlacementMode.FLOATING -> (w - panelWidth) * floatingX.safe(.5f).coerceIn(0f, 1f)
            else -> 0f
        }
        val top = when {
            minimized -> h - panelHeight
            floating -> ((h - expandedHeight) * floatingY.safe(.7f).coerceIn(0f, 1f)).coerceAtMost(h - panelHeight)
            else -> h - panelHeight - bottomSpace.safe(0f).coerceIn(0f, h - panelHeight)
        }
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

private fun relativePosition(at: Float, travel: Float) =
    if (travel <= 0f) .5f else (at / travel).coerceIn(0f, 1f)
