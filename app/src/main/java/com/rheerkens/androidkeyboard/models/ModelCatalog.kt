package com.rheerkens.androidkeyboard.models

/** One file of a model package, pinned by size and SHA-256. */
data class ModelArtifact(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    init {
        val problem = when {
            !isPlainFileName(name) -> "not a plain file name"
            !url.startsWith("https://") -> "not an HTTPS URL"
            sizeBytes <= 0 -> "no size"
            !SHA256_HEX.matches(sha256) -> "not a lowercase SHA-256"
            else -> null
        }
        require(problem == null) { "Invalid model artifact $name: $problem" }
    }

    private companion object {
        val SHA256_HEX = Regex("[0-9a-f]{64}")

        /** A single path segment, so a download can never write outside its model directory. */
        fun isPlainFileName(name: String) =
            name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' }
    }
}

/** Which runtime and configuration loads a model package. */
sealed interface EngineSpec {
    /** sherpa-onnx Whisper: `<prefix>-encoder.int8.onnx`, `<prefix>-decoder.int8.onnx`, `<prefix>-tokens.txt`. */
    data class SherpaWhisper(val prefix: String, val englishOnly: Boolean = prefix.endsWith(".en")) : EngineSpec
}

/** A selectable recognition language. [AUTO] lets the model detect it. */
data class SpeechLanguage(val code: String, val label: String) {
    companion object {
        val AUTO = SpeechLanguage("auto", "Auto-detect")
    }
}

/** Static description of a downloadable speech model. */
data class ModelInfo(
    val id: String,
    val name: String,
    val family: String,
    val language: String,
    val description: String,
    val engine: EngineSpec,
    val license: String,
    val source: String,
    /** Files that make up the installed model. */
    val artifacts: List<ModelArtifact>,
    /** Languages the user can choose; empty means the model needs no choice. */
    val languages: List<SpeechLanguage> = emptyList(),
    val recommended: Boolean = false,
) {
    init {
        require(artifacts.isNotEmpty() && artifacts.map { it.name }.toSet().size == artifacts.size)
    }

    /** Bytes the installed model occupies, and transferred by a download. */
    val installedBytes: Long get() = artifacts.sumOf { it.sizeBytes }

    val sizeMb: Int get() = (installedBytes / 1_000_000).toInt()
    val sizeLabel: String get() = "$sizeMb MB"
    val sizeShort: String get() = if (sizeMb >= 1000) "%.1f GB".format(sizeMb / 1000.0) else "$sizeMb MB"
}

/**
 * Downloadable models: OpenAI Whisper and Distil-Whisper (MIT), as sherpa-onnx int8 exports on Hugging
 * Face. Every URL is pinned to a commit, and every file to its size and SHA-256 (checked against the
 * hosts on 2026-10-10). They run on the sherpa-onnx library that scripts/build-sherpa-onnx.sh builds.
 */
object ModelCatalog {
    private fun hf(repo: String, commit: String, name: String, size: Long, sha256: String) =
        ModelArtifact(name, "https://huggingface.co/$repo/resolve/$commit/$name?download=true", size, sha256)

    private fun whisper(
        size: String, label: String, commit: String, decoder: Pair<Long, String>, encoder: Pair<Long, String>,
        english: Boolean = size.endsWith(".en"), source: String = "OpenAI Whisper $size", note: String? = null,
        recommended: Boolean = false,
    ): ModelInfo {
        val repo = "csukuangfj/sherpa-onnx-whisper-$size"
        // `.en` exports ship the English-only tokenizer; the others (including Distil) the multilingual one.
        val tokens = if (size.endsWith(".en")) {
            835_554L to "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"
        } else {
            816_730L to "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"
        }
        return ModelInfo(
            id = "whisper-$size", name = "Whisper $label", family = "OpenAI Whisper",
            language = if (english) "English" else "Multilingual",
            engine = EngineSpec.SherpaWhisper(prefix = size, englishOnly = english), license = "MIT",
            source = "$source, sherpa-onnx int8 export by k2-fsa",
            description = listOfNotNull(
                note,
                if (english) "Text after Stop. English only." else "Text after Stop. Detects the language or uses the one you choose.",
            ).joinToString(" "),
            artifacts = listOf(
                hf(repo, commit, "$size-encoder.int8.onnx", encoder.first, encoder.second),
                hf(repo, commit, "$size-decoder.int8.onnx", decoder.first, decoder.second),
                hf(repo, commit, "$size-tokens.txt", tokens.first, tokens.second),
            ),
            languages = if (english) emptyList() else listOf(SpeechLanguage.AUTO) + WHISPER_LANGUAGES,
            recommended = recommended,
        )
    }

    private fun langs(vararg pairs: Pair<String, String>) = pairs.map { SpeechLanguage(it.first, it.second) }

    private val WHISPER_LANGUAGES = langs(
        "en" to "English", "nl" to "Dutch", "de" to "German", "fr" to "French", "es" to "Spanish", "it" to "Italian",
        "pt" to "Portuguese", "pl" to "Polish", "sv" to "Swedish", "da" to "Danish", "no" to "Norwegian",
        "fi" to "Finnish", "cs" to "Czech", "ro" to "Romanian", "hu" to "Hungarian", "tr" to "Turkish",
        "ru" to "Russian", "uk" to "Ukrainian", "ar" to "Arabic", "hi" to "Hindi", "ja" to "Japanese",
        "ko" to "Korean", "zh" to "Chinese", "vi" to "Vietnamese",
    )

    val models: List<ModelInfo> = listOf(
        whisper(
            "tiny.en", "tiny.en", "d026532c022fa99fd789d6b32446a1df7b6bfc43",
            89_853_865L to "06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527",
            12_937_772L to "0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6",
        ),
        whisper(
            "base.en", "base.en", "59eea950fc76df2453efb57e6c0fd334548e8ffe",
            130_669_978L to "f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41",
            29_120_534L to "ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823",
        ),
        whisper(
            "small.en", "small.en", "d9533f69affd85061aee349af7fea5cb2996dbbe",
            262_223_042L to "710ccf890e10f3faa15f51ec346081a2723c9f3adb6e4da81c6573a5a6f877fb",
            112_442_483L to "8bdac288f369aa94ee2194059238c465ed82ea9d47ee8fa4a8c0a891873e462f",
        ),
        whisper(
            "tiny", "tiny", "65176e2deb88badc814a94058666cadccc29b61c",
            89_855_401L to "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925",
            12_937_772L to "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
        ),
        whisper(
            "base", "base", "bb53ee204431c90d314c1cc08d28d23e5b7927cc",
            130_672_026L to "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d",
            29_120_534L to "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11",
            recommended = true,
        ),
        whisper(
            "small", "small", "8f3c18b358db4d1f2fc1eae49d75cd20989e4309",
            262_226_114L to "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee",
            112_442_483L to "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9",
        ),
        whisper(
            "distil-large-v3.5", "Distil-Large v3.5", "2933155a51e982b9ebd9ca3b7fb80fc78162fa64",
            314_871_757L to "7cef98c7b5491ba52a520fd61da957a62e6e64c702c6c8aeac8cdfb146cbdb6a",
            668_147_593L to "c30727100ba2dfbe64f2309a2ec44a5049b005d8a6c0acce49952f635083cbdb",
            english = true, source = "Distil-Whisper large-v3.5 by Hugging Face",
            note = "1 GB. Large-model accuracy, faster than Turbo.",
        ),
        whisper(
            "turbo", "Turbo", "2ca6ff69fc878651b770880507669577ac41c2ff",
            361_080_764L to "20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547",
            674_716_297L to "b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b",
            source = "OpenAI Whisper large-v3-turbo",
            note = "1 GB. Close to Whisper large-v3 accuracy.",
        ),
    )

    fun byId(id: String?): ModelInfo? = models.firstOrNull { it.id == id }
}

/**
 * The language to load [this] model with: the user's [preferred] code if the model offers it,
 * else English, else its first option. Null for models without a language choice.
 */
fun ModelInfo.resolveLanguage(preferred: String): SpeechLanguage? {
    if (languages.isEmpty()) return null
    return languages.firstOrNull { it.code == preferred }
        ?: languages.firstOrNull { it.code == "en" }
        ?: languages.first()
}
