package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rheerkens.androidkeyboard.AppGraph
import com.rheerkens.androidkeyboard.ui.SlimIcons

/** A quiet shared editor; its draft stays with the companion navigation state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardTestSheet(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onClose: () -> Unit,
    onSetupVoice: () -> Unit,
    resizeKeyboard: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val compact = LocalConfiguration.current.screenHeightDp < 480
    val context = LocalContext.current
    val models by AppGraph.models.state.collectAsStateWithLifecycle()
    var hasMic by remember { mutableStateOf(AppGraph.hasMicPermission(context)) }
    var focused by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { hasMic = AppGraph.hasMicPermission(context); onPauseOrDispose { } }
    val voiceReady = hasMic && models.activeModel?.id?.let { it in models.installed } == true
    val colors = MaterialTheme.colorScheme
    val options = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
        platformImeOptions = if (resizeKeyboard) PlatformImeOptions(privateImeOptions = "lowkey.resize") else null)
    fun clear() {
        onDraftChange(TextFieldValue())
        focus.requestFocus()
        keyboard?.show()
    }
    ModalBottomSheet(onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetMaxWidth = 640.dp) {
        if (compact) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = draft, onValueChange = onDraftChange,
                    placeholder = { Text("Type something...") }, maxLines = 2, keyboardOptions = options,
                    trailingIcon = {
                        if (draft.text.isNotEmpty()) IconButton(onClick = ::clear) {
                            Icon(SlimIcons.Trash, "Clear test input")
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("quick_test_input"))
                IconButton(onClick = onClose) { Icon(SlimIcons.Close, "Close keyboard test") }
            }
        } else Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Try your keyboard", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp))
                IconButton(onClick = onClose) { Icon(SlimIcons.Close, "Close keyboard test") }
            }
            val shape = RoundedCornerShape(18.dp)
            Box(Modifier.fillMaxWidth().heightIn(min = 104.dp).background(colors.surfaceContainerLow, shape)
                .border(1.dp, if (focused) colors.primary else colors.outlineVariant, shape)) {
                BasicTextField(value = draft, onValueChange = onDraftChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, fontSize = 17.sp),
                    cursorBrush = SolidColor(colors.primary), keyboardOptions = options, minLines = 2, maxLines = 5,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
                        .semantics { contentDescription = "Test input" }.testTag("quick_test_input"),
                    decorationBox = { input ->
                        Box(Modifier.padding(start = 16.dp, end = 56.dp, top = 14.dp, bottom = 44.dp)) {
                            if (draft.text.isEmpty()) Text("Type something...", style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant)
                            input()
                        }
                    })
                if (draft.text.isNotEmpty()) IconButton(onClick = ::clear, modifier = Modifier.align(Alignment.BottomEnd)) {
                    Icon(SlimIcons.Trash, "Clear test input", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            if (!voiceReady) TextButton(onClick = onSetupVoice, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(SlimIcons.Mic, null, Modifier.size(20.dp))
                    Text("Set up voice for a cleaner experience", Modifier.weight(1f))
                    Icon(SlimIcons.ChevronRight, null, Modifier.size(18.dp))
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            focus.requestFocus()
            keyboard?.show()
        }
    }
}
