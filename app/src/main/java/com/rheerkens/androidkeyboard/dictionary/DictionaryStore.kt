package com.rheerkens.androidkeyboard.dictionary

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted personal dictionary: exact-spelling terms plus speech phrase rules.
 * Stored as JSON in private SharedPreferences. A new install starts empty.
 */
class DictionaryStore(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("dictionary", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DictionaryState> = _state.asStateFlow()

    sealed interface AddResult {
        data class Added(val term: PersonalTerm) : AddResult
        data object AlreadyExists : AddResult
        data object Invalid : AddResult
    }

    fun contains(text: String): Boolean = _state.value.terms.any { it.text == text.trim() }

    fun addTerm(text: String): AddResult {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_TERM_LENGTH) return AddResult.Invalid
        if (contains(clean)) return AddResult.AlreadyExists
        val term = PersonalTerm(nextId(), clean, clock())
        mutate { it.copy(terms = it.terms + term) }
        return AddResult.Added(term)
    }

    fun editTerm(id: Long, text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_TERM_LENGTH) return false
        if (_state.value.terms.any { it.text == clean && it.id != id }) return false
        mutate { s -> s.copy(terms = s.terms.map { if (it.id == id) it.copy(text = clean) else it }) }
        return true
    }

    fun deleteTerm(id: Long) = mutate { s -> s.copy(terms = s.terms.filterNot { it.id == id }) }

    fun addRule(spoken: String, written: String): Boolean {
        if (spoken.isBlank() || written.isBlank()) return false
        mutate { it.copy(rules = it.rules + PhraseRule(nextId(), spoken.trim(), written.trim())) }
        return true
    }

    fun editRule(id: Long, spoken: String, written: String): Boolean {
        if (spoken.isBlank() || written.isBlank()) return false
        mutate { s ->
            s.copy(rules = s.rules.map {
                if (it.id == id) it.copy(spoken = spoken.trim(), written = written.trim()) else it
            })
        }
        return true
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) = mutate { s ->
        s.copy(rules = s.rules.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun deleteRule(id: Long) = mutate { s -> s.copy(rules = s.rules.filterNot { it.id == id }) }

    private fun nextId(): Long {
        val s = _state.value
        return maxOf(s.terms.maxOfOrNull { it.id } ?: 0, s.rules.maxOfOrNull { it.id } ?: 0) + 1
    }

    private fun mutate(transform: (DictionaryState) -> DictionaryState) {
        _state.update(transform)
        save(_state.value)
    }

    /**
     * Reads the saved dictionary, skipping entries it can't read. If anything was unreadable, the
     * saved text is first copied to [KEY_UNREADABLE], because the next change overwrites [KEY].
     */
    private fun load(): DictionaryState {
        val raw = prefs.getString(KEY, null) ?: return DictionaryState(emptyList(), emptyList())
        var complete = true
        fun <T> entries(root: JSONObject?, name: String, read: (JSONObject) -> T): List<T> {
            val arr = root?.optJSONArray(name) ?: run { complete = false; return emptyList() }
            return (0 until arr.length()).mapNotNull { i ->
                try {
                    read(arr.getJSONObject(i))
                } catch (_: org.json.JSONException) {
                    complete = false
                    null
                }
            }
        }
        val root = try { JSONObject(raw) } catch (_: org.json.JSONException) { null }
        val terms = entries(root, "terms") { PersonalTerm(it.getLong("id"), it.getString("text"), it.getLong("addedAt")) }
        val rules = entries(root, "rules") {
            PhraseRule(it.getLong("id"), it.getString("spoken"), it.getString("written"), it.getBoolean("enabled"))
        }
        // Keep the first unreadable copy: it holds the words from before the damage.
        if (!complete && !prefs.contains(KEY_UNREADABLE)) {
            prefs.edit { putString(KEY_UNREADABLE, raw) }
            Log.w(TAG, "Saved dictionary was partly unreadable; kept a copy (${terms.size} terms, ${rules.size} rules read)")
        }
        return DictionaryState(terms, rules)
    }

    private fun save(state: DictionaryState) {
        val root = JSONObject()
        root.put("terms", JSONArray().apply {
            state.terms.forEach {
                put(JSONObject().put("id", it.id).put("text", it.text).put("addedAt", it.addedAt))
            }
        })
        root.put("rules", JSONArray().apply {
            state.rules.forEach {
                put(
                    JSONObject().put("id", it.id).put("spoken", it.spoken)
                        .put("written", it.written).put("enabled", it.enabled)
                )
            }
        })
        prefs.edit { putString(KEY, root.toString()) }
    }

    private companion object {
        const val TAG = "DictionaryStore"
        const val KEY = "dictionary_v1"
        const val KEY_UNREADABLE = "dictionary_v1_unreadable"
        const val MAX_TERM_LENGTH = 48
    }
}
