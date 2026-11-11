package com.rheerkens.androidkeyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Load order of [LexiconCache], driven step by step with a manual executor. */
class LexiconCacheTest {
    private val queue = ArrayDeque<Runnable>()
    private val built = mutableListOf<String>()
    private val published = mutableListOf<String>()
    private var failing = emptySet<String>()
    private val cache = LexiconCache(
        capacity = 3,
        execute = { queue.addLast(it) },
        build = { language ->
            built += language.id
            if (language.id in failing) null else Lexicon.fromWords(listOf(language.id))
        },
        onPublished = { published += it.id },
    )

    private fun lang(id: String) = TestLanguages.language(id)
    private fun runNext() = queue.removeFirst().run()
    private fun runAll() { while (queue.isNotEmpty()) runNext() }

    @Test
    fun loadsOnceAndPublishes() {
        cache.preload(lang("pl"))
        cache.preload(lang("pl"))
        assertEquals(1, queue.size)
        runAll()
        assertNotNull(cache.get("pl"))
        assertEquals(listOf("pl"), published)
        cache.preload(lang("pl"))
        assertTrue(queue.isEmpty())
    }

    @Test
    fun queuedLoadsNoLongerWantedAreSkipped() {
        // Cycling quickly through five languages: only the last three are built.
        for (id in listOf("de", "fr", "es", "it", "pl")) cache.preload(lang(id))
        runAll()
        assertEquals(listOf("es", "it", "pl"), built)
        assertNull(cache.get("de"))
    }

    @Test
    fun preloadingACachedListKeepsItFromEviction() {
        for (id in listOf("pl", "nl", "de")) cache.preload(lang(id))
        runAll()
        // French is asked for; then the user switches back to Polish before French is built.
        cache.preload(lang("fr"))
        cache.preload(lang("pl"))
        runAll()
        assertNotNull("Polish was just used and must stay", cache.get("pl"))
        assertNotNull(cache.get("fr"))
        assertNull("Dutch is the least recently used", cache.get("nl"))
    }

    @Test
    fun aListNoLongerWantedWhenBuiltIsNotPublished() {
        cache.preload(lang("de"))
        // While German is queued, three other languages are asked for.
        for (id in listOf("pl", "nl", "fr")) cache.preload(lang(id))
        runNext() // German's turn: skipped, it is no longer among the three wanted
        assertEquals(emptyList<String>(), built)
        runAll()
        assertEquals(listOf("pl", "nl", "fr"), published)
    }

    @Test
    fun aReplacementIsLoadedWhenTheOldLoadIsStillQueued() {
        cache.preload(lang("pl"))
        // Polish is deleted and downloaded again while its first load waits.
        cache.invalidate("pl")
        cache.preload(lang("pl"))
        assertEquals(2, queue.size)
        runNext() // the old generation: discarded
        assertNull(cache.get("pl"))
        runNext() // the new one
        assertNotNull(cache.get("pl"))
        assertEquals(listOf("pl"), published)
    }

    @Test
    fun aListDeletedWhileItIsBuiltIsDiscarded() {
        lateinit var cache: LexiconCache
        val jobs = ArrayDeque<Runnable>()
        cache = LexiconCache(3, { jobs.addLast(it) }, build = { language ->
            cache.invalidate(language.id) // deleted while its words are read
            Lexicon.fromWords(listOf(language.id))
        })
        cache.preload(lang("pl"))
        jobs.removeFirst().run()
        assertNull(cache.get("pl"))
    }

    @Test
    fun aFailedBuildCanBeRetried() {
        failing = setOf("pl")
        cache.preload(lang("pl"))
        runAll()
        assertNull(cache.get("pl"))
        failing = emptySet()
        cache.preload(lang("pl"))
        runAll()
        assertNotNull(cache.get("pl"))
    }

    @Test
    fun aBuiltListIsReturnedAsIs() {
        cache.preload(lang("pl"))
        runAll()
        assertSame(cache.get("pl"), cache.get("pl"))
    }
}
