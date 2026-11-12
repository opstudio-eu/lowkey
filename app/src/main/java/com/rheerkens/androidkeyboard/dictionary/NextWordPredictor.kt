package com.rheerkens.androidkeyboard.dictionary

/** Next-word suggestions from the current editor and explicitly saved phrases only. */
object NextWordPredictor {
    /** No learning or storage: only bounded text supplied by the currently focused editor. */
    const val CONTEXT_LIMIT = 2048

    fun suggest(before: String, terms: List<PersonalTerm>, limit: Int = 3): List<Suggestion> {
        if (limit <= 0 || before.lastOrNull()?.isWhitespace() != true) return emptyList()
        val history = tokens(before.takeLast(CONTEXT_LIMIT))
        if (history.lastOrNull() == null) return emptyList()
        val context = history.takeLastWhile { it != null }.takeLast(3).filterNotNull()
        val candidates = LinkedHashMap<String, Candidate>()
        fun collect(words: List<String?>, personal: Boolean) {
            for (i in 0 until words.lastIndex) {
                val next = words[i + 1] ?: continue
                var matched = 0
                while (matched < context.size && i - matched >= 0 &&
                    words[i - matched]?.equals(context[context.lastIndex - matched], ignoreCase = true) == true) matched++
                if (matched == 0) continue
                val key = next.lowercase()
                val old = candidates[key]
                candidates[key] = when {
                    old == null || matched > old.matched -> Candidate(next, matched, personal, 1)
                    matched == old.matched -> old.copy(word = if (personal) next else old.word,
                        personal = old.personal || personal, count = old.count + 1)
                    else -> old
                }
            }
        }
        collect(history, false)
        terms.forEach { collect(tokens(it.text), true) }
        return candidates.values.sortedWith(compareByDescending<Candidate> { it.matched }
            .thenByDescending { it.personal }.thenByDescending { it.count })
            .take(limit).map { Suggestion(it.word, isPersonal = it.personal) }
    }

    private data class Candidate(val word: String, val matched: Int, val personal: Boolean, val count: Int)
    private val TOKEN = Regex("\\S+|\\n")

    /** Nulls preserve sentence boundaries and prevent learning fragments of URLs or addresses. */
    private fun tokens(text: String): List<String?> = buildList {
        for (match in TOKEN.findAll(text)) {
            val raw = match.value
            val word = raw.trim('"', '\'', '“', '”', '‘', '’', '(', ')', '[', ']', '{', '}', ',', '.', '!', '?', ';', ':')
            add(word.takeIf { it.isNotEmpty() && it.all { ch -> ch.isLetter() || ch == '\'' || ch == '’' } })
            if (raw.trimEnd('"', '\'', '”', '’', ')', ']', '}').lastOrNull() in listOf('.', '!', '?', ';', ':')) add(null)
        }
    }
}
