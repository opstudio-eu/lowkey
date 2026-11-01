package com.rheerkens.androidkeyboard.companion.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SOURCE_URL = "https://github.com/opstudio-eu/lowkey"
private const val APACHE_ASSET = "licenses/Apache-2.0.txt"
private const val OVERVIEW_ASSET = "licenses/README.txt"

/** Every bundled license text: the overview first, then assets/licenses (with subfolders) and dict/NOTICE. */
private fun licenseAssets(context: android.content.Context): List<String> {
    fun walk(dir: String): List<String> = context.assets.list(dir).orEmpty().sorted().flatMap { name ->
        val path = "$dir/$name"
        if (context.assets.list(path).isNullOrEmpty()) listOf(path) else walk(path)
    }
    val all = walk("licenses") + "dict/NOTICE"
    return listOf(OVERVIEW_ASSET) + (all - OVERVIEW_ASSET)
}

private fun licenseTitle(path: String): String = when (path) {
    OVERVIEW_ASSET -> "Overview of components and licenses"
    APACHE_ASSET -> "Apache License 2.0"
    "dict/NOTICE" -> "AOSP word lists notice"
    else -> path.removePrefix("licenses/").removeSuffix(".txt").replace("/", " / ")
}

private data class Reference(val name: String, val url: String, val role: String, val license: String)

private val references = listOf(
    Reference(
        name = "FUTO Keyboard",
        url = "https://github.com/futo-org/android-keyboard",
        role = "Design reference for the input-method lifecycle, key layout, sizing settings, " +
            "personal dictionary, and voice-input integration.",
        license = "FUTO Source First License 1.1 (not open source). No code from it is used in this app.",
    ),
    Reference(
        name = "Moonshine voice-input fork (Today20092/voice-input)",
        url = "https://github.com/Today20092/voice-input",
        role = "Design reference for the recording lifecycle, compact voice UI, streaming speech " +
            "backends, model catalog, and post-recognition text corrections.",
        license = "FUTO Source First License 1.0 (not open source). No code from it is used in this app.",
    ),
)

@Composable
fun AboutScreen(modifier: Modifier) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } ?: "unknown"
    }

    var openLicense by rememberSaveable { mutableStateOf<String?>(null) }
    openLicense?.let { file ->
        LicenseDialog(
            title = licenseTitle(file),
            file = file,
            onDismiss = { openLicense = null },
        )
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SlimCard(Modifier.padding(16.dp)) {
            Text("Lowkey Keyboard", style = MaterialTheme.typography.titleLarge)
            Text("Version $version · com.rheerkens.androidkeyboard", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "A slim keyboard with local dictation. Speech runs on the device with Whisper. " +
                    "Suggestions use a small built-in word list for now.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionTitle("License")
        Body(
            "Lowkey is open source under the Apache License 2.0. It comes with no warranty. " +
                "The libraries it includes are listed under License texts below."
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { openLicense = APACHE_ASSET }) { Text("Apache License 2.0") }
            TextButton(onClick = { uri.openUri(SOURCE_URL) }) { Text("Source code") }
        }

        SectionTitle("Privacy")
        Body(
            "Everything stays on the device. The app makes no network requests except model downloads " +
                "you start. Typed text, audio, dictation, and your dictionary never leave the phone. " +
                "Audio is recorded only while voice mode is listening and is not saved."
        )

        SectionTitle("References")
        Body(
            "Lowkey's code is its own. These projects inspired its design; no source code or assets " +
                "are taken from them. Model file names, URLs, sizes and checksums are facts about the " +
                "published model files and were cross-checked against them."
        )
        references.forEach { ref ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(ref.name, style = MaterialTheme.typography.titleSmall)
                Text(ref.role, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "License: ${ref.license}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { uri.openUri(ref.url) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(ref.url, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionTitle("Models")
        Body(
            "Speech recognition uses OpenAI Whisper and Distil-Whisper models (MIT), as sherpa-onnx " +
                "exports by k2-fsa. They are downloaded on request and stored in app-private storage, " +
                "never inside the APK."
        )

        SectionTitle("Word lists")
        Body(
            "Suggestions and autocorrect use word lists derived from the Android Open Source Project " +
                "LatinIME dictionaries (English and Dutch). Copyright 2008 The Android Open Source Project, " +
                "Apache License 2.0. Converted to word and frequency lists, with offensive entries removed."
        )

        SectionTitle("Open-source libraries")
        Body(
            "AndroidX, Jetpack Compose, Material 3 and Kotlin coroutines (Apache License 2.0). Speech " +
                "recognition runs on sherpa-onnx (Apache License 2.0), built for speech recognition only, and " +
                "ONNX Runtime (MIT); the overview below lists their components."
        )

        SectionTitle("License texts")
        val licenseFiles = remember { licenseAssets(context) }
        licenseFiles.forEach { file ->
            TextButton(onClick = { openLicense = file }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(licenseTitle(file))
            }
        }
    }
}

@Composable
private fun LicenseDialog(title: String, file: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Paragraphs, so long notices are laid out lazily.
    val paragraphs by produceState<List<String>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(file).bufferedReader().use { it.readText() }
                    .split(Regex("\\n\\s*\\n")).map { it.trimEnd() }.filter { it.isNotBlank() }
            }.getOrElse { listOf("This license text is missing from the app.") }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(paragraphs ?: listOf("Loading…")) { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}
