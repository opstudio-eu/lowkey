package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    val version = remember { appVersion(context) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    var openLicense by rememberSaveable { mutableStateOf<String?>(null) }
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    var creditsOpen by rememberSaveable { mutableStateOf(false) }
    var trademarksOpen by rememberSaveable { mutableStateOf(false) }
    openLicense?.let { file ->
        LicenseDialog(
            title = licenseTitle(file),
            file = file,
            onDismiss = { openLicense = null },
        )
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Lowkey Keyboard", style = MaterialTheme.typography.titleMedium)
            Text("Version $version · made by OpStudio", style = MaterialTheme.typography.bodyMedium, color = muted)
            Spacer(Modifier.height(6.dp))
            Text(
                "A slim keyboard with on-device voice typing. Speech recognition, suggestions and autocorrect run on your phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SettingsGroup("Privacy") {
            GroupText(
                "Typed text, audio, dictation and your dictionary stay on your phone. Lowkey goes online only to " +
                    "download the speech models and keyboard languages you choose, to check for a newer version about " +
                    "once a day (unless you turn it off in Settings → Updates), and to download an update when you tap " +
                    "Update. Audio is recorded only while you dictate and is not saved.",
            )
        }

        SettingsGroup("Open source") {
            LinkRow("Apache License 2.0", "Lowkey's license. It comes with no warranty.") { openLicense = APACHE_ASSET }
            LinkRow("Source code", SOURCE_URL.removePrefix("https://")) { uri.openUri(SOURCE_URL) }
            DisclosureRow("Third-party licenses", licensesOpen, { licensesOpen = !licensesOpen },
                summary = "Full license texts of bundled components")
            if (licensesOpen) {
                val licenseFiles = remember { licenseAssets(context) }
                licenseFiles.forEach { file ->
                    TextButton(
                        onClick = { openLicense = file },
                        modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
                    ) { Text(licenseTitle(file)) }
                }
            }
        }

        SettingsGroup("Credits") {
            DisclosureRow("Acknowledgements", creditsOpen, { creditsOpen = !creditsOpen },
                summary = "Speech models, word lists, libraries and design references")
            if (creditsOpen) {
                CreditHeading("Speech models")
                GroupText(
                    "Speech recognition uses OpenAI Whisper and Distil-Whisper models (MIT), as sherpa-onnx " +
                        "exports by k2-fsa. They are downloaded on request and stored in app-private storage, " +
                        "never inside the APK.",
                )
                CreditHeading("Word lists")
                GroupText(
                    "Suggestions and autocorrect use word lists derived from the Android Open Source Project " +
                        "LatinIME dictionaries. Copyright 2008 The Android Open Source Project, Apache License 2.0. " +
                        "English (US) is built in; other languages download on request. Converted to word and " +
                        "frequency lists, with offensive entries removed.",
                )
                CreditHeading("Open-source libraries")
                GroupText(
                    "AndroidX, Jetpack Compose, Material 3 and Kotlin coroutines (Apache License 2.0). Speech " +
                        "recognition runs on sherpa-onnx (Apache License 2.0), built for speech recognition only, and " +
                        "ONNX Runtime (MIT); the license overview lists their components.",
                )
                CreditHeading("Design references")
                GroupText(
                    "Lowkey's code is its own. These projects inspired its design; no source code or assets " +
                        "are taken from them. Model file names, URLs, sizes and checksums are facts about the " +
                        "published model files and were cross-checked against them.",
                )
                references.forEach { ref ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(ref.name, style = MaterialTheme.typography.titleSmall)
                        Text(ref.role, style = MaterialTheme.typography.bodyMedium)
                        Text("License: ${ref.license}", style = MaterialTheme.typography.bodySmall, color = muted)
                        TextButton(
                            onClick = { uri.openUri(ref.url) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(ref.url, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            DisclosureRow("Trademarks", trademarksOpen, { trademarksOpen = !trademarksOpen })
            if (trademarksOpen) {
                GroupText(
                    "The OpStudio name and logo are trademarks of OpStudio. The Apache License does not grant " +
                        "permission to use them, except to describe where Lowkey comes from. Forks and modified " +
                        "versions should remove the logo.",
                )
            }
        }
        Text(
            "Package ${context.packageName}",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CreditHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).semantics { heading() },
    )
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
