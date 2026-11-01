package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.text.InputType
import android.widget.EditText
import com.rheerkens.androidkeyboard.companion.SystemImeStatus
import com.rheerkens.androidkeyboard.ui.SlimIcons
import kotlinx.coroutines.delay

private val samplePrompts = listOf(
    "Explain this Kotlin coroutine",
    "Refactor the ViewModel so the repository is injected",
    "Why does adb install fail?",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TryItScreen(modifier: Modifier) {
    val context = LocalContext.current
    var message by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var send by rememberSaveable { mutableStateOf("") }
    var lastAction by rememberSaveable { mutableStateOf<String?>(null) }
    val sent = remember { mutableStateListOf<String>() }
    var currentKeyboard by remember { mutableStateOf(SystemImeStatus.read(context)) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        currentKeyboard = SystemImeStatus.read(context)
        delay(600)
        currentKeyboard = SystemImeStatus.read(context)
    }

    Column(
        modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (currentKeyboard.selected) "Current keyboard: Lowkey Keyboard"
                else "Current keyboard: ${currentKeyboard.currentLabel ?: "another keyboard"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { SystemImeStatus.showPicker(context) }) { Text("Switch") }
        }
        Text("Sample prompts", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            samplePrompts.forEach { p ->
                AssistChip(onClick = { message = p }, label = { Text(p) })
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message (multiline)") },
            placeholder = { Text("Type or dictate a coding request") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("tryit_message"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
        )
        // Most apps use Android's EditText, which reports word positions to the keyboard
        // (slide from Backspace into the text to delete back to a word). Compose text fields don't yet.
        Text("Android text field (EditText)", style = MaterialTheme.typography.labelLarge)
        val ink = MaterialTheme.colorScheme.onSurface.toArgb()
        val hint = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        AndroidView(
            factory = { ctx ->
                EditText(ctx).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    minLines = 3
                    textSize = 18f
                    this.hint = "Type here, then slide from Backspace into the text"
                    tag = "tryit_edittext"
                    contentDescription = "Android text field"
                }
            },
            update = { it.setTextColor(ink); it.setHintTextColor(hint) },
            modifier = Modifier.fillMaxWidth().testTag("tryit_edittext"),
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search") },
            singleLine = true,
            leadingIcon = { Icon(SlimIcons.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { lastAction = "Search: “$search”" }),
            modifier = Modifier.fillMaxWidth().testTag("tryit_search"),
        )
        OutlinedTextField(
            value = send,
            onValueChange = { send = it },
            label = { Text("Send a message") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (send.isNotBlank()) {
                    sent += send
                    lastAction = "Sent: “$send”"
                    send = ""
                }
            }),
            trailingIcon = {
                IconButton(onClick = {
                    if (send.isNotBlank()) {
                        sent += send
                        lastAction = "Sent: “$send”"
                        send = ""
                    }
                }) { Icon(SlimIcons.Send, "Send") }
            },
            modifier = Modifier.fillMaxWidth().testTag("tryit_send"),
        )
        lastAction?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        if (sent.isNotEmpty()) {
            Text("Sent messages", style = MaterialTheme.typography.labelLarge)
            sent.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        OutlinedButton(onClick = {
            message = ""
            search = ""
            send = ""
            lastAction = null
            sent.clear()
        }) { Text("Clear") }
    }
}
