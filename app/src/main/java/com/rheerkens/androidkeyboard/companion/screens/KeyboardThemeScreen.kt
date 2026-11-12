package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.theme.isDarkTheme
import com.rheerkens.androidkeyboard.keyboard.ui.ContrastFix
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardSeed
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardThemePreview
import com.rheerkens.androidkeyboard.keyboard.ui.SeedRole
import com.rheerkens.androidkeyboard.keyboard.ui.blue8
import com.rheerkens.androidkeyboard.keyboard.ui.deriveKeyboardColors
import com.rheerkens.androidkeyboard.keyboard.ui.fixContrast
import com.rheerkens.androidkeyboard.keyboard.ui.green8
import com.rheerkens.androidkeyboard.keyboard.ui.keyboardContrastFailures
import com.rheerkens.androidkeyboard.keyboard.ui.parseHexColor
import com.rheerkens.androidkeyboard.keyboard.ui.red8
import com.rheerkens.androidkeyboard.keyboard.ui.standardKeyboardColors
import com.rheerkens.androidkeyboard.keyboard.ui.toHex
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Draft colours survive rotation; an empty string means no draft. */
private val DraftSaver = Saver<KeyboardSeed?, String>(save = { it?.encode() ?: "" }, restore = { KeyboardSeed.decode(it) })

/** Pairs listed before "and N more". */
private const val SHOWN_FAILURES = 6

/**
 * What the hex field keeps of typed or pasted text: hex digits only, upper case, without `#`.
 * Up to eight are kept so a pasted colour with alpha shows as too long instead of being cut to a
 * different colour.
 */
internal fun hexFieldInput(raw: String): String =
    raw.trim().removePrefix("#").filter { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }.uppercase().take(8)

/**
 * Keyboard theme: the standard palette (following the app theme) or one custom palette of five
 * colours. Edits are a draft until Save; Cancel, Back or leaving the page drops them.
 */
@Composable
fun KeyboardThemeScreen(modifier: Modifier) {
    val store = AppGraph.keyboardTheme
    val theme by store.theme.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val standardSeed = KeyboardSeed.of(standardKeyboardColors(isDarkTheme(settings.theme)))
    val saved = theme.seed ?: standardSeed

    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<KeyboardSeed?>(null) }
    var role by rememberSaveable { mutableStateOf(SeedRole.BACKGROUND) }
    val shown = draft ?: saved
    val dirty = draft != null && draft != saved
    fun edit(seed: KeyboardSeed) { draft = seed }

    LazyColumn(modifier.fillMaxWidth()) {
        item {
            ChoiceRow(
                "Keyboard colors", listOf(false, true), theme.enabled,
                { if (it) "Custom" else "Standard" },
                { custom ->
                    if (!custom) draft = null
                    store.setEnabled(custom, initial = standardSeed)
                },
                subtitle = if (theme.enabled) {
                    "Your five colors. Switching to Standard keeps them for later."
                } else {
                    "OpStudio colors, light or dark with the app theme." +
                        if (theme.seed != null) " Your custom colors are kept." else ""
                },
            )
        }
        if (!theme.enabled) return@LazyColumn

        item {
            KeyboardThemePreview(
                remember(shown) { deriveKeyboardColors(shown) },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Note(
                if (dirty) "Unsaved changes. The keyboard keeps its saved colors until you save."
                else "Save, then tap Test keyboard to try the colors on the real keyboard.",
            )
        }
        item { SectionTitle("Colors") }
        item { Swatches(shown, role) { role = it } }
        item { ColorPicker(role, shown[role]) { edit(shown.with(role, it)) } }
        item { ContrastSection(shown, ::edit) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { draft = standardSeed }, enabled = shown != standardSeed) { Text("Restore default") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { draft = null }, enabled = dirty) { Text("Cancel") }
                Button(
                    onClick = {
                        draft?.let(store::save)
                        draft = null
                    },
                    enabled = dirty,
                ) { Text("Save") }
            }
        }
        item {
            Note(
                "Restore default sets the five colors to the standard ones for the current app theme; save to use them. " +
                    "To stop using custom colors, choose Standard at the top.",
            )
        }
        item { Spacer(Modifier.padding(12.dp)) }
    }
}

@Composable
private fun Swatches(seed: KeyboardSeed, selected: SeedRole, onSelect: (SeedRole) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        SeedRole.entries.forEach { r ->
            val on = r == selected
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onSelect(r) }
                    .semantics {
                        this.selected = on
                        contentDescription = "${r.label}, #${seed[r].toHex()}"
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(if (on) 5.dp else 3.dp)
                        .background(seed[r], CircleShape),
                )
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    // The swatch's description already names the role.
                    modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics {},
                )
            }
        }
    }
}

/**
 * Red, green and blue sliders and a six-digit hex field for one colour. The sliders always show
 * [color]; the field shows what was typed until it is a valid colour, which then becomes [color].
 * A slider move rewrites the field, so the two never disagree once the field is valid.
 */
@Composable
private fun ColorPicker(role: SeedRole, color: Color, onChange: (Color) -> Unit) {
    var hex by rememberSaveable(role) { mutableStateOf(color.toHex()) }
    LaunchedEffect(color) { if (parseHexColor(hex) != color) hex = color.toHex() }
    val valid = parseHexColor(hex) != null
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = hex,
            onValueChange = { raw ->
                hex = hexFieldInput(raw)
                parseHexColor(hex)?.let(onChange)
            },
            label = { Text("${role.label} hex color") },
            prefix = { Text("#") },
            singleLine = true,
            isError = !valid,
            supportingText = {
                Text(
                    if (valid) "Six digits, 0–9 and A–F. Colors are always opaque."
                    else if (hex.length > 6) "Use six digits. Transparency isn't supported."
                    else "Enter six digits, 0–9 and A–F, like 1E4632.",
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Channel("Red", color.red8()) { onChange(Color(it, color.green8(), color.blue8())) }
        Channel("Green", color.green8()) { onChange(Color(color.red8(), it, color.blue8())) }
        Channel("Blue", color.blue8()) { onChange(Color(color.red8(), color.green8(), it)) }
    }
}

@Composable
private fun Channel(name: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { v -> v.roundToInt().coerceIn(0, 255).let { if (it != value) onChange(it) } },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f).semantics { contentDescription = name },
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
    }
}

/**
 * The pairs below WCAG AA with their ratios, and a fix when one helps. The fix is worked out in the
 * background; its button says "Fix contrast" only when it clears every pair.
 */
@Composable
private fun ContrastSection(seed: KeyboardSeed, onApply: (KeyboardSeed) -> Unit) {
    val failures = remember(seed) { keyboardContrastFailures(deriveKeyboardColors(seed)) }
    var fix by remember { mutableStateOf<Pair<KeyboardSeed, ContrastFix>?>(null) }
    LaunchedEffect(seed) {
        if (failures.isEmpty()) return@LaunchedEffect
        delay(250) // Let slider drags settle.
        val result = withContext(Dispatchers.Default) { fixContrast(seed) }
        fix = seed to result
    }
    val scheme = MaterialTheme.colorScheme
    if (failures.isEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SlimIcons.Check, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Text(
                "All text and icons meet the WCAG AA contrast minimum.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        return
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (failures.size == 1) "1 pair has low contrast" else "${failures.size} pairs have low contrast",
                style = MaterialTheme.typography.titleSmall,
            )
            failures.take(SHOWN_FAILURES).forEach { Text("• ${it.description}", style = MaterialTheme.typography.bodySmall) }
            if (failures.size > SHOWN_FAILURES) {
                Text("and ${failures.size - SHOWN_FAILURES} more", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "You can still save these colors, but some text may be hard to read.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            val current = fix?.takeIf { it.first == seed }?.second
            if (current == null) {
                Text("Looking for a fix…", style = MaterialTheme.typography.bodySmall)
            } else if (current.remaining.size < failures.size) {
                val changed = SeedRole.entries.filter { current.seed[it] != seed[it] }.joinToString(", ") { it.label }
                Text(
                    if (current.resolvesAll) "Fix contrast changes: $changed."
                    else "Improve contrast changes $changed and fixes ${failures.size - current.remaining.size} of ${failures.size} " +
                        "pairs. The rest conflict with each other; change the colors by hand.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { onApply(current.seed) }) {
                    Text(if (current.resolvesAll) "Fix contrast" else "Improve contrast")
                }
            } else {
                Text("No automatic fix found; change the colors by hand.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
