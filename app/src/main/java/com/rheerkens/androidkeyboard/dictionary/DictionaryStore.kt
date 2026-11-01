package com.rheerkens.androidkeyboard.dictionary

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted personal dictionary: exact-spelling terms plus speech phrase rules.
 * Stored as JSON in private SharedPreferences. Seeded with the spec's examples on first run.
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

    private fun load(): DictionaryState {
        val raw = prefs.getString(KEY, null) ?: return seed().also { save(it) }
        return try {
            val root = JSONObject(raw)
            val terms = root.getJSONArray("terms").let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    PersonalTerm(o.getLong("id"), o.getString("text"), o.getLong("addedAt"))
                }
            }
            val rules = root.getJSONArray("rules").let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    PhraseRule(o.getLong("id"), o.getString("spoken"), o.getString("written"), o.getBoolean("enabled"))
                }
            }
            DictionaryState(terms, rules)
        } catch (_: org.json.JSONException) {
            seed()
        }
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

    private fun seed(): DictionaryState {
        val now = clock()
        // Older timestamps for later entries so "T3 Code" is the newest seeded term.
        val terms = listOf("T3 Code", "coroutineScope", "adb", "ViewModel", "Jetpack Compose")
            .mapIndexed { i, t -> PersonalTerm(i + 1L, t, now - i * 1000L) }
        val rules = listOf(
            "three code" to "T3 Code",
            "t3 code" to "T3 Code",
            "view model" to "ViewModel",
            "a d b" to "adb",
        ).mapIndexed { i, (s, w) -> PhraseRule(100L + i, s, w, enabled = true) }
        return DictionaryState(terms, rules)
    }

    private companion object {
        const val KEY = "dictionary_v1"
        const val MAX_TERM_LENGTH = 48
    }
}
