package com.rheerkens.androidkeyboard.companion.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.companion.theme.opStudioLogo

/** OpStudio's public site, linked from the credit. */
const val OPSTUDIO_URL = "https://opstudio.eu"

/** The installed app's version name, or "unknown". */
fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (_: PackageManager.NameNotFoundException) {
    null
} ?: "unknown"

/**
 * "Made by OpStudio" credit: the OpStudio logo in the variant for the current theme (blue on light,
 * light blue on dark), the app version and a link to OpStudio's site.
 */
@Composable
fun OpStudioCredit(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember { appVersion(context) }
    val dark = MaterialTheme.colorScheme.isDark
    SlimCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(opStudioLogo(dark)),
                contentDescription = "OpStudio logo",
                modifier = Modifier.size(width = 50.dp, height = 40.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Made by OpStudio", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lowkey Keyboard $version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { uri.openUri(OPSTUDIO_URL) },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        ) {
            Text("Visit opstudio.eu")
        }
        Text(
            "The OpStudio name and logo are trademarks of OpStudio. They are not covered by the Apache License.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
