package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.dictionary.KeyboardLanguage
import com.rheerkens.androidkeyboard.dictionary.LanguagePackState
import com.rheerkens.androidkeyboard.settings.Settings
import com.rheerkens.androidkeyboard.ui.SlimIcons

/**
 * Keyboard languages: check the ones the globe key switches between, and download or delete the
 * word lists that are not built in.
 */
@Composable
fun LanguagesScreen(modifier: Modifier) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val packs by AppGraph.languagePacks.state.collectAsStateWithLifecycle()
    val all = AppGraph.languages.languages
    val onPhone = all.filter { it.bundled || it.id in packs.installed }
    val available = all.filter { !it.bundled && it.id !in packs.installed }
    val usableIds = onPhone.map { it.id }
    val current = AppGraph.currentLanguage(s)

    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Note(
                "The globe key switches between the checked languages; long-press it to switch keyboards. " +
                    "Each language has its own keys, accents on long-press, suggestions and autocorrect.",
                Modifier.padding(top = 12.dp),
            )
        }
        item { SectionTitle("On this phone") }
        items(onPhone, key = { "on-" + it.id }) { language ->
            val checked = language.id in s.enabledLanguages
            val onlyOne = checked && usableIds.count { it in s.enabledLanguages } == 1
            // The delete button sits outside the checkbox row, so each is its own accessibility target.
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clickable(enabled = !onlyOne, role = Role.Checkbox) {
                            AppGraph.settings.update { toggleLanguage(it, language.id, usableIds) }
                        }
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = !onlyOne)
                    Column(Modifier.weight(1f).padding(start = 8.dp, top = 10.dp, bottom = 10.dp)) {
                        Text(language.name, style = MaterialTheme.typography.bodyLarge)
                        val notes = listOfNotNull(
                            language.englishName.takeIf { it != language.name },
                            if (language.bundled) "Built in" else "Downloaded",
                            "Typing now".takeIf { language.id == current.id && checked },
                        )
                        Text(
                            notes.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!language.bundled) {
                    IconButton(onClick = { AppGraph.deleteLanguage(language.id) }) {
                        Icon(SlimIcons.Trash, "Delete ${language.englishName} word list")
                    }
                }
            }
        }
        if (available.isNotEmpty()) {
            item { SectionTitle("Download") }
            items(available, key = { "dl-" + it.id }) { language -> DownloadRow(language, packs) }
        }
        item {
            Note(
                "Word lists come from the Android Open Source Project keyboard (Apache 2.0). English (US), " +
                    "Dutch and Polish are built in. Others download from android.googlesource.com at a pinned " +
                    "version, are checked against their SHA-256, and are converted on the phone. Nothing you type " +
                    "is sent anywhere.",
                Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun DownloadRow(language: KeyboardLanguage, packs: LanguagePackState) {
    val progress = packs.downloads[language.id]
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(language.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(language.englishName.takeIf { it != language.name }, downloadSize(language))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (progress != null) {
                TextButton(
                    onClick = { AppGraph.languagePacks.cancel(language.id) },
                    modifier = Modifier.semantics { contentDescription = "Cancel download of ${language.englishName}" },
                ) { Text("Cancel", color = MaterialTheme.colorScheme.tertiary) }
            } else {
                Button(
                    onClick = { AppGraph.languagePacks.download(language.id) },
                    modifier = Modifier.semantics { contentDescription = "Download ${language.englishName}" },
                ) {
                    Icon(SlimIcons.Download, null, Modifier.padding(end = 6.dp))
                    Text("Download")
                }
            }
        }
        packs.errors[language.id]?.let { error ->
            Text(
                "Download failed: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

/** What a download transfers: the pinned file as base64 text, a third larger. */
internal fun downloadSize(language: KeyboardLanguage): String {
    val mb = language.sourceBytes * 4 / 3 / 100_000 / 10.0
    return "%.1f MB download".format(java.util.Locale.ROOT, mb)
}

/**
 * Checks or unchecks [id], keeping at least one of the [usable] languages (those on the phone)
 * enabled, and moving the current language off one that is unchecked.
 */
internal fun toggleLanguage(s: Settings, id: String, usable: List<String>): Settings {
    val enabled = if (id in s.enabledLanguages) s.enabledLanguages - id else s.enabledLanguages + id
    val stillUsable = usable.filter { it in enabled }
    if (stillUsable.isEmpty()) return s
    val current = if (s.currentLanguage in stillUsable) s.currentLanguage else stillUsable.first()
    return s.copy(enabledLanguages = enabled, currentLanguage = current)
}
