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
    /** The three letter rows of the language's own layout, lowercase, one string per row (any script). */
    val rows: List<String>,
    /** That layout's name: `QWERTY`, `QWERTZ`, `AZERTY`, `ЙЦУКЕН`. */
    val layoutName: String,
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
    /** Position in the catalog, for the globe key's order. */
    val index: Int = 0,
) {
    /** Letters on this layout's keys, row by row, as single-character strings. */
    val letterRows: List<List<String>> get() = rows.map { row -> row.map { it.toString() } }

    /** Written in the Latin alphabet, so the alternative arrangements (AZERTY, Dvorak, ...) fit it. */
    val latin: Boolean get() = rows.joinToString("").filter(Char::isLetter).all {
        Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN
    }

    /** Which accented letters count as a missing accent: those without a key on this language's own layout. */
    val folding: AccentFolding by lazy { AccentFolding(rows.joinToString("").toSet()) }

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
            return LanguageCatalog(sections.mapIndexed { i, (id, s) -> language(id, s, source).copy(index = i) })
        }

        /** Keys a language section may have; any other is a typo that would silently do nothing. */
        private val KEYS = setOf(
            "name", "english", "short", "speech", "rows", "layout", "accents", "bundled", "size", "sha256",
            "accent_margin", "capitalized_nouns",
        )

        private fun language(id: String, s: Map<String, String>, source: String): KeyboardLanguage {
            (s.keys - KEYS).firstOrNull()?.let { throw IllegalArgumentException("[$id]: unknown key $it") }
            fun req(key: String) = s[key]?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("[$id]: missing $key")
            require(ID.matches(id)) { "[$id]: id must look like pl or pt_BR" }
            val rows = req("rows").split(' ').filter { it.isNotEmpty() }.map { it.lowercase() }
            require(rows.size == 3 && rows.all { r -> r.all { it.isLetter() || it == '\'' } }) {
                "[$id]: rows must be three rows of letters (and apostrophes)"
            }
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
                speech = s["speech"]?.takeIf { it.isNotEmpty() }, rows = rows, layoutName = req("layout"), accents = accents,
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
 * Accents a typist may leave out: an accented letter folds to its base letter (`ż` → `z`,
 * `ł` → `l`, `ё` → `е`) unless it has a key of its own on the language's layout ([keyLetters]),
 * as `ä` has on a Swedish keyboard or `й` on a Russian one.
 */
class AccentFolding(keyLetters: Set<Char>) {
    private val folded = CharArray(TABLE_SIZE) { code ->
        val ch = code.toChar()
        if (ch in keyLetters) ch else baseLetter(ch)
    }

    fun fold(c: Char): Char = if (c.code < TABLE_SIZE) folded[c.code] else c

    fun fold(s: String): String {
        var i = 0
        while (i < s.length && fold(s[i]) == s[i]) i++
        if (i == s.length) return s // no allocation for the common case
        return buildString(s.length) { s.forEach { append(fold(it)) } }
    }

    companion object {
        /** Latin, Greek, Cyrillic, Armenian, Hebrew and Arabic letters; others are left as they are. */
        internal const val TABLE_SIZE = 0x800

        /** Letters whose accent is not a combining mark. Declared before [QWERTY], which uses it. */
        private val SPECIAL = mapOf('ł' to 'l', 'đ' to 'd', 'ø' to 'o', 'ı' to 'i', 'ħ' to 'h', 'ŀ' to 'l')

        /** For QWERTY languages (English): every accent folds. */
        val QWERTY = AccentFolding("qwertyuiopasdfghjklzxcvbnm".toSet())

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
