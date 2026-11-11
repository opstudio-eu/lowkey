package com.rheerkens.androidkeyboard.keyboard

import com.rheerkens.androidkeyboard.dictionary.KeyboardLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ShiftState { OFF, ONCE, LOCKED }

data class KeyboardState(
    val layer: Layer = Layer.LETTERS,
    val shift: ShiftState = ShiftState.OFF,
    val language: KeyboardLanguage,
)

/** Shift, caps lock, layer, and keyboard-language state for keys mode. No Android dependencies. */
class KeyboardController(
    initialLanguage: KeyboardLanguage,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(KeyboardState(language = initialLanguage))
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

    /**
     * Switches to the language after the current one in [order] (the usable languages, in
     * catalog order) and returns it. A current language missing from [order] goes to its first.
     */
    fun cycleLanguage(order: List<KeyboardLanguage>): KeyboardLanguage {
        if (order.isEmpty()) return _state.value.language
        _state.update {
            val i = order.indexOfFirst { l -> l.id == it.language.id }
            it.copy(language = order[(i + 1) % order.size])
        }
        return _state.value.language
    }

    fun setLanguage(language: KeyboardLanguage) = _state.update { it.copy(language = language) }

    fun applyCase(text: String): String =
        if (_state.value.shift != ShiftState.OFF && _state.value.layer == Layer.LETTERS) shiftCase(text) else text

    private companion object {
        const val DOUBLE_TAP_MS = 350L
    }
}

/** Uppercases each character on its own, so `ß` stays one letter instead of becoming `SS`. */
fun shiftCase(text: String): String = buildString(text.length) { text.forEach { append(it.uppercaseChar()) } }
