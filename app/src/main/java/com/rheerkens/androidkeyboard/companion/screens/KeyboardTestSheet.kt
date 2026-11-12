package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.launch
import com.rheerkens.androidkeyboard.ui.SlimIcons

/** A shared editor over the current page. The page stays composed behind the modal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardTestSheet(
    returnTo: String,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onClose: () -> Unit,
    resizeKeyboard: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val compact = LocalConfiguration.current.screenHeightDp < 480
    val options = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
        platformImeOptions = if (resizeKeyboard) PlatformImeOptions(privateImeOptions = "lowkey.resize") else null)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 640.dp,
    ) {
        if (compact) {
            // Landscape leaves very little height above the keys. Keep the editor and its
            // close button together instead of letting the header push the editor offscreen.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft, onValueChange = onDraftChange,
                    label = { Text("Test input") }, maxLines = 2,
                    keyboardOptions = options,
                    trailingIcon = {
                        IconButton(onClick = { onDraftChange(TextFieldValue()) }) {
                            Icon(SlimIcons.Trash, "Clear test input")
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("quick_test_input"),
                )
                IconButton(onClick = onClose) { Icon(SlimIcons.Close, "Close keyboard test") }
            }
        } else Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keyboard lab", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Type or dictate without leaving $returnTo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(SlimIcons.Close, "Close keyboard test") }
            }
            ModelMemoryCard(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = draft, onValueChange = onDraftChange,
                label = { Text("Test input") },
                placeholder = { Text("Try a word, a number, or a coding request") },
                minLines = 2, maxLines = 4,
                keyboardOptions = options,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("quick_test_input"),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDraftChange(TextFieldValue()) }, enabled = draft.text.isNotEmpty()) { Text("Clear") }
                TextButton(
                    onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Keyboard test", draft.text))) } },
                    enabled = draft.text.isNotEmpty(),
                ) { Text("Copy") }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Back to $returnTo") }
            }
            Text(
                "Your draft stays while you explore the app. Nothing is sent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            focus.requestFocus()
            keyboard?.show()
        }
    }
}
