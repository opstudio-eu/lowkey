package com.rheerkens.androidkeyboard.dictionary

/**
 * Full word lists by language id, built in the background and kept for the [capacity] languages
 * used most recently (each holds about 200,000 words). Pure Kotlin, so the load order is testable.
 *
 * - Lists are built one at a time by [execute], which must run its jobs in order on one thread at
 *   a time: a list being built holds its words twice, and several at once could exhaust the heap.
 * - A queued load is skipped once its language is no longer among the [capacity] asked for most
 *   recently, and a finished list is only published while it is still wanted, so a stale load
 *   never pushes a wanted list out.
 * - [invalidate] (a list deleted or replaced) bumps the language's generation: a load of an older
 *   generation is discarded, and a request for the new one is never swallowed by it.
 */
class LexiconCache(
    private val capacity: Int,
    private val execute: (Runnable) -> Unit,
    /** Builds [KeyboardLanguage]'s full list, or returns null if it can't. Runs inside [execute]. */
    private val build: (KeyboardLanguage) -> Lexicon?,
    /** Called after a list is published, so results computed on a fallback can be redone. */
    private val onPublished: (KeyboardLanguage) -> Unit = {},
) {
    private val lock = Any()
    /** Access-ordered: the eldest entry is the least recently used. */
    private val lexicons = LinkedHashMap<String, Lexicon>(capacity + 1, 0.75f, true)
    /** The languages asked for most recently, newest last. */
    private val wanted = ArrayDeque<String>()
    /** Generation of the load queued or running per language. */
    private val loading = HashMap<String, Int>()
    private val generations = HashMap<String, Int>()

    /** The full list of [id] if it is built, marking it as just used. */
    fun get(id: String): Lexicon? = synchronized(lock) { lexicons[id] }

    /** Asks for [language]'s list: marks it as wanted, and queues a load unless it's built or on its way. */
    fun preload(language: KeyboardLanguage) {
        val id = language.id
        val generation = synchronized(lock) {
            want(id)
            if (lexicons[id] != null) return // the lookup also marks it as just used
            val generation = generations[id] ?: 0
            if (loading[id] == generation) return
            loading[id] = generation
            generation
        }
        execute(Runnable { run(language, generation) })
    }

    /** Drops [id]'s list (deleted or replaced); a load of the old one in progress is discarded. */
    fun invalidate(id: String) = synchronized(lock) {
        lexicons.remove(id)
        generations[id] = (generations[id] ?: 0) + 1
    }

    private fun want(id: String) {
        wanted.remove(id)
        wanted.addLast(id)
        while (wanted.size > capacity) wanted.removeFirst()
    }

    private fun run(language: KeyboardLanguage, generation: Int) {
        val id = language.id
        try {
            // Passed over while queued behind other loads: it would only push a wanted list out.
            if (synchronized(lock) { id !in wanted || (generations[id] ?: 0) != generation }) return
            val lexicon = build(language) ?: return
            val published = synchronized(lock) {
                val current = (generations[id] ?: 0) == generation && id in wanted
                if (current) {
                    lexicons[id] = lexicon
                    // Evict the least recently used lists, never one that is still wanted while
                    // another isn't.
                    while (lexicons.size > capacity) {
                        val victim = lexicons.keys.firstOrNull { it !in wanted } ?: lexicons.keys.first()
                        lexicons.remove(victim)
                    }
                }
                current
            }
            if (published) onPublished(language)
        } finally {
            // Only this load's own entry: a newer generation's load may already be queued.
            synchronized(lock) { if (loading[id] == generation) loading.remove(id) }
        }
    }
}
