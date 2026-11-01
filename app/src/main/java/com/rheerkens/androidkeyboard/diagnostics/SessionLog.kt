package com.rheerkens.androidkeyboard.diagnostics

import android.content.Context
import androidx.core.content.edit
import com.rheerkens.androidkeyboard.voice.VoiceSessionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Persisted voice session log for the Diagnostics page, newest first, capped at [MAX_ENTRIES]. */
class SessionLog(context: Context) {
    private val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<VoiceSessionRecord>> = _entries.asStateFlow()

    /** Session IDs keep increasing across keyboard restarts and log clears. */
    fun nextSessionId(): Long = prefs.getLong(K_NEXT_ID, 1L)

    fun reserveSessionIds(upTo: Long) = prefs.edit { putLong(K_NEXT_ID, maxOf(upTo + 1, nextSessionId())) }

    fun add(record: VoiceSessionRecord) {
        _entries.update { (listOf(record) + it).take(MAX_ENTRIES) }
        reserveSessionIds(record.sessionId)
        save()
    }

    fun clear() {
        _entries.value = emptyList()
        save()
    }

    private fun save() {
        val arr = JSONArray()
        _entries.value.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.sessionId)
                    .put("start", r.startedAtMs)
                    .put("transitions", JSONArray(r.transitions))
                    .put("words", r.words)
                    .put("latency", r.finalizeLatencyMs ?: -1L)
            )
        }
        prefs.edit { putString(K_LOG, arr.toString()) }
    }

    private fun load(): List<VoiceSessionRecord> {
        val raw = prefs.getString(K_LOG, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val t = o.getJSONArray("transitions")
                VoiceSessionRecord(
                    sessionId = o.getLong("id"),
                    startedAtMs = o.getLong("start"),
                    transitions = List(t.length()) { t.getString(it) },
                    words = o.getInt("words"),
                    finalizeLatencyMs = o.getLong("latency").takeIf { it >= 0 },
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    companion object {
        const val MAX_ENTRIES = 100
        private const val K_LOG = "sessions_v1"
        private const val K_NEXT_ID = "next_session_id"
    }
}
