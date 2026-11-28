package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.BuildConfig
import com.rheerkens.androidkeyboard.ai.AiConnection
import com.rheerkens.androidkeyboard.ai.AiService
import com.rheerkens.androidkeyboard.ai.ImageModel
import com.rheerkens.androidkeyboard.ai.ImageQuality
import com.rheerkens.androidkeyboard.ai.Secret
import com.rheerkens.androidkeyboard.ai.gallerySupported
import com.rheerkens.androidkeyboard.settings.Settings

/**
 * Settings › Experimental: features still being tried out. For now, AI on the keyboard (✨),
 * with OpenAI or the user's own OpenAI-compatible server, which creates images; later actions (such
 * as cleaning up text) share the same connection.
 */
@Composable
fun ExperimentalScreen(modifier: Modifier) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val saved by AppGraph.secrets.saved.collectAsStateWithLifecycle()
    val refused by AppGraph.secrets.refused.collectAsStateWithLifecycle()
    val update: ((Settings) -> Settings) -> Unit = { AppGraph.settings.update(it) }
    // A search result for a row below the fold scrolls it into view.
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    RevealSettingHighlight(list)
    LazyColumn(modifier.fillMaxWidth(), state = list) {
        item { PageSummary("Features still being tried out. They may change or go away in a later version.") }
        item {
            SettingsGroup("AI on the keyboard") {
                SwitchRow(
                    "AI on the keyboard", s.aiKeyboard, { v -> update { it.copy(aiKeyboard = v) } },
                    subtitle = "Adds ✨ at the right of the keyboard's top strip: a menu of AI actions. For now it " +
                        "creates images, which land in your clipboard.",
                )
                if (s.aiKeyboard) {
                    ChoiceDialogRow(
                        "AI service", AiService.entries, s.aiService, { it.label },
                        { v -> update { it.copy(aiService = v) } },
                        detail = { serviceDetail(it) },
                    )
                    when (s.aiService) {
                        AiService.OPENAI -> SecretRow(
                            "OpenAI API key", Secret.OPENAI_API_KEY, saved[Secret.OPENAI_API_KEY],
                            refusedBy = "OpenAI".takeIf { AppGraph.secrets.isRefused(Secret.OPENAI_API_KEY, saved, refused) },
                            help = "OpenAI bills that account for what you create.",
                            required = true, field = "Key", placeholder = "sk-…",
                            link = "Create an OpenAI API key" to "https://platform.openai.com/api-keys",
                            looksWrong = { if (it.startsWith("sk-")) null else "OpenAI keys usually start with sk-" },
                        )
                        AiService.OWN_SERVER -> {
                            ServerAddressRow(s.aiServerUrl) { v -> update { it.copy(aiServerUrl = v) } }
                            SecretRow(
                                "Access token", Secret.SERVER_TOKEN, saved[Secret.SERVER_TOKEN],
                                refusedBy = "Your server".takeIf { AppGraph.secrets.isRefused(Secret.SERVER_TOKEN, saved, refused) },
                                help = "Optional. Sent as a bearer token, if your server asks for one.",
                                required = false, field = "Token",
                            )
                        }
                    }
                }
            }
        }
        if (s.aiKeyboard) item {
            SettingsGroup("Create image", footer = "The chip in the ✨ prompt box also switches the service and sets the quality, and with a working OpenAI key, the model.") {
                when (s.aiService) {
                    AiService.OPENAI -> {
                        val models = (ImageModel.entries.map { it.id } + s.imageModel).distinct()
                        ChoiceDialogRow(
                            "Image model", models, s.imageModel,
                            { id -> ImageModel.entries.firstOrNull { it.id == id }?.label ?: id },
                            { v -> update { it.copy(imageModel = v) } },
                            detail = { id -> ImageModel.entries.firstOrNull { it.id == id }?.let { "${it.speed}. ${it.detail}" } },
                        )
                    }
                    AiService.OWN_SERVER -> {
                        TextSettingRow(
                            "Server model", s.imageServerModel.ifBlank { "Server default" }, initial = s.imageServerModel,
                            placeholder = "Leave empty for the server's default",
                            help = "Sent as model, if your server needs one.",
                        ) { v -> update { it.copy(imageServerModel = v) } }
                        TextSettingRow(
                            "Text model", s.promptServerModel.ifBlank { "Server default" }, initial = s.promptServerModel,
                            placeholder = "Leave empty for the server's default",
                            help = "Rewrites your prompt when you tap Improve in the prompt box. Sent as model, if your server needs one.",
                        ) { v -> update { it.copy(promptServerModel = v) } }
                    }
                }
                ChoiceDialogRow(
                    "Image quality", ImageQuality.entries, s.imageQuality, { it.label },
                    { v -> update { it.copy(imageQuality = v) } },
                    detail = { if (s.aiService == AiService.OPENAI) "${it.detail} · ${it.cost} per image" else it.detail },
                )
                SwitchRow(
                    "Save to Gallery", s.saveImagesToGallery && gallerySupported,
                    { v -> update { it.copy(saveImagesToGallery = v) } },
                    subtitle = if (gallerySupported) "Also keep every created image in Pictures/Lowkey. Otherwise it's only in " +
                        "the clipboard history, which forgets it like a copied image (${s.clipExpiry.label.lowercase()})."
                    else "Needs Android 10 or later",
                    enabled = gallerySupported,
                )
            }
        }
        if (s.aiKeyboard) item { AiSafetySettings(s) }
        if (s.aiKeyboard) item {
            SettingsGroup("What is sent") {
                GroupText(
                    "Only what is in the ✨ prompt box when you tap Create goes to the service above: the words and the " +
                        "pictures you added to it, nothing else you type. Improve sends just the words. " +
                        "With OpenAI, Lowkey also sends that text, and a finished image, to OpenAI's safety check with your key " +
                        "before anything is shown or copied. Your own server is not asked to look at the picture; an on-device list " +
                        "checks the words. A report stays on this phone until you email it yourself. " +
                        "The key and token are encrypted on this phone and never included in a backup.",
                )
                if (s.aiService == AiService.OWN_SERVER) GroupText(
                    "Your server speaks OpenAI's API: Create image sends POST <address>/v1/images/generations with the " +
                        "prompt, n, quality and (if set) model as JSON, and reads data[0].b64_json (or an HTTPS data[0].url). " +
                        "With pictures added, it sends them with the same fields as a multipart upload to " +
                        "/v1/images/edits. Improve sends POST /v1/chat/completions and reads choices[0].message.content.",
                )
            }
        }
        item { Spacer(Modifier.padding(12.dp)) }
    }
}

private fun serviceDetail(service: AiService): String = when (service) {
    AiService.OPENAI -> "Your OpenAI API key, straight from this phone"
    AiService.OWN_SERVER -> "A server you run that speaks OpenAI's API, such as an agent on your private network"
}

/** A key or token: shows whether one is saved (and its last four characters), and edits it in a dialog. */
@Composable
private fun SecretRow(
    title: String, secret: Secret, hint: String?, help: String, required: Boolean,
    /** Who refused the saved value ("OpenAI"), when the AI service turned it down; null otherwise. */
    refusedBy: String? = null,
    /** The field's own short label (the dialog's title already names it), and its placeholder. */
    field: String, placeholder: String? = null,
    /** A link above [help]: its words and where it leads (where to get the key). */
    link: Pair<String, String>? = null,
    /** An advisory for a value that looks unusual; Save still works, and the link stays. */
    looksWrong: (String) -> String? = { null },
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // "ends in …a1B2", kept on one line, so the last characters don't read as a word.
    val ends = hint?.let { "in\u00A0…\u2060" + it.removePrefix("…") }
    val state = when {
        ends == null -> if (required) "Not set" else "None"
        refusedBy != null -> "Refused · ends $ends"
        else -> "Saved · ends $ends"
    }
    ValueRow(title, state, warn = (hint == null && required) || refusedBy != null) { open = true }
    if (open) {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        // Remove takes a second tap: the dialog is usually opened to replace a key, not to lose it.
        var confirmRemove by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    if (refusedBy != null && ends != null) {
                        Text("$refusedBy refused the saved ${field.lowercase()} (ends\u00A0$ends).",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    val advisory = text.trim().takeIf { it.isNotEmpty() }?.let(looksWrong)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        label = { Text(if (hint == null) field else "New ${field.lowercase()}") },
                        placeholder = placeholder?.let { { Text(it) } },
                        singleLine = true,
                        // Red only for what stops Save; an unusual-looking key is just advised on.
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        // Keys are pasted, not typed. The clipboard is read only on this tap.
                        trailingIcon = {
                            TextButton(onClick = { clipboard.getText()?.text?.trim()?.let { text = it; error = null } }) { Text("Paste") }
                        },
                        supportingText = {
                            Column {
                                if (error != null) Text(error!!)
                                else if (advisory != null) Text(advisory, color = MaterialTheme.colorScheme.tertiary)
                                if (link != null) Text(modifier = Modifier.padding(top = 4.dp), text = androidx.compose.ui.text.buildAnnotatedString {
                                    withLink(androidx.compose.ui.text.LinkAnnotation.Url(link.second,
                                        androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(
                                            color = MaterialTheme.colorScheme.primary,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)))) { append(link.first) }
                                })
                                Text(help)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusOnOpen(),
                    )
                }
            },
            confirmButton = {
                // Nothing to save until something is pasted or typed.
                TextButton(enabled = text.isNotBlank(), onClick = {
                    when {
                        text.isBlank() -> error = "Paste the ${title.lowercase()} first"
                        text.trim().any { it.isWhitespace() } -> error = "A ${field.lowercase()} has no spaces"
                        !AppGraph.secrets.set(secret, text) -> error = "Couldn't store it securely on this phone"
                        else -> open = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hint != null) TextButton(onClick = {
                        if (confirmRemove) { AppGraph.secrets.set(secret, null); open = false } else confirmRemove = true
                    }) {
                        Text(if (confirmRemove) "Tap again to remove" else "Remove", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            },
        )
    }
}

/** The own server's address, checked as it is saved. */
@Composable
private fun ServerAddressRow(url: String, onSave: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ValueRow("Server address", url.ifBlank { "Not set" }, warn = url.isBlank()) { open = true }
    if (open) {
        var text by rememberSaveable { mutableStateOf(url) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Server address") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("Address") },
                    placeholder = { Text("https://ai.example.net") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                    ),
                    supportingText = {
                        Text(error ?: "HTTPS. The server, or its /v1 API address.")
                    },
                    modifier = Modifier.fillMaxWidth().focusOnOpen(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val problem = AiConnection.apiBase(AiService.OWN_SERVER, text, allowLocalHttp = BuildConfig.DEBUG).problem
                    if (problem != null && text.isNotBlank()) error = problem
                    else { onSave(text.trim()); open = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/** A short free-text setting edited in a dialog. */
@Composable
private fun TextSettingRow(title: String, value: String, initial: String, placeholder: String, help: String, onSave: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ValueRow(title, value, warn = false) { open = true }
    if (open) {
        var text by rememberSaveable { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(100) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    supportingText = { Text(help) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { onSave(text.trim()); open = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/** A row with a value under its title that opens an editor; [warn] marks something still missing. */
@Composable
internal fun ValueRow(title: String, value: String, warn: Boolean, onClick: () -> Unit) {
    Column(
        settingHighlight(title, onEdit = onClick)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Change", onClick = onClick)
            .semantics { stateDescription = value }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value, style = MaterialTheme.typography.bodyMedium,
            // Something to do (not set, refused) is advised in the same amber as the keyboard; red is for errors only.
            color = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        )
    }
}

/** The Experimental link's summary on the Settings page. */
internal fun experimentalSummary(s: Settings): String =
    if (s.aiKeyboard) "AI on the keyboard · ${s.aiService.label}" else "AI on the keyboard off"

/** The dialog's field takes focus as it opens, so the keyboard is up for pasting a key or address. */
@Composable
private fun Modifier.focusOnOpen(): Modifier {
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(requester) {
        androidx.compose.runtime.withFrameNanos {}
        runCatching { requester.requestFocus() }
    }
    return focusRequester(requester)
}
