package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.settings.TypingKeyboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ShiftState { OFF, ONCE, LOCKED }

data class KeyboardState(
    val layer: Layer = Layer.LETTERS,
    val shift: ShiftState = ShiftState.OFF,
    /** Language and letter arrangement. */
    val keyboard: TypingKeyboard = TypingKeyboard.DEFAULT,
)

/** Shift, caps lock, layer, and typing-keyboard state for keys mode. No Android dependencies. */
class KeyboardController(
    initialKeyboard: TypingKeyboard,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(KeyboardState(keyboard = initialKeyboard))
    val state: StateFlow<KeyboardState> = _state.asStateFlow()

    private var lastShiftTapMs = 0L

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
    }

    /** Called after a character is typed; one-shot shift turns off. */
    fun onCharTyped() {
        _state.update { if (it.shift == ShiftState.ONCE) it.copy(shift = ShiftState.OFF) else it }
    }

    /** Applies sentence auto-capitalization unless caps lock is on. */
    fun setAutoShift(capitalize: Boolean) {
        _state.update {
            when {
                it.shift == ShiftState.LOCKED || it.layer != Layer.LETTERS -> it
                capitalize -> it.copy(shift = ShiftState.ONCE)
                else -> it.copy(shift = ShiftState.OFF)
            }
        }
    }

    fun setLayer(layer: Layer) = _state.update { it.copy(layer = layer) }

    fun reset() = _state.update { it.copy(layer = Layer.LETTERS, shift = ShiftState.OFF) }

    /** Cycles to the next enabled keyboard (language, then arrangement order) and returns it. */
    fun cycleKeyboard(enabled: Collection<TypingKeyboard>): TypingKeyboard {
        val order = TypingKeyboard.ordered(enabled).ifEmpty { listOf(TypingKeyboard.DEFAULT) }
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
