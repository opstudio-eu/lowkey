package com.rheerkens.androidkeyboard.dictionary

/**
 * Applies enabled [PhraseRule]s to final speech text in a single left-to-right pass.
 *
 * - Matching is case-insensitive and whitespace-tolerant (`a  d b` matches `a d b`).
 * - Matches must sit on word boundaries, so `adb` does not match inside `adbd`.
 * - Longer spoken phrases win over shorter overlapping ones.
 * - Output of one rule is never re-matched, so rules apply exactly once.
 */
object PhraseRuleApplier {

    data class Result(val text: String, val appliedCount: Int)

    fun apply(text: String, rules: List<PhraseRule>): Result {
        val active = rules
            .filter { it.enabled && it.spoken.isNotBlank() }
            .associateBy { normalize(it.spoken) }
        if (active.isEmpty() || text.isEmpty()) return Result(text, 0)

        val alternatives = active.keys
            .sortedByDescending { it.length }
            .joinToString("|") { spoken ->
                spoken.split(' ').joinToString("\\s+") { Regex.escape(it) }
            }
        val regex = Regex(
            "(?<![\\p{L}\\p{N}_])(?:$alternatives)(?![\\p{L}\\p{N}_])",
            RegexOption.IGNORE_CASE,
        )
        var count = 0
        val out = regex.replace(text) { match ->
            val rule = active[normalize(match.value)]
            if (rule == null) {
                match.value
            } else {
                count++
                rule.written
            }
        }
        return Result(out, count)
    }

    private fun normalize(s: String): String =
        s.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
}
