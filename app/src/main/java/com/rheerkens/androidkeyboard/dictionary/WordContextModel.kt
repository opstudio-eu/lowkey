package com.rheerkens.androidkeyboard.dictionary

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * What you type after which words, learned on the phone when you turn it on: how often each word
 * followed the word before it, and the two before it, within a sentence. Also the words whose
 * autocorrection you undid, so they stay as typed for good. Feeds next-word predictions and the
 * order of completions ([PersonalPredictions]).
 *
 * Bounded: past [maxEntries] word pairs, every count halves and the faint ones are dropped, so
 * old habits fade and the model stays a few megabytes. Words are matched without case; each keeps
 * the spelling it was last typed with (`Lowkey`, `iPhone`). Only words with a word before them in
 * their sentence are learned, so a sentence's capital never sticks to an ordinary word.
 */
class WordContextModel(private val maxEntries: Int = MAX_ENTRIES) {
    private class Entry(var spelling: String, var count: Float)

    /** Next words (lowercase) per context: one previous word, or two joined by a space (lowercase). */
    private val contexts = LinkedHashMap<String, LinkedHashMap<String, Entry>>()
    private var entries = 0
    private val rejected = LinkedHashSet<String>()

    @get:Synchronized
    val size: Int get() = entries

    @get:Synchronized
    val isEmpty: Boolean get() = entries == 0 && rejected.isEmpty()

    /** Learns that [word] followed [previous] (the words before it in its sentence, nearest last). */
    @Synchronized
    fun learn(previous: List<String>, word: String) {
        if (previous.isEmpty() || !looksLikeWord(word)) return
        for (context in keys(previous)) {
            val next = contexts.getOrPut(context) { LinkedHashMap() }
            val entry = next[word.lowercase()]
            if (entry == null) {
                next[word.lowercase()] = Entry(word, 1f)
                entries++
            } else {
                entry.count += 1f
                entry.spelling = word
            }
        }
        if (entries > maxEntries) fade()
    }

    /** Takes back one [learn] of [word] after [previous] (an autocorrection the user undid). */
    @Synchronized
    fun unlearn(previous: List<String>, word: String) {
        for (context in keys(previous)) {
            val next = contexts[context] ?: continue
            val entry = next[word.lowercase()] ?: continue
            entry.count -= 1f
            if (entry.count <= 0f) {
                next.remove(word.lowercase())
                entries--
                if (next.isEmpty()) contexts.remove(context)
            }
        }
    }

    /** Remembers that the user undid the autocorrection of [word]: it is never corrected again. */
    @Synchronized
    fun reject(word: String) {
        rejected.remove(word.lowercase())
        rejected += word.lowercase()
        while (rejected.size > MAX_REJECTED) rejected.remove(rejected.first())
    }

    @Synchronized
    fun isRejected(word: String): Boolean = word.lowercase() in rejected

    /**
     * Up to [limit] words that followed [previous] (nearest last), starting with [prefix] but
     * longer, best first. Two matching previous words count [TWO_WORD_WEIGHT] times one.
     */
    @Synchronized
    fun next(previous: List<String>, prefix: String = "", limit: Int = 3): List<String> {
        if (previous.isEmpty() || limit <= 0) return emptyList()
        val p = prefix.lowercase()
        val scores = LinkedHashMap<String, Float>()
        val spellings = HashMap<String, String>()
        keys(previous).forEachIndexed { i, context ->
            val weight = if (i == 0) 1f else TWO_WORD_WEIGHT
            contexts[context]?.forEach { (lower, entry) ->
                if (lower.length > p.length && lower.startsWith(p)) {
                    scores[lower] = (scores[lower] ?: 0f) + weight * entry.count
                    spellings[lower] = entry.spelling
                }
            }
        }
        return scores.entries.sortedByDescending { it.value }.take(limit).map { spellings.getValue(it.key) }
    }

    @Synchronized
    fun clear() {
        contexts.clear()
        rejected.clear()
        entries = 0
    }

    /** Halves every count and drops those that fall below one, until well under the limit. */
    private fun fade() {
        while (entries > maxEntries * 3 / 4) {
            val emptied = ArrayList<String>()
            for ((context, next) in contexts) {
                val it = next.values.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    entry.count /= 2f
                    if (entry.count < 1f) { it.remove(); entries-- }
                }
                if (next.isEmpty()) emptied += context
            }
            emptied.forEach(contexts::remove)
        }
    }

    @Synchronized
    fun write(out: OutputStream) {
        val data = DataOutputStream(out.buffered())
        data.writeInt(MAGIC)
        data.writeInt(rejected.size)
        rejected.forEach(data::writeUTF)
        data.writeInt(contexts.size)
        for ((context, next) in contexts) {
            data.writeUTF(context)
            data.writeInt(next.size)
            for ((lower, entry) in next) {
                data.writeUTF(lower)
                data.writeUTF(entry.spelling)
                data.writeFloat(entry.count)
            }
        }
        data.flush()
    }

    companion object {
        /** Word pairs kept before old counts fade. */
        const val MAX_ENTRIES = 50_000
        /** Undone corrections kept; the oldest go first. */
        const val MAX_REJECTED = 2_000
        /** Weight of a match on the two previous words over a match on one. */
        const val TWO_WORD_WEIGHT = 2f
        private const val MAGIC = 0x4C4B5701 // "LKW", version 1

        /** A model saved by [write], or an empty one if [input] holds something else. Throws if damaged. */
        fun read(input: InputStream, maxEntries: Int = MAX_ENTRIES): WordContextModel {
            val model = WordContextModel(maxEntries)
            val data = DataInputStream(input.buffered())
            if (data.readInt() != MAGIC) return model
            repeat(data.readInt()) { model.rejected += data.readUTF() }
            repeat(data.readInt()) {
                val next = LinkedHashMap<String, Entry>()
                model.contexts[data.readUTF()] = next
                repeat(data.readInt()) { next[data.readUTF()] = Entry(data.readUTF(), data.readFloat()) }
                model.entries += next.size
            }
            return model
        }

        /** The contexts [previous] gives: the last word, then the last two (lowercase). */
        private fun keys(previous: List<String>): List<String> {
            val last = previous.last().lowercase()
            return if (previous.size >= 2) listOf(last, previous[previous.size - 2].lowercase() + " " + last) else listOf(last)
        }

        private fun looksLikeWord(word: String) =
            word.isNotEmpty() && word.length <= 48 && word.all { it.isLetter() || it == '\'' || it == '’' || it == '-' }
    }
}
