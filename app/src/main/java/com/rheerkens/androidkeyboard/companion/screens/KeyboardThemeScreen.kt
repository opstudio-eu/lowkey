package com.rheerkens.androidkeyboard.companion.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.companion.theme.isDarkTheme
import com.rheerkens.androidkeyboard.keyboard.ui.ColorRole
import com.rheerkens.androidkeyboard.keyboard.ui.ContrastCheck
import com.rheerkens.androidkeyboard.keyboard.ui.ContrastFix
import com.rheerkens.androidkeyboard.keyboard.ui.CustomKeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardBackgroundImage
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardSeed
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardTheme
import com.rheerkens.androidkeyboard.keyboard.ui.KeyboardThemePreview
import com.rheerkens.androidkeyboard.keyboard.ui.MAX_DIM
import com.rheerkens.androidkeyboard.keyboard.ui.MIN_DIM
import com.rheerkens.androidkeyboard.keyboard.ui.RoleGroup
import com.rheerkens.androidkeyboard.keyboard.ui.SeedRole
import com.rheerkens.androidkeyboard.keyboard.ui.blue8
import com.rheerkens.androidkeyboard.keyboard.ui.dimmingForImage
import com.rheerkens.androidkeyboard.keyboard.ui.fixContrast
import com.rheerkens.androidkeyboard.keyboard.ui.get
import com.rheerkens.androidkeyboard.keyboard.ui.green8
import com.rheerkens.androidkeyboard.keyboard.ui.parseHexColor
import com.rheerkens.androidkeyboard.keyboard.ui.red8
import com.rheerkens.androidkeyboard.keyboard.ui.standardKeyboardColors
import com.rheerkens.androidkeyboard.keyboard.ui.toHex
import com.rheerkens.androidkeyboard.settings.BackgroundImport
import com.rheerkens.androidkeyboard.settings.KeyboardThemeCodec
import com.rheerkens.androidkeyboard.settings.LoadedBackground
import com.rheerkens.androidkeyboard.settings.StoredKeyboardTheme
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The draft survives rotation and process recreation in the same form the store saves; an empty
 * string means no draft. A draft image whose file has gone shows as missing, not as a broken image.
 */
internal val DraftSaver = Saver<CustomKeyboardTheme?, String>(
    save = { draft ->
        draft?.let {
            val s = KeyboardThemeCodec.encode(KeyboardTheme(enabled = true, custom = it))
            listOf(s.colors.orEmpty(), s.overrides.orEmpty(), s.image.orEmpty(), s.dim?.toString().orEmpty()).joinToString("\n")
        } ?: ""
    },
    restore = { saved ->
        val parts = saved.split('\n')
        if (parts.size != 4) null
        else KeyboardThemeCodec.decode(
            StoredKeyboardTheme(
                enabled = true,
                colors = parts[0],
                overrides = parts[1].ifEmpty { null },
                image = parts[2].ifEmpty { null },
                dim = parts[3].toIntOrNull(),
            ),
        ).custom
    },
)

/** Pairs listed before "and N more". */
private const val SHOWN_FAILURES = 6

/**
 * Normalize case and an optional leading `#`, preserving invalid input so validation can explain
 * it. Never filter or truncate a paste into a different, valid colour.
 */
internal fun hexFieldInput(raw: String): String =
    raw.trim().removePrefix("#").uppercase()

/** Where a background image stands in the editor. */
private sealed interface ImageState {
    data object None : ImageState
    data object Loading : ImageState
    data object Missing : ImageState
    class Loaded(val image: LoadedBackground) : ImageState
}

/**
 * Keyboard theme: the standard palette (following the app theme) or one custom theme of five seed
 * colours, optional overrides for every other role, and an optional background image. Edits,
 * including the image, are a draft until Save; Cancel, Back or leaving the page drops them.
 */
@Composable
fun KeyboardThemeScreen(modifier: Modifier) {
    val store = AppGraph.keyboardTheme
    val theme by store.theme.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val standardSeed = KeyboardSeed.of(standardKeyboardColors(isDarkTheme(settings.theme)))
    val saved = theme.custom ?: CustomKeyboardTheme(standardSeed)
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()

    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<CustomKeyboardTheme?>(null) }
    var selectedName by rememberSaveable { mutableStateOf(ColorRole.BACKGROUND.name) }
    val selected = ColorRole.valueOf(selectedName)
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var imageError by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = draft ?: saved
    val dirty = draft != null && draft != saved
    fun edit(t: CustomKeyboardTheme) { draft = t }

    // Image files left by an earlier draft that was never saved; the saved one and this draft's stay.
    LaunchedEffect(Unit) { store.releaseUnused(draft?.image) }

    val imageState by produceState<ImageState>(ImageState.None, shown.image) {
        val name = shown.image
        value = if (name == null) ImageState.None else ImageState.Loading
        if (name != null) value = store.images.load(name)?.let { ImageState.Loaded(it) } ?: ImageState.Missing
    }
    val loaded = (imageState as? ImageState.Loaded)?.image?.takeIf { it.name == shown.image }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            imageError = null
            when (val result = store.images.import(resolver, uri)) {
                is BackgroundImport.Imported -> {
                    draft = (draft ?: saved).copy(image = result.name)
                    // A replaced draft image is deleted; the saved one never is.
                    store.releaseUnused(result.name)
                }
                is BackgroundImport.Failed -> imageError = result.error.message
            }
            importing = false
        }
    }

    LazyColumn(modifier.fillMaxWidth()) {
        item {
            ChoiceRow(
                "Keyboard colors", listOf(false, true), theme.enabled,
                { if (it) "Custom" else "Standard" },
                { custom ->
                    if (!custom) {
                        draft = null
                        store.releaseUnused()
                    }
                    store.setEnabled(custom, initial = standardSeed)
                },
                subtitle = if (theme.enabled) {
                    "Your colors and image. Switching to Standard keeps them for later."
                } else {
                    "OpStudio colors, light or dark with the app theme." +
                        if (theme.custom != null) " Your custom theme is kept." else ""
                },
            )
        }
        if (!theme.enabled) return@LazyColumn

        item {
            val colors = remember(shown) { shown.effectiveColors() }
            KeyboardThemePreview(
                colors,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                image = loaded?.let { KeyboardBackgroundImage(it.bitmap, shown.dim) },
            )
        }
        item {
            Note(
                if (dirty) "Unsaved changes. The keyboard keeps its saved theme until you save."
                else "Save, then tap Test keyboard to try the theme on the real keyboard.",
            )
        }
        item { SectionTitle("Colors") }
        item { Swatches(shown.seed, selected.seed) { selectedName = ColorRole.forSeed(it).name } }
        selected.seed?.let { seedRole ->
            item {
                ColorPicker(seedRole.label, selected, shown.seed[seedRole]) { edit(shown.with(selected, it)) }
            }
        }
        item { SectionTitle("Background image") }
        item {
            ImageSection(
                shown, imageState, importing, imageError,
                onChoose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = {
                    imageError = null
                    edit(shown.copy(image = null))
                    store.releaseUnused(null)
                },
                onDim = { edit(shown.copy(dim = it)) },
            )
        }
        item {
            AdvancedHeader(advancedOpen, shown.overrides.size) { advancedOpen = !advancedOpen }
        }
        if (advancedOpen) {
            val colors = shown.effectiveColors()
            RoleGroup.entries.forEach { group ->
                item(key = "group-${group.name}") {
                    Text(
                        group.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                ColorRole.entries.filter { it.group == group }.forEach { role ->
                    item(key = "role-${role.name}") {
                        RoleRow(
                            role, colors[role], overridden = role in shown.overrides, open = role == selected,
                            onToggle = { selectedName = if (role == selected) ColorRole.BACKGROUND.name else role.name },
                        )
                        if (role == selected && role.seed == null) {
                            ColorPicker(role.label, role, colors[role]) { edit(shown.with(role, it)) }
                            if (role in shown.overrides) {
                                TextButton(
                                    onClick = { edit(shown.with(role, null)) },
                                    modifier = Modifier.padding(start = 8.dp),
                                ) { Text("Reset to automatic") }
                            }
                        }
                    }
                }
            }
        }
        item { ContrastSection(shown, loaded, ::edit) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val restored = shown.copy(seed = standardSeed, overrides = emptyMap())
                TextButton(onClick = { draft = restored }, enabled = shown != restored && !importing) { Text("Restore default") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        draft = null
                        imageError = null
                        store.releaseUnused()
                    },
                    enabled = dirty && !importing,
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        draft?.let(store::save)
                        draft = null
                    },
                    enabled = dirty && !importing,
                ) { Text("Save") }
            }
        }
        item {
            Note(
                "Restore default sets the five colors to the standard ones for the current app theme and clears " +
                    "advanced colors; the image stays. Save to use it. To stop using the custom theme, choose " +
                    "Standard at the top; your theme is kept.",
            )
        }
        item { Spacer(Modifier.padding(12.dp)) }
    }
}

@Composable
private fun Swatches(seed: KeyboardSeed, selected: SeedRole?, onSelect: (SeedRole) -> Unit) {
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

@Composable
private fun ImageSection(
    theme: CustomKeyboardTheme,
    state: ImageState,
    importing: Boolean,
    error: String?,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onDim: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val status = when {
            importing -> "Preparing the image…"
            theme.image == null -> "None. The keyboard uses the background color."
            state is ImageState.Missing -> "The image file is missing. The keyboard uses the background color; choose the image again or remove it."
            state is ImageState.Loading -> "Loading…"
            else -> "Fills the keyboard behind the keys, cropped from the middle. Keys stay solid."
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChoose, enabled = !importing) {
                Text(if (theme.image == null) "Choose image" else "Replace image")
            }
            if (theme.image != null) {
                TextButton(onClick = onRemove, enabled = !importing) { Text("Remove image") }
            }
        }
        if (theme.image != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dimming", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                Slider(
                    value = theme.dim.toFloat(),
                    onValueChange = { v -> (v / 5).roundToInt().times(5).coerceIn(MIN_DIM, MAX_DIM).let { if (it != theme.dim) onDim(it) } },
                    valueRange = MIN_DIM.toFloat()..MAX_DIM.toFloat(),
                    steps = (MAX_DIM - MIN_DIM) / 5 - 1,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = "Dimming"
                        stateDescription = "${theme.dim} percent"
                    },
                )
                Text(
                    "${theme.dim} %",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
            Text(
                "Covers the image with the background color so text stays readable. Only this app keeps a copy of the image, without its location or other details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvancedHeader(open: Boolean, overrides: Int, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Advanced colors", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                if (overrides == 0) "Every color follows the five above automatically."
                else if (overrides == 1) "1 color set by hand; the rest follow the five above."
                else "$overrides colors set by hand; the rest follow the five above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(if (open) SlimIcons.ChevronUp else SlimIcons.ChevronDown, null)
    }
}

@Composable
private fun RoleRow(role: ColorRole, color: Color, overridden: Boolean, open: Boolean, onToggle: () -> Unit) {
    val state = when {
        role.seed != null -> "Set by ${role.seed.label} above"
        overridden -> "Custom"
        else -> "Automatic"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = role.seed == null, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = "${role.label}, $state, #${color.toHex()}"
                selected = open
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .padding(2.dp)
                .background(color, CircleShape),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(role.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$state · #${color.toHex()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (overridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Red, green and blue sliders and a six-digit hex field for one colour. The sliders always show
 * [color]; the field shows what was typed until it is a valid colour, which then becomes [color].
 * A slider move rewrites the field, so the two never disagree once the field is valid.
 */
@Composable
private fun ColorPicker(label: String, key: ColorRole, color: Color, onChange: (Color) -> Unit) {
    var hex by rememberSaveable(key) { mutableStateOf(color.toHex()) }
    LaunchedEffect(color) { if (parseHexColor(hex) != color) hex = color.toHex() }
    val valid = parseHexColor(hex) != null
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = hex,
            onValueChange = { raw ->
                hex = hexFieldInput(raw)
                parseHexColor(hex)?.let(onChange)
            },
            label = { Text("$label hex color") },
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

/** "Key text on keys: 2.3:1, needs 4.5:1", marked when one of its colours is set by hand. */
internal fun failureLine(check: ContrastCheck, overrides: Set<ColorRole>): String {
    val manual = check.foregroundRole in overrides || check.surfaceRole in overrides
    return "• ${check.description}" + if (manual) " (advanced color)" else ""
}

/**
 * The pairs below WCAG AA with their ratios, and fixes when they help: seed changes (never
 * overrides or the image) and, with an image, more dimming. Both are worked out in the background;
 * the fix's button says "Fix contrast" only when it clears every pair, image included.
 */
@Composable
private fun ContrastSection(theme: CustomKeyboardTheme, image: LoadedBackground?, onApply: (CustomKeyboardTheme) -> Unit) {
    val sample = image?.takeIf { it.name == theme.image }?.sample
    val failures = remember(theme, sample) { theme.contrastFailures(sample) }
    var fix by remember { mutableStateOf<Triple<CustomKeyboardTheme, ContrastFix, Int?>?>(null) }
    LaunchedEffect(theme, sample) {
        if (failures.isEmpty()) return@LaunchedEffect
        delay(250) // Let slider drags settle.
        val result = withContext(Dispatchers.Default) {
            Triple(theme, fixContrast(theme, sample), dimmingForImage(theme, sample))
        }
        fix = result
    }
    val scheme = MaterialTheme.colorScheme
    val imageNote = "Text straight on the image is checked against its lightest and darkest parts after dimming, " +
        "measured on a small copy of the image; fine detail is not measured."
    if (failures.isEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SlimIcons.Check, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Text(
                "All text and icons meet the WCAG AA contrast minimum.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (sample != null) Note(imageNote)
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
            val manual = theme.overrides.keys
            failures.take(SHOWN_FAILURES).forEach { Text(failureLine(it, manual), style = MaterialTheme.typography.bodySmall) }
            if (failures.size > SHOWN_FAILURES) {
                Text("and ${failures.size - SHOWN_FAILURES} more", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "You can still save this theme, but some text may be hard to read.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (sample != null) Text(imageNote, style = MaterialTheme.typography.bodySmall)
            val current = fix?.takeIf { it.first == theme }
            if (current == null) {
                Text("Looking for a fix…", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            val (_, seedFix, dim) = current
            if (seedFix.remaining.size < failures.size) {
                val changed = SeedRole.entries.filter { seedFix.seed[it] != theme.seed[it] }.joinToString(", ") { it.label }
                Text(
                    if (seedFix.resolvesAll) "Fix contrast changes: $changed."
                    else "Improve contrast changes $changed and fixes ${failures.size - seedFix.remaining.size} of ${failures.size} " +
                        "pairs. Advanced colors and the image are not changed; adjust the rest by hand.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { onApply(seedFix.theme) }) {
                    Text(if (seedFix.resolvesAll) "Fix contrast" else "Improve contrast")
                }
            }
            if (dim != null) {
                Text("More dimming makes the text over the image readable.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onApply(theme.copy(dim = dim)) }) { Text("Set dimming to $dim %") }
            }
            if (seedFix.remaining.size >= failures.size && dim == null) {
                Text("No automatic fix found; change the colors by hand.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
