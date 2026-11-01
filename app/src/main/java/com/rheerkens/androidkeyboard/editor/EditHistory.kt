package com.rheerkens.androidkeyboard.editor

import androidx.compose.ui.geometry.Offset

/** Undo and redo over whole editor states. [record] the state before each change. */
class EditHistory<T> {
    private val undos = ArrayDeque<T>()
    private val redos = ArrayDeque<T>()

    val canUndo get() = undos.isNotEmpty()
    val canRedo get() = redos.isNotEmpty()

    /** Call with the state just before a change; a new change drops what could be redone. */
    fun record(before: T) {
        undos.addLast(before)
        if (undos.size > LIMIT) undos.removeFirst()
        redos.clear()
    }

    /** The state to go back to from [current], or null if there is none. */
    fun undo(current: T): T? = undos.removeLastOrNull()?.also { redos.addLast(current) }

    /** The state to go forward to from [current], or null if there is none. */
    fun redo(current: T): T? = redos.removeLastOrNull()?.also { undos.addLast(current) }

    private companion object {
        const val LIMIT = 100
    }
}

/**
 * Two-finger zoom on top of the fitted image: a view point s shows what the fitted image has at
 * (s - pan) / zoom. Zoom stays between 1 and [MAX] and the image keeps covering its fitted area.
 */
data class ViewZoom(val zoom: Float = 1f, val pan: Offset = Offset.Zero) {
    /** After a pinch step: scaled by [factor] about [centroid], then moved by [move], in a [width] × [height] view. */
    fun pinch(factor: Float, centroid: Offset, move: Offset, width: Float, height: Float): ViewZoom {
        val z = (zoom * factor).coerceIn(1f, MAX)
        val k = z / zoom
        val p = (pan - centroid) * k + centroid + move
        return ViewZoom(z, Offset(p.x.coerceIn(width * (1 - z), 0f), p.y.coerceIn(height * (1 - z), 0f)))
    }

    companion object {
        const val MAX = 8f
    }
}
