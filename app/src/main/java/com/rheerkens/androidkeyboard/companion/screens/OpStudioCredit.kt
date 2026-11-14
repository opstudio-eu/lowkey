package com.rheerkens.androidkeyboard.companion.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "Visit opstudio.eu") { uri.openUri(OPSTUDIO_URL) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(opStudioLogo(dark)),
            contentDescription = "OpStudio logo",
            modifier = Modifier.size(width = 40.dp, height = 32.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Made by OpStudio", style = MaterialTheme.typography.titleSmall)
            Text(
                "Lowkey Keyboard $version · opstudio.eu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
