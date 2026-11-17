package com.rheerkens.androidkeyboard.keyboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ShiftState { OFF, ONCE, LOCKED }

data class KeyboardState(
    val layer: Layer = Layer.LETTERS,
    val shift: ShiftState = ShiftState.OFF,
    /** Language and letter arrangement. */
    val keyboard: TypingKeyboard,
    /** The number pad of the focused field, or null in a text field. */
    val pad: NumberPad? = null,
) {
    /** The layer the field opens on: its number pad, or the letters. */
    val home: Layer get() = if (pad != null) Layer.NUMBERS else Layer.LETTERS
}

/** Shift, caps lock, layer, and typing-keyboard state for keys mode. No Android dependencies. */
class KeyboardController(
    initialKeyboard: TypingKeyboard,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(KeyboardState(keyboard = initialKeyboard))
    val state: StateFlow<KeyboardState> = _state.asStateFlow()

    private var lastShiftTapMs = 0L

    /**
     * Shift is on for one capital because the user tapped it, not through auto-capitalization or
     * caps lock. A slide from Space then selects text, as arrow keys do with Shift held.
     */
    var shiftTapped = false
        private set

    /** Tap: one capital. Double-tap: caps lock. Tap while on/locked: off. */
    fun onShiftTap() {
        val now = clock()
        val doubleTap = now - lastShiftTapMs < DOUBLE_TAP_MS
        lastShiftTapMs = now
        _state.update {
            val next = when {
                doubleTap && it.shift == ShiftState.ONCE -> ShiftState.LOCKED
                it.shift == ShiftState.OFF -> ShiftState.ONCE
                else -> ShiftState.OFF
            }
            it.copy(shift = next)
        }
        shiftTapped = _state.value.shift == ShiftState.ONCE
    }

    /** Called after a character is typed; one-shot shift turns off. */
    fun onCharTyped() {
        shiftTapped = false
        _state.update { if (it.shift == ShiftState.ONCE) it.copy(shift = ShiftState.OFF) else it }
    }

    /** Applies sentence auto-capitalization unless caps lock is on. */
    fun setAutoShift(capitalize: Boolean) {
        shiftTapped = false
        _state.update {
            when {
                it.shift == ShiftState.LOCKED || it.layer != Layer.LETTERS -> it
                capitalize -> it.copy(shift = ShiftState.ONCE)
                else -> it.copy(shift = ShiftState.OFF)
            }
        }
    }

    fun setLayer(layer: Layer) = _state.update { it.copy(layer = layer) }

    /** A new field opens: on its number [pad] if it has one, else on the letters. */
    fun reset(pad: NumberPad? = null) {
        shiftTapped = false
        _state.update {
            it.copy(pad = pad, layer = if (pad != null) Layer.NUMBERS else Layer.LETTERS, shift = ShiftState.OFF)
        }
    }

    /** Back to the field's own keys ([KeyboardState.home]). */
    fun showHome() = _state.update { it.copy(layer = it.home) }

    /** After Space or a suggestion, a symbol layer returns to the field's own keys. */
    fun leaveSymbols() = _state.update {
        if (it.layer == Layer.SYMBOLS || it.layer == Layer.SYMBOLS_2) it.copy(layer = it.home) else it
    }

    /**
     * Switches to the keyboard after the current one in [order] (the usable keyboards, in globe
     * order) and returns it.
     */
    fun cycleKeyboard(order: List<TypingKeyboard>): TypingKeyboard {
        if (order.isEmpty()) return _state.value.keyboard
        _state.update {
            // A keyboard that is no longer enabled continues from the start of the cycle.
            val i = order.indexOf(it.keyboard)
            it.copy(keyboard = order[(i + 1) % order.size])
        }
        return _state.value.keyboard
    }

    fun setKeyboard(keyboard: TypingKeyboard) = _state.update { it.copy(keyboard = keyboard) }

    fun applyCase(text: String): String =
        if (_state.value.shift != ShiftState.OFF && _state.value.layer == Layer.LETTERS) shiftCase(text) else text

    private companion object {
        const val DOUBLE_TAP_MS = 350L
    }
}

/** Uppercases each character on its own, so `ß` stays one letter instead of becoming `SS`. */
fun shiftCase(text: String): String = buildString(text.length) { text.forEach { append(it.uppercaseChar()) } }
