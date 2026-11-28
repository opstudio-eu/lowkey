package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rheerkens.androidkeyboard.privacy.Disclosure

/**
 * A prominent disclosure: shown before a permission request or the first AI request. Tapping
 * outside or Back is the same as [onDecline]; only [onAccept] goes on.
 */
@Composable
fun DisclosureDialog(disclosure: Disclosure, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(disclosure.title) },
        text = {
            Text(
                disclosure.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { Button(onClick = onAccept) { Text(disclosure.accept) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(disclosure.decline) } },
    )
}
