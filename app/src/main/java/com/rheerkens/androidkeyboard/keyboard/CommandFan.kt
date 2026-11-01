package com.rheerkens.androidkeyboard.keyboard

import kotlin.math.atan2
import kotlin.math.hypot

/** What the minimized bar's Backspace fan can do. */
enum class FanCommand {
    /** Hands over to the hold-and-slide word eraser. */
    WORDS,
    SPACE,
    CLEAR_CLIPBOARD,
    /** Brings back what the last Clear clips removed; takes Clear clips' place until something new is copied. */
    UNDO_CLEAR,
    /** The editor's own action (Send, Go, Search…), or a plain Enter key where it declares none. */
    ACTION,
    NEW_LINE,
    /** Full keys (letters) for this field only; the next field opens minimized again. */
    KEYBOARD_ONCE,
    /** Like [KEYBOARD_ONCE], opening on the number and symbol keys. */
    NUMBERS_ONCE,
}

/**
 * One wedge of the fan, centred on Backspace. Angles are in degrees counter-clockwise from the
 * right (90 = straight up, 180 = left); radii are in dp. [reach] is how far past [outer] the wedge
 * still catches a finger that overshoots; it is not drawn.
 */
data class FanSector(
    val command: FanCommand,
    val fromDeg: Float,
    val toDeg: Float,
    val inner: Float,
    val outer: Float,
    val reach: Float = outer,
)

/**
 * The Backspace command fan of the minimized bar: a quarter circle that opens up and to the left
 * of the key. Up erases words (the existing slider), up-left types a space (further out: clear the
 * clipboard history, or undo that), and left runs the editor's action (further out: a new line).
 * The outer ring opens the full keyboard for this field only: on the number keys up-left, on the
 * letters on the left.
 */
object CommandFan {
    /** The round Backspace button the fan opens around (its radius, dp). */
    const val BUTTON = 21f
    /** The wedges start here, just outside the button; releasing closer to its centre cancels. */
    const val DEAD_ZONE = BUTTON + 2f
    const val INNER = 106f
    const val OUTER = 158f
    const val THIRD = 210f
    /** Past this radius in the Words wedge, the word slider takes over. */
    const val WORDS_SLIDER = 46f
    /** Before the fan opens, sliding up within this angle of vertical starts the word slider, as before the fan existed. */
    const val SLIDER_CONE_DEG = 22f
    /** Overshooting the outermost ring still counts this much further. */
    private const val OVERSHOOT = 24f

    /** The Words wedge runs from here (just right of straight up) to [UP_LEFT]. */
    const val WORDS_FROM = 80f
    private const val UP_LEFT = 100f
    private const val LEFT = 136f
    private const val BELOW_LEFT = 186f

    /**
     * The wedges. Every one is always there, in the same place: after a clear, Undo clear takes
     * Clear clips' place, and New line stays even where the field can't take one (the fan dims it).
     * Words spans all three rings, so the fan's outer edge is one unbroken arc.
     */
    fun sectors(undoClear: Boolean = false): List<FanSector> = listOf(
        FanSector(FanCommand.WORDS, WORDS_FROM, UP_LEFT, DEAD_ZONE, THIRD, THIRD + OVERSHOOT),
        FanSector(FanCommand.SPACE, UP_LEFT, LEFT, DEAD_ZONE, INNER),
        FanSector(if (undoClear) FanCommand.UNDO_CLEAR else FanCommand.CLEAR_CLIPBOARD, UP_LEFT, LEFT, INNER, OUTER),
        FanSector(FanCommand.NUMBERS_ONCE, UP_LEFT, LEFT, OUTER, THIRD, THIRD + OVERSHOOT),
        FanSector(FanCommand.ACTION, LEFT, BELOW_LEFT, DEAD_ZONE, INNER),
        FanSector(FanCommand.NEW_LINE, LEFT, BELOW_LEFT, INNER, OUTER),
        FanSector(FanCommand.KEYBOARD_ONCE, LEFT, BELOW_LEFT, OUTER, THIRD, THIRD + OVERSHOOT),
    )

    /** Angle of a finger offset (screen coordinates: y grows downward), 0–360 degrees. */
    fun angleOf(dx: Float, dy: Float): Float {
        val a = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).toFloat()
        return if (a < 0) a + 360f else a
    }

    /**
     * The wedge under a finger [dx], [dy] dp from the button's centre, or null (cancel). A press
     * away from the centre widens the cancel zone ([deadZone]) so it can't start on a wedge.
     */
    fun hit(sectors: List<FanSector>, dx: Float, dy: Float, deadZone: Float = DEAD_ZONE): FanSector? {
        val r = hypot(dx, dy)
        if (r < maxOf(deadZone, DEAD_ZONE)) return null
        val a = angleOf(dx, dy)
        return sectors.firstOrNull { a >= it.fromDeg && a < it.toDeg && r >= it.inner && r < it.reach }
    }

    /** The cancel zone for a press [offset] dp from the button's centre: past the press, never inside the button. */
    fun deadZoneFor(offset: Float): Float = maxOf(DEAD_ZONE, offset + 6f)

    /** True when a finger [dx], [dy] dp from the press point is sliding up steeply enough to start the word slider directly. */
    fun isSliderStart(dx: Float, dy: Float, minUp: Float): Boolean {
        if (-dy <= minUp) return false
        return kotlin.math.abs(angleOf(dx, dy) - 90f) <= SLIDER_CONE_DEG
    }
}
