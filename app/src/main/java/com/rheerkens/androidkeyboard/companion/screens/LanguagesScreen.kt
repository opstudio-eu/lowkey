package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.dictionary.KeyboardLanguage
import com.rheerkens.androidkeyboard.dictionary.LanguagePackState
import com.rheerkens.androidkeyboard.keyboard.TypingKeyboard
import com.rheerkens.androidkeyboard.settings.KeyArrangement
import com.rheerkens.androidkeyboard.ui.SlimIcons

/**
 * Your languages first: use, turn on or off, or remove them here, and pick the letter layouts of
 * each language that is on. The download catalog stays behind Add a language; downloads in
 * progress or failed stay visible while it is closed.
 */
@Composable
fun LanguagesScreen(modifier: Modifier) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val packs by AppGraph.languagePacks.state.collectAsStateWithLifecycle()
    val all = AppGraph.languages.languages
    val onPhone = all.filter { it.bundled || it.id in packs.installed }
    val available = all.filter { !it.bundled && it.id !in packs.installed }
    val current = AppGraph.currentKeyboard(s)
    val installed = remember(packs.installed) { AppGraph.installedKeyboards() }
    val usableIds = installed.map { it.id }
    var removing by remember { mutableStateOf<KeyboardLanguage?>(null) }
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    // Downloads in progress or failed show even with the catalog closed.
    val active = available.filter { it.id in packs.downloads || it.id in packs.errors }

    removing?.let { language ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${language.name}?") },
            text = { Text("This removes its word list and turns off its layouts. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = { AppGraph.deleteLanguage(language.id); removing = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    val list = rememberLazyListState()
    RevealSettingHighlight(list)
    LazyColumn(modifier.fillMaxWidth(), state = list) {
        item(key = "summary") { PageSummary("Typing in ${current.fullLabel}.") }
        item(key = "yours") {
            val anyLast = onPhone.any { language -> isLastLanguage(language, installed, s.enabledKeyboards) }
            val layoutsOn = usableIds.count { it in s.enabledKeyboards }
            val hints = listOfNotNull(
                "Tap a language to use it.".takeIf { onPhone.size > 1 },
                "Keep at least one language on.".takeIf { anyLast },
                "Layouts change only the letter keys; suggestions and autocorrect follow the language.",
                switchHint(s.bottomKey).takeIf { layoutsOn > 1 },
            )
            SettingsGroup("Your languages", footer = hints.joinToString(" ")) {
                onPhone.forEachIndexed { index, language ->
                    val options = installed.filter { it.language.id == language.id }
                    val enabled = options.any { it.id in s.enabledKeyboards }
                    val inUse = current.language.id == language.id
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    InstalledLanguageRow(
                        language = language,
                        enabled = enabled,
                        inUse = inUse,
                        last = isLastLanguage(language, installed, s.enabledKeyboards),
                        layoutLabel = current.layoutLabel,
                        onUse = {
                            // As before: the current layout, else the first one that's on, else its own.
                            val preferred = current.takeIf { it.language.id == language.id }
                                ?: options.firstOrNull { it.id in s.enabledKeyboards } ?: options.firstOrNull()
                            if (preferred != null) AppGraph.settings.update { it.selectKeyboard(preferred.id, usableIds) }
                        },
                        onEnabled = { on -> AppGraph.settings.update { it.setLanguageEnabled(language.id, on, usableIds) } },
                        onRemove = { removing = language },
                    )
                    // A language written in the Latin alphabet also offers the other letter layouts.
                    if (enabled && options.size > 1) {
                        LayoutChips(
                            language, options, s.enabledKeyboards, onlyOneOn = layoutsOn == 1,
                            // A search for layouts lands on those of the language you type in.
                            modifier = if (inUse) settingHighlight("Layouts") else Modifier,
                        ) { keyboard -> AppGraph.settings.update { it.tapLayout(keyboard.id, usableIds) } }
                    }
                }
            }
        }
        if (active.isNotEmpty() && !catalogOpen) {
            item(key = "active") {
                SettingsGroup("Downloads") { active.forEach { DownloadRow(it, packs) } }
            }
        }
        if (available.isNotEmpty()) {
            if (!catalogOpen) {
                item(key = "add") {
                    MainAction("Add a language", { catalogOpen = true }, icon = SlimIcons.Plus)
                }
            } else {
                item(key = "catalog") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GroupTitle("Add a language", Modifier.weight(1f))
                        TextButton(
                            onClick = { catalogOpen = false },
                            modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp),
                        ) { Text("Done") }
                    }
                    SettingsGroup {
                        available.forEach { DownloadRow(it, packs) }
                        TextButton(
                            onClick = { catalogOpen = false },
                            modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
                        ) { Text("Done") }
                    }
                }
            }
        }
        item(key = "about") {
            SettingsGroup {
                DisclosureRow("About word lists", aboutOpen, { aboutOpen = !aboutOpen })
                if (aboutOpen) {
                    GroupText(
                        "Word lists come from the Android Open Source Project keyboard (Apache 2.0). English (US) is " +
                            "built in and can be turned off. The others download from android.googlesource.com at a pinned " +
                            "version, are checked against their SHA-256, and are converted on the phone. Nothing you type is " +
                            "sent anywhere.",
                    )
                }
            }
        }
        item(key = "end") { Text("", Modifier.padding(8.dp)) }
    }
}

private fun isLastLanguage(
    language: KeyboardLanguage,
    installed: List<TypingKeyboard>,
    enabledKeyboards: Collection<String>,
): Boolean {
    val enabled = installed.any { it.language.id == language.id && it.id in enabledKeyboards }
    return enabled && installed.none { it.language.id != language.id && it.id in enabledKeyboards }
}

/**
 * A chip per letter layout of [language]: its own first, then the alternatives. Every chip that's
 * on is one stop of the language switcher. Chips sit in a fixed grid, so a checkmark appearing
 * never moves the others.
 */
@Composable
private fun LayoutChips(
    language: KeyboardLanguage,
    options: List<TypingKeyboard>,
    enabledKeyboards: Set<String>,
    onlyOneOn: Boolean,
    modifier: Modifier,
    onTap: (TypingKeyboard) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        val columns = when {
            fontScale >= 1.6f -> 1
            fontScale >= 1.2f -> 2
            maxWidth >= 560.dp -> KeyArrangement.entries.size
            else -> 3
        }
        Column {
            options.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { keyboard ->
                        val on = keyboard.id in enabledKeyboards
                        val required = on && onlyOneOn
                        FilterChip(
                            selected = on,
                            onClick = { onTap(keyboard) },
                            enabled = !required,
                            // The chip's description names the language too; don't read the label twice.
                            label = { Text(keyboard.layoutLabel, Modifier.clearAndSetSemantics {}, maxLines = 1) },
                            leadingIcon = if (on) ({ Icon(SlimIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                            modifier = Modifier.weight(1f).semantics {
                                contentDescription = "${language.name}, ${keyboard.layoutLabel}"
                                if (required) stateDescription = "On, required: the last layout that's on"
                            },
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun InstalledLanguageRow(
    language: KeyboardLanguage,
    enabled: Boolean,
    inUse: Boolean,
    last: Boolean,
    layoutLabel: String,
    onUse: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val status = listOfNotNull(
        language.englishName.takeIf { it != language.name },
        when {
            inUse -> "In use · $layoutLabel"
            enabled -> "On"
            else -> "Off"
        },
        "Built in".takeIf { language.bundled },
    ).joinToString(" · ")
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f)
                .clickable(enabled = !inUse, role = Role.Button, onClickLabel = "Use ${language.englishName}", onClick = onUse)
                .semantics { stateDescription = if (inUse) "In use" else if (enabled) "On" else "Off" }
                .padding(vertical = 14.dp),
        ) {
            Text(language.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (inUse) FontWeight.SemiBold else null)
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (inUse) MaterialTheme.colorScheme.primary else muted)
        }
        if (!language.bundled) {
            IconButton(onClick = onRemove, enabled = !last) {
                Icon(
                    SlimIcons.Trash,
                    contentDescription = "Remove ${language.englishName}",
                    tint = muted.copy(alpha = if (last) 0.38f else 1f),
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabled,
            enabled = !last,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp).semantics {
                contentDescription = "${language.englishName} enabled"
                if (last) stateDescription = "On, required: the last language that's on"
            },
        )
    }
}

@Composable
private fun DownloadRow(language: KeyboardLanguage, packs: LanguagePackState) {
    val progress = packs.downloads[language.id]
    val error = packs.errors[language.id]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(enabled = progress == null, role = Role.Button, onClickLabel = "Download ${language.englishName}") {
                    AppGraph.languagePacks.download(language.id)
                }
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(language.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(language.englishName.takeIf { it != language.name }, downloadSize(language))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            if (progress != null) {
                TextButton(
                    onClick = { AppGraph.cancelDownload(language.id) },
                    modifier = Modifier.semantics { contentDescription = "Cancel download of ${language.englishName}" },
                ) { Text("Cancel") }
            } else {
                IconButton(onClick = { AppGraph.languagePacks.download(language.id) }) {
                    Icon(
                        SlimIcons.Download,
                        contentDescription = (if (error != null) "Retry download of " else "Download ") + language.englishName,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        error?.let {
            Text(
                "Download failed: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
        }
        if (progress != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
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
