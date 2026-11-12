package com.rheerkens.androidkeyboard.keyboard

data class EmojiVariant(val value: String, val name: String)
data class EmojiItem(val value: String, val name: String, val category: String, val variants: List<EmojiVariant>)

/** Unicode data only; glyph rendering belongs to the device, not a downloaded image service. */
class EmojiCatalog(val entries: List<EmojiItem>) {
    private val byValue = entries.flatMap { item ->
        item.variants.map { variant -> variant.value to item.copy(value = variant.value, name = variant.name) }
    }.toMap()
    private val longestFirst = byValue.keys.sortedByDescending { it.length }
    val categories: List<String> = entries.map { it.category }.distinct()
    fun find(value: String): EmojiItem? = byValue[value]
    fun suffixOf(text: String): String? = longestFirst.firstOrNull { text.endsWith(it) }

    fun supported(hasGlyph: (String) -> Boolean): EmojiCatalog = EmojiCatalog(entries.mapNotNull { item ->
        val variants = item.variants.filter { hasGlyph(it.value) }
        variants.firstOrNull()?.let { first -> item.copy(value = first.value, name = first.name, variants = variants) }
    })

    companion object {
        private val version = Regex("\\s+E[0-9.]+\\s+")
        private val tones = Regex("(?:, )?(?:light|medium-light|medium|medium-dark|dark) skin tone")
        private data class Row(val value: String, val name: String, val category: String) {
            val toned get() = value.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF }
            val baseKey get() = value.codePoints().filter { it !in 0x1F3FB..0x1F3FF && it != 0xFE0F }
                .toArray().joinToString(",")
        }

        fun parse(text: String): EmojiCatalog {
            var category = ""
            val rows = mutableListOf<Row>()
            for (line in text.lineSequence()) {
                if (line.startsWith("# group: ")) category = line.substringAfter("# group: ")
                if (!line.contains(';') || line.substringAfter(';').substringBefore('#').trim() != "fully-qualified") continue
                val comment = line.substringAfter('#').trim()
                val marker = version.find(comment) ?: continue
                val value = buildString {
                    line.substringBefore(';').trim().split(Regex("\\s+")).forEach { appendCodePoint(it.toInt(16)) }
                }
                rows += Row(value, comment.substring(marker.range.last + 1), category)
            }
            val bases = rows.filterNot { it.toned }
            val byKey = bases.associateBy { it.baseKey }
            val byName = bases.associateBy { it.name }
            val groups = linkedMapOf<Row, MutableList<EmojiVariant>>()
            bases.forEach { groups[it] = mutableListOf(EmojiVariant(it.value, it.name)) }
            for (row in rows.filter { it.toned }) {
                val base = byKey[row.baseKey] ?: byName[row.name.replace(tones, "").trimEnd(':', ',', ' ')] ?: row
                groups.getOrPut(base) { mutableListOf() }.add(EmojiVariant(row.value, row.name))
            }
            return EmojiCatalog(groups.map { (base, variants) -> EmojiItem(base.value, base.name, base.category, variants) })
        }
    }
}

/** Recent emoji are explicit selections, never a history of editor contents. */
object EmojiHistory {
    const val LIMIT = 40
    private fun valid(value: String) = value.isNotBlank() && value.length <= 64 && value.none { it.isISOControl() }
    fun record(recent: List<String>, value: String, remember: Boolean = true): List<String> =
        if (remember && valid(value)) (listOf(value) + recent).filter(::valid).distinct().take(LIMIT) else recent
    fun decode(value: String?): List<String> = value?.takeIf { it.length <= 8192 }?.lineSequence()
        ?.filter(::valid)?.distinct()?.take(LIMIT)?.toList().orEmpty()
    fun encode(recent: List<String>): String = recent.filter(::valid).distinct().take(LIMIT).joinToString("\n")
}
