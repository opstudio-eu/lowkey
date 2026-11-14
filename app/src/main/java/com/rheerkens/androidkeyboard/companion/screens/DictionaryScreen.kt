package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.dictionary.DictionaryStore
import com.rheerkens.androidkeyboard.dictionary.PersonalTerm
import com.rheerkens.androidkeyboard.dictionary.PhraseRule
import com.rheerkens.androidkeyboard.ui.SlimIcons

@Composable
fun DictionaryScreen(modifier: Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("My words") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Speech rules") })
        }
        if (tab == 0) WordsTab() else RulesTab()
    }
}

/** The count (or search result) on the left, the tab's main Add action on the right. */
@Composable
private fun SummaryWithAction(summary: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            summary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        androidx.compose.material3.Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(SlimIcons.Plus, null, Modifier.padding(end = 6.dp))
            Text(action)
        }
    }
}

@Composable
private fun WordsTab() {
    val store = AppGraph.dictionary
    val state by store.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<PersonalTerm?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PersonalTerm?>(null) }
    val shown = filterTerms(state.terms, query)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "summary") {
            SummaryWithAction(wordsSummary(shown.size, state.terms.size, query), "Add word") { adding = true }
        }
        if (state.terms.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    "No words yet",
                    "Add names, brands or terms. Suggestions and autocorrect keep their exact spelling and capitals.",
                )
            }
        } else {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search my words") },
                    leadingIcon = { Icon(SlimIcons.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) ({
                        IconButton(onClick = { query = "" }) { Icon(SlimIcons.Close, "Clear search") }
                    }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(shown.size, key = { shown[it].id }) { index ->
            val term = shown[index]
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Edit") { editing = term }
                    .padding(start = 20.dp, end = 8.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    term.text,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                IconButton(onClick = { deleting = term }) {
                    Icon(SlimIcons.Trash, "Delete ${term.text}", tint = muted)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (state.terms.isNotEmpty()) item(key = "meaning") {
            Text(
                "Suggestions and autocorrect keep these exact spellings. Tap a word to edit it.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
            )
        }
        item(key = "dictation") {
            SettingsGroup("When you dictate", Modifier.padding(top = 4.dp)) {
                SwitchRow(
                    "Match the spelling of my words", settings.matchTermCase,
                    { on -> AppGraph.settings.update { it.copy(matchTermCase = on) } },
                    subtitle = "Dictated text gets the exact capitals of your words, e.g. t3 code → T3 Code. " +
                        "Speech recognition itself isn't trained.",
                )
            }
        }
        item(key = "end") { Spacer(Modifier.height(16.dp)) }
    }

    if (adding || editing != null) {
        val original = editing
        TermDialog(
            initial = original?.text.orEmpty(),
            title = if (original == null) "Add a word" else "Edit word",
            validate = { termError(it, state.terms, original?.id) },
            onDismiss = { adding = false; editing = null },
            onSave = { text ->
                if (original == null) {
                    when (store.addTerm(text)) {
                        is DictionaryStore.AddResult.Added -> null
                        DictionaryStore.AddResult.AlreadyExists -> "“${text.trim()}” is already in your words."
                        DictionaryStore.AddResult.Invalid -> "Enter 1 to ${MAX_WORD_LENGTH} characters."
                    }
                } else {
                    if (store.editTerm(original.id, text)) null else termError(text, state.terms, original.id) ?: "Couldn't save this word."
                }
            },
            onDelete = original?.let { term -> { editing = null; deleting = term } },
        )
    }
    deleting?.let { term ->
        ConfirmDeleteDialog(
            title = "Delete “${term.text}”?",
            text = "Suggestions, autocorrect and dictation stop keeping its exact spelling.",
            onConfirm = { store.deleteTerm(term.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TermDialog(
    initial: String,
    title: String,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onSave: (String) -> String?,
    onDelete: (() -> Unit)?,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save() {
        val e = validate(text) ?: onSave(text)
        if (e == null) onDismiss() else error = e
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("Word or phrase") },
                    placeholder = { Text("e.g. Gradle wrapper") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    supportingText = {
                        Text(error ?: "Saved exactly as typed. ${text.trim().length}/${MAX_WORD_LENGTH} characters.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Save") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun RulesTab() {
    val store = AppGraph.dictionary
    val state by store.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PhraseRule?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PhraseRule?>(null) }
    val active = settings.applyPhraseRules
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "master") {
            SettingsGroup(
                modifier = Modifier.padding(top = 8.dp),
                footer = if (active) null else "Off: dictation is inserted exactly as recognized. Each rule keeps its own choice.",
            ) {
                SwitchRow(
                    title = "Use speech rules",
                    subtitle = "After you stop dictating, replace what you said with how you write it. Live text stays as heard.",
                    checked = active,
                    onCheckedChange = { on -> AppGraph.settings.update { it.copy(applyPhraseRules = on) } },
                )
            }
        }
        item(key = "add") {
            SummaryWithAction(rulesSummary(state.rules.size, state.rules.count { it.enabled }, active), "Add rule") { adding = true }
        }
        if (state.rules.isEmpty()) {
            item(key = "empty") {
                EmptyState("No speech rules yet", "A rule turns a spoken phrase into the text you want, e.g. view model → ViewModel.")
            }
        }
        items(state.rules.size, key = { state.rules[it].id }) { index ->
            val rule = state.rules[index]
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Edit") { editing = rule }
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.written, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                    Text("When you say “${rule.spoken}”", style = MaterialTheme.typography.bodyMedium, color = muted)
                    Text(
                        ruleStatus(rule.enabled, active),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rule.enabled && active) MaterialTheme.colorScheme.primary else muted,
                    )
                }
                // Stays usable while speech rules are off, so a rule can be prepared without losing its choice.
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { store.setRuleEnabled(rule.id, it) },
                    modifier = Modifier.semantics {
                        contentDescription = "Use rule ${rule.spoken} to ${rule.written}"
                        stateDescription = ruleStatus(rule.enabled, active)
                    },
                )
                IconButton(onClick = { deleting = rule }) { Icon(SlimIcons.Trash, "Delete rule ${rule.spoken}", tint = muted) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        item(key = "end") { Spacer(Modifier.height(16.dp)) }
    }

    if (adding || editing != null) {
        val original = editing
        RuleDialog(
            initial = original,
            onDismiss = { adding = false; editing = null },
            onSave = { spoken, written ->
                if (original == null) store.addRule(spoken, written) else store.editRule(original.id, spoken, written)
            },
            onDelete = original?.let { rule -> { editing = null; deleting = rule } },
        )
    }
    deleting?.let { rule ->
        ConfirmDeleteDialog(
            title = "Delete this rule?",
            text = "“${rule.spoken}” will be inserted as heard instead of “${rule.written}”.",
            onConfirm = { store.deleteRule(rule.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RuleDialog(initial: PhraseRule?, onDismiss: () -> Unit, onSave: (String, String) -> Boolean, onDelete: (() -> Unit)?) {
    var spoken by rememberSaveable { mutableStateOf(initial?.spoken.orEmpty()) }
    var written by rememberSaveable { mutableStateOf(initial?.written.orEmpty()) }
    var tried by remember { mutableStateOf(false) }
    val spokenMissing = tried && spoken.isBlank()
    val writtenMissing = tried && written.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a speech rule" else "Edit speech rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    spoken, { spoken = it },
                    label = { Text("When you say") }, placeholder = { Text("view model") }, singleLine = true,
                    isError = spokenMissing,
                    supportingText = { Text(if (spokenMissing) "Enter what you say." else "Matched without regard to capitals.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    written, { written = it },
                    label = { Text("Write") }, placeholder = { Text("ViewModel") }, singleLine = true,
                    isError = writtenMissing,
                    supportingText = { Text(if (writtenMissing) "Enter what to write." else "Inserted exactly as typed.") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tried = true
                if (spoken.isNotBlank() && written.isNotBlank() && onSave(spoken, written)) onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "1 word", "2 rules": English singular and plural. */
internal fun countLabel(n: Int, singular: String, plural: String): String = "$n ${if (n == 1) singular else plural}"

/** Words matching the search (any part, any case), newest first. */
internal fun filterTerms(terms: List<PersonalTerm>, query: String): List<PersonalTerm> {
    val q = query.trim()
    return terms.filter { q.isEmpty() || it.text.contains(q, ignoreCase = true) }.sortedByDescending { it.addedAt }
}

internal fun wordsSummary(shown: Int, total: Int, query: String): String {
    val q = query.trim()
    return when {
        q.isEmpty() -> countLabel(total, "word", "words")
        shown == 0 -> "No words match “$q”"
        else -> "${countLabel(shown, "match", "matches")} for “$q”"
    }
}

internal fun rulesSummary(total: Int, enabled: Int, active: Boolean): String = when {
    total == 0 -> "No rules"
    !active -> "${countLabel(total, "rule", "rules")} · inactive"
    else -> "${countLabel(total, "rule", "rules")} · $enabled on"
}

internal fun ruleStatus(enabled: Boolean, active: Boolean): String = when {
    !enabled -> "Off"
    active -> "On"
    else -> "On, inactive while speech rules are off"
}

/**
 * Why [text] can't be saved, with the same rules as [DictionaryStore.addTerm] and
 * [DictionaryStore.editTerm]: trimmed, 1 to [MAX_WORD_LENGTH] characters, and no
 * other saved word with exactly this spelling. Null when it can be saved.
 */
internal fun termError(text: String, terms: List<PersonalTerm>, editingId: Long?): String? {
    val clean = text.trim()
    return when {
        clean.isEmpty() -> "Enter a word or phrase."
        clean.length > MAX_WORD_LENGTH -> "Use at most ${MAX_WORD_LENGTH} characters."
        terms.any { it.text == clean && it.id != editingId } -> "“$clean” is already in your words."
        else -> null
    }
}

/** The longest word or phrase [DictionaryStore] accepts (its own limit is private). */
internal const val MAX_WORD_LENGTH = 48
