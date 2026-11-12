package com.rheerkens.androidkeyboard.keyboard

/**
 * Pending taps commit in press order when thumbs overlap. Established long presses opt out.
 * Starting the next tap accepts the previous tap, so a later slide/cancel cannot retract it.
 */
class KeyRollover {
    private val pending = LinkedHashMap<Any, () -> Unit>()

    fun press(owner: Any, commit: () -> Unit) {
        val earlier = pending.values.toList()
        pending.clear()
        earlier.forEach { it() }
        pending[owner] = commit
    }

    fun release(owner: Any) { pending.remove(owner)?.invoke() }
    fun cancel(owner: Any) { pending.remove(owner) }
}
