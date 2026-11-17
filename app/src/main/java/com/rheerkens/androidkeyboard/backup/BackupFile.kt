package com.rheerkens.androidkeyboard.backup

import com.rheerkens.androidkeyboard.dictionary.PersonalTerm
import com.rheerkens.androidkeyboard.dictionary.PhraseRule
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What a backup holds: saved words, speech rules, and every setting as stored. */
data class BackupContents(
    val terms: List<PersonalTerm>,
    val rules: List<PhraseRule>,
    /** Stored settings: Boolean, Int, Long, Float, String or a set of strings, by key. */
    val settings: Map<String, Any>,
)

/**
 * The backup file the user saves and opens with Android's file picker: readable JSON, so it can
 * be checked or kept in any folder. Word lists, speech models and the keyboard theme's photo are
 * not in it; they download or are chosen again.
 */
object BackupFile {
    const val MIME = "application/json"
    const val NAME = "lowkey-backup.json"
    private const val FORMAT = "lowkey-backup"
    private const val VERSION = 1
    /** A backup is small; anything larger is not one. */
    private const val MAX_BYTES = 4 shl 20

    fun write(out: OutputStream, contents: BackupContents, exportedAt: Long) {
        val settings = JSONObject()
        fun group(name: String) = settings.optJSONObject(name) ?: JSONObject().also { settings.put(name, it) }
        for ((key, value) in contents.settings.toSortedMap()) when (value) {
            is Boolean -> group("boolean").put(key, value)
            is Int -> group("int").put(key, value)
            is Long -> group("long").put(key, value)
            is Float -> group("float").put(key, value.toDouble())
            is String -> group("string").put(key, value)
            is Set<*> -> group("stringSet").put(key, JSONArray(value.filterIsInstance<String>().sorted()))
        }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("words", JSONArray().apply { contents.terms.forEach { put(JSONObject().put("text", it.text).put("addedAt", it.addedAt)) } })
            .put("speechRules", JSONArray().apply {
                contents.rules.forEach { put(JSONObject().put("spoken", it.spoken).put("written", it.written).put("enabled", it.enabled)) }
            })
            .put("settings", settings)
        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** The contents of a backup file. Throws [IllegalArgumentException] for a file that isn't one. */
    fun read(input: InputStream): BackupContents {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
            require(buffer.size() <= MAX_BYTES) { "This file is too large to be a Lowkey backup" }
        }
        val bytes = buffer.toByteArray()
        try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            require(root.optString("format") == FORMAT) { "This file is not a Lowkey backup" }
            require(root.optInt("version") in 1..VERSION) { "This backup is from a newer version of Lowkey" }
            val words = root.optJSONArray("words") ?: JSONArray()
            val terms = List(words.length()) { i ->
                words.getJSONObject(i).let { PersonalTerm(0, it.getString("text"), it.optLong("addedAt")) }
            }
            val spoken = root.optJSONArray("speechRules") ?: JSONArray()
            val rules = List(spoken.length()) { i ->
                spoken.getJSONObject(i).let { PhraseRule(0, it.getString("spoken"), it.getString("written"), it.optBoolean("enabled", true)) }
            }
            val settings = HashMap<String, Any>()
            root.optJSONObject("settings")?.let { groups ->
                fun each(name: String, read: (JSONObject, String) -> Any) {
                    val group = groups.optJSONObject(name) ?: return
                    for (key in group.keys()) settings[key] = read(group, key)
                }
                each("boolean") { o, k -> o.getBoolean(k) }
                each("int") { o, k -> o.getInt(k) }
                each("long") { o, k -> o.getLong(k) }
                each("float") { o, k -> o.getDouble(k).toFloat() }
                each("string") { o, k -> o.getString(k) }
                each("stringSet") { o, k -> o.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) }.toSet() } }
            }
            return BackupContents(terms, rules, settings)
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is not a Lowkey backup", e)
        }
    }
}
