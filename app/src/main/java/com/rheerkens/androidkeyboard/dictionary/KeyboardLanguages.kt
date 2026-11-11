package com.rheerkens.androidkeyboard.dictionary

import java.text.Normalizer

/**
 * One keyboard language from `assets/dict/languages.txt`: its letter rows, preferred accents,
 * Whisper language code, and where its AOSP word list comes from. Pure data; see [LanguageCatalog].
 */
data class KeyboardLanguage(
    /** AOSP word-list locale, also the dictionary file name: `en_US`, `nl`, `pl`. */
    val id: String,
    /** The language's own name, shown on the space bar. */
    val name: String,
    val englishName: String,
    val shortLabel: String,
    /** Whisper language code, or null to let the model detect it. */
    val speech: String?,
    /** The three letter rows, lowercase, one string per row (any script). */
    val rows: List<String>,
    /** Long-press choices offered first for a letter, before the general accent list. */
    val accents: Map<String, List<String>>,
    /** `accent_margin` and `capitalized_nouns` from the catalog. */
    val rules: SpellingRules = SpellingRules(),
    /** Shipped in the APK (`assets/dict/<id>.dict`); otherwise downloaded on request. */
    val bundled: Boolean,
    /** Size and SHA-256 of the pinned AOSP `<id>_wordlist.combined.gz`. */
    val sourceBytes: Long,
    val sourceSha256: String,
    /** HTTPS address of that file's base64 text at the pinned commit. */
    val sourceUrl: String,
) {
    /** Letters on this layout's keys, row by row, as single-character strings. */
    val letterRows: List<List<String>> get() = rows.map { row -> row.map { it.toString() } }

    val keys: KeyGrid by lazy { KeyGrid(rows) }

    /** The asset path of a bundled word list, or the file name of a downloaded one. */
    val dictionaryFile: String get() = "$id.dict"

    override fun toString(): String = id
}

/** Parses and holds the language catalog. Lines are `key = value`; `[id]` starts a language. */
class LanguageCatalog(val languages: List<KeyboardLanguage>) {
    init {
        require(languages.isNotEmpty()) { "No keyboard languages" }
        require(languages.map { it.id }.toSet().size == languages.size) { "Duplicate language ids" }
    }

    private val byId = languages.associateBy { it.id }

    operator fun get(id: String?): KeyboardLanguage? = id?.let(byId::get)

    /** English (US), the language every install starts with. */
    val default: KeyboardLanguage get() = byId[DEFAULT_ID] ?: languages.first()

    /** Ids that are enabled and usable, in catalog order. */
    fun usable(enabled: Collection<String>, isInstalled: (KeyboardLanguage) -> Boolean): List<KeyboardLanguage> =
        languages.filter { it.id in enabled && (it.bundled || isInstalled(it)) }

    companion object {
        const val DEFAULT_ID = "en_US"
        const val ASSET = "dict/languages.txt"
        private val ID = Regex("[a-z]{2,3}(_[A-Z]{2})?")
        private val SHA256 = Regex("[0-9a-f]{64}")

        fun parse(text: String): LanguageCatalog {
            val global = HashMap<String, String>()
            val sections = ArrayList<Pair<String, HashMap<String, String>>>()
            text.lineSequence().forEachIndexed { n, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                if (line.startsWith("[") && line.endsWith("]")) {
                    sections += line.substring(1, line.length - 1).trim() to HashMap()
                    return@forEachIndexed
                }
                val eq = line.indexOf('=')
                require(eq > 0) { "languages.txt line ${n + 1}: expected key = value" }
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim()
                (sections.lastOrNull()?.second ?: global)[key] = value
            }
            val source = global["source"]
            require(source != null && source.startsWith("https://")) { "languages.txt: no HTTPS source" }
            return LanguageCatalog(sections.map { (id, s) -> language(id, s, source) })
        }

        private fun language(id: String, s: Map<String, String>, source: String): KeyboardLanguage {
            fun req(key: String) = s[key]?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("[$id]: missing $key")
            require(ID.matches(id)) { "[$id]: id must look like pl or pt_BR" }
            val rows = req("rows").split(' ').filter { it.isNotEmpty() }.map { it.lowercase() }
            require(rows.size == 3 && rows.all { r -> r.all(Char::isLetter) }) { "[$id]: rows must be three rows of letters" }
            require(rows.joinToString("").let { it.toSet().size == it.length }) { "[$id]: a letter appears twice in rows" }
            val accents = s["accents"].orEmpty().split(' ').filter { it.isNotEmpty() }.associate { pair ->
                val colon = pair.indexOf(':')
                require(colon == 1 && pair.length > 2) { "[$id]: accents entries look like a:ą" }
                pair.substring(0, 1) to pair.substring(2).map { it.toString() }
            }
            val sha = req("sha256")
            require(SHA256.matches(sha)) { "[$id]: sha256 must be 64 lowercase hex digits" }
            val bytes = req("size").toLongOrNull()
            require(bytes != null && bytes > 0) { "[$id]: size must be a positive number" }
            val bundled = when (req("bundled")) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("[$id]: bundled must be true or false")
            }
            return KeyboardLanguage(
                id = id, name = req("name"), englishName = req("english"), shortLabel = req("short"),
                speech = s["speech"]?.takeIf { it.isNotEmpty() }, rows = rows, accents = accents,
                rules = SpellingRules(
                    accentMargin = s["accent_margin"]?.let { v ->
                        v.toIntOrNull()?.takeIf { it in 1..255 } ?: throw IllegalArgumentException("[$id]: accent_margin must be 1-255")
                    } ?: 0,
                    capitalizedNouns = when (s["capitalized_nouns"]) {
                        null, "false" -> false
                        "true" -> true
                        else -> throw IllegalArgumentException("[$id]: capitalized_nouns must be true or false")
                    },
                ),
                bundled = bundled, sourceBytes = bytes, sourceSha256 = sha,
                sourceUrl = "${source.trimEnd('/')}/${id}_wordlist.combined.gz?format=TEXT",
            )
        }
    }
}

/**
 * Key positions of a letter layout, for the spelling corrector: which keys neighbour each other,
 * and which letters are accented forms of a key (`ż` of `z` on a Polish layout). A letter that
 * has its own key (`ä` on a Swedish layout, `й` on a Russian one) is never folded into another.
 */
class KeyGrid(rows: List<String>) {
    private val keyCount = rows.sumOf { it.length }
    /** Key index per character (its own key, else its base letter's key), or -1. */
    private val keyIndex = IntArray(TABLE_SIZE) { -1 }
    private val folded = CharArray(TABLE_SIZE) { it.toChar() }
    /** Every letter's base letter, even if it has its own key (`ü` → `u` on German). */
    private val bases = CharArray(TABLE_SIZE) { baseLetter(it.toChar()) }
    private val neighbours = BooleanArray(keyCount * keyCount)

    init {
        val width = rows.maxOf { it.length }
        val positions = ArrayList<Pair<Double, Double>>()
        val onKeys = HashSet<Char>()
        rows.forEachIndexed { r, row ->
            // Shorter rows are centred, as the keyboard draws them.
            val offset = (width - row.length) / 2.0
            row.forEachIndexed { c, ch ->
                if (ch.code < TABLE_SIZE) keyIndex[ch.code] = positions.size
                onKeys += ch
                positions += (c + offset) to r.toDouble()
            }
        }
        for (code in 0 until TABLE_SIZE) {
            val ch = code.toChar()
            if (ch in onKeys) continue
            val base = baseLetter(ch)
            if (base != ch) {
                folded[code] = base
                if (base.code < TABLE_SIZE) keyIndex[code] = keyIndex[base.code]
            }
        }
        for (a in 0 until keyCount) for (b in 0 until keyCount) {
            val (ax, ay) = positions[a]
            val (bx, by) = positions[b]
            neighbours[a * keyCount + b] = a != b && kotlin.math.abs(ax - bx) <= 1.0 && kotlin.math.abs(ay - by) <= 1.0
        }
    }

    /** [c] without its accent if that accent has no key of its own on this layout. */
    fun fold(c: Char): Char = if (c.code < TABLE_SIZE) folded[c.code] else c

    fun fold(s: String): String {
        var i = 0
        while (i < s.length && fold(s[i]) == s[i]) i++
        if (i == s.length) return s // no allocation for the common case
        return buildString(s.length) { s.forEach { append(fold(it)) } }
    }

    /**
     * True if [a] and [b] are the same letter apart from accents, even when the accented one has
     * its own key (`u` and `ü` on a German layout): a slip as likely as a neighbouring key.
     */
    fun sameBaseLetter(a: Char, b: Char): Boolean =
        a.code < TABLE_SIZE && b.code < TABLE_SIZE && bases[a.code] == bases[b.code]

    /** True if [a] and [b] (lowercase) sit on neighbouring keys. */
    fun adjacent(a: Char, b: Char): Boolean {
        val x = if (a.code < TABLE_SIZE) keyIndex[a.code] else -1
        val y = if (b.code < TABLE_SIZE) keyIndex[b.code] else -1
        return x >= 0 && y >= 0 && neighbours[x * keyCount + y]
    }

    companion object {
        /** Latin, Greek, Cyrillic, Armenian, Hebrew and Arabic letters; others get no neighbours. */
        private const val TABLE_SIZE = 0x800

        /** Letters whose accent is not a combining mark. Declared before [QWERTY], which uses it. */
        private val SPECIAL = mapOf('ł' to 'l', 'đ' to 'd', 'ø' to 'o', 'ı' to 'i', 'ħ' to 'h', 'ŀ' to 'l')

        val QWERTY = KeyGrid(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"))

        /** The letter without accents (`ż` → `z`, `ł` → `l`, `ё` → `е`), or [c] itself. */
        fun baseLetter(c: Char): Char {
            SPECIAL[c]?.let { return it }
            if (!c.isLetter()) return c
            val nfd = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            if (nfd.length < 2 || !nfd[0].isLetter()) return c
            // Only strip combining marks (ogonek, acute, caron, breve, comma below, ...).
            for (i in 1 until nfd.length) if (Character.getType(nfd[i]) != Character.NON_SPACING_MARK.toInt()) return c
            return nfd[0]
        }
    }
}
