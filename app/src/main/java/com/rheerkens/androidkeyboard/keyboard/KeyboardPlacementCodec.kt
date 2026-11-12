package com.rheerkens.androidkeyboard.keyboard

/** A versioned, bounded preference format; a damaged profile never prevents IME startup. */
internal object PlacementCodec {
    fun encode(value: PlacementPreferences): String = buildString {
        append("2|").append(value.automaticSplit).append('|').append(value.legacyRowHeight).append('\n')
        value.profiles.forEach { (profile, p) ->
            append(listOf(profile.name, p.mode.name, p.automatic, p.rowHeight, p.bottomSpace,
                p.oneHandWidth, p.rightHand, p.splitGap, p.floatingWidth, p.floatingRowHeight,
                p.floatingX, p.floatingY, p.dockedWidth, p.dockedX).joinToString("|")).append('\n')
        }
    }

    fun decode(text: String?, legacy: Float): PlacementPreferences {
        val fallback = PlacementPreferences(legacyRowHeight = legacy)
        if (text == null || text.length > 8192) return fallback
        val lines = text.lineSequence().toList()
        val header = lines.firstOrNull()?.split('|') ?: return fallback
        if (header.size !in 2..3 || header[0] !in setOf("1", "2") || header[1] !in setOf("true", "false")) return fallback
        val baseHeight = header.getOrNull(2)?.toFloatOrNull()?.safe(legacy)?.coerceIn(32f, 88f) ?: legacy
        val profiles = mutableMapOf<PlacementProfile, KeyboardPlacement>()
        for (line in lines.drop(1)) {
            val parts = line.split('|')
            if (parts.size != if (header[0] == "2") 14 else 12) continue
            val profile = PlacementProfile.entries.find { it.name == parts[0] } ?: continue
            val mode = PlacementMode.entries.find { it.name == parts[1] } ?: continue
            fun number(i: Int, default: Float) = parts[i].toFloatOrNull()?.safe(default) ?: default
            profiles[profile] = KeyboardPlacement(mode, parts[2] == "true", number(3, baseHeight),
                number(4, 0f), number(5, .8f), parts[6] != "false", number(7, -1f),
                number(8, 320f), number(9, baseHeight), number(10, .5f), number(11, .7f),
                if (parts.size == 14) number(12, 1f) else 1f,
                if (parts.size == 14) number(13, .5f) else .5f)
        }
        return PlacementPreferences(header[1] == "true", profiles, baseHeight)
    }
}
