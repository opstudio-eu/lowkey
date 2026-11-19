package com.rheerkens.androidkeyboard.keyboard

/**
 * Pending taps commit in press order when thumbs overlap. Established long presses opt out.
 * Starting the next tap accepts the previous tap, so a later slide/cancel cannot retract it.
 * Keys that act on touch-down or change what the keys mean (Backspace, Shift, the layer and emoji
 * keys) take part too, so that a letter pressed before them is typed before they act.
 */
class KeyRollover {
    private val pending = LinkedHashMap<Any, () -> Unit>()

    fun press(owner: Any, commit: () -> Unit) {
        flush()
        pending[owner] = commit
    }

    /** Commits every pending tap, in press order. */
    fun flush() {
        val earlier = pending.values.toList()
        pending.clear()
        earlier.forEach { it() }
    }

    fun release(owner: Any) { pending.remove(owner)?.invoke() }
    fun cancel(owner: Any) { pending.remove(owner) }
}
