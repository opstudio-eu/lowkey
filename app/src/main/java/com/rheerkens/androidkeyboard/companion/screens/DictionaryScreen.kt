package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Personal terms") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Speech phrase rules") })
        }
        if (tab == 0) TermsTab() else RulesTab()
    }
}

@Composable
private fun TermsTab() {
    val store = AppGraph.dictionary
    val state by store.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<PersonalTerm?>(null) }
    var adding by remember { mutableStateOf(false) }
    val shown = state.terms
        .filter { query.isBlank() || it.text.contains(query.trim(), ignoreCase = true) }
        .sortedByDescending { it.addedAt }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Note(
                "Terms keep their exact spelling and case in suggestions and autocorrect. " +
                    "Multiword terms are allowed. Terms do not train speech recognition.",
                Modifier.padding(top = 12.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search terms") },
                    leadingIcon = { Icon(SlimIcons.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { adding = true }) {
                    Icon(SlimIcons.Plus, null, Modifier.padding(end = 4.dp))
                    Text("Add")
                }
            }
        }
        if (shown.isEmpty()) {
            item { Note(if (query.isBlank()) "No saved terms yet." else "No terms match “$query”.") }
        }
        items(shown, key = { it.id }) { term ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    term.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { editing = term }) { Icon(SlimIcons.Edit, "Edit ${term.text}") }
                IconButton(onClick = { store.deleteTerm(term.id) }) { Icon(SlimIcons.Trash, "Delete ${term.text}") }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        item { ImportExportRow() }
    }

    if (adding || editing != null) {
        val original = editing
        TermDialog(
            initial = original?.text.orEmpty(),
            title = if (original == null) "Add term" else "Edit term",
            onDismiss = { adding = false; editing = null },
            onSave = { text ->
                if (original == null) {
                    when (store.addTerm(text)) {
                        is DictionaryStore.AddResult.Added -> null
                        DictionaryStore.AddResult.AlreadyExists -> "That term is already saved."
                        DictionaryStore.AddResult.Invalid -> "Enter 1–48 characters."
                    }
                } else {
                    if (store.editTerm(original.id, text)) null else "Enter a new, non-empty term."
                }
            },
        )
    }
}

@Composable
private fun TermDialog(initial: String, title: String, onDismiss: () -> Unit, onSave: (String) -> String?) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("Exact spelling") },
                    placeholder = { Text("e.g. Gradle wrapper") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Case is kept exactly as typed.") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = onSave(text)
                if (e == null) onDismiss() else error = e
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RulesTab() {
    val store = AppGraph.dictionary
    val state by store.state.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PhraseRule?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            SwitchRow(
                title = "Apply phrase rules after Stop",
                subtitle = "Master switch. Off: dictation is inserted exactly as recognized.",
                checked = settings.applyPhraseRules,
                onCheckedChange = { on -> AppGraph.settings.update { it.copy(applyPhraseRules = on) } },
            )
            Note("Rules apply once to the final speech text after Stop. They don't change live text or train recognition.")
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = { adding = true }) {
                    Icon(SlimIcons.Plus, null, Modifier.padding(end = 4.dp))
                    Text("Add rule")
                }
            }
        }
        items(state.rules, key = { it.id }) { rule ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.spoken, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "→ ${rule.written}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { store.setRuleEnabled(rule.id, it) },
                    enabled = settings.applyPhraseRules,
                    modifier = Modifier.semantics { contentDescription = "Rule ${rule.spoken} to ${rule.written}" },
                )
                IconButton(onClick = { editing = rule }) { Icon(SlimIcons.Edit, "Edit rule ${rule.spoken}") }
                IconButton(onClick = { store.deleteRule(rule.id) }) { Icon(SlimIcons.Trash, "Delete rule ${rule.spoken}") }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        item { ImportExportRow() }
    }

    if (adding || editing != null) {
        val original = editing
        RuleDialog(
            initial = original,
            onDismiss = { adding = false; editing = null },
            onSave = { spoken, written ->
                if (original == null) store.addRule(spoken, written) else store.editRule(original.id, spoken, written)
            },
        )
    }
}

@Composable
private fun RuleDialog(initial: PhraseRule?, onDismiss: () -> Unit, onSave: (String, String) -> Boolean) {
    var spoken by remember { mutableStateOf(initial?.spoken.orEmpty()) }
    var written by remember { mutableStateOf(initial?.written.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add phrase rule" else "Edit phrase rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(spoken, { spoken = it; error = false }, label = { Text("Spoken") }, placeholder = { Text("view model") }, singleLine = true)
                OutlinedTextField(
                    written, { written = it; error = false }, label = { Text("Written") }, placeholder = { Text("ViewModel") }, singleLine = true,
                    isError = error, supportingText = { if (error) Text("Fill in both fields.") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (onSave(spoken, written)) onDismiss() else error = true }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImportExportRow() {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = {}, enabled = false) { Text("Import") }
        OutlinedButton(onClick = {}, enabled = false) { Text("Export") }
        Text("Coming later", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
