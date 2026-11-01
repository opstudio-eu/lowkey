package com.rheerkens.androidkeyboard.voice

/** Short status notes for the one-row voice panel and the keys-mode strip. */
object VoiceNotes {
    /** `6 words`, `6 words · 1 rule`, `No speech`, `Cancelled · 4 words discarded`. */
    fun outcome(outcome: VoiceOutcome): String = when (outcome) {
        is VoiceOutcome.Inserted -> buildString {
            append(words(outcome.words))
            if (outcome.rulesApplied > 0) append(" · ").append(plural(outcome.rulesApplied, "rule"))
        }
        VoiceOutcome.NoSpeech -> "No speech"
        is VoiceOutcome.Notice -> outcome.text
        is VoiceOutcome.Cancelled -> when {
            outcome.error != null -> "Error · ${outcome.error}"
            outcome.leftInEditor -> "Stopped · ${words(outcome.wordsDiscarded)} left in the field"
            else -> cancelled(outcome.wordsDiscarded)
        }
    }

    fun cancelled(wordsDiscarded: Int): String =
        if (wordsDiscarded == 0) "Cancelled" else "Cancelled · ${words(wordsDiscarded)} discarded"

    /** `0:07`, `1:05`. */
    fun elapsed(ms: Long): String {
        val secs = (ms.coerceAtLeast(0) / 1000).toInt()
        return "%d:%02d".format(secs / 60, secs % 60)
    }

    private fun words(n: Int) = plural(n, "word")
    private fun plural(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"
}
