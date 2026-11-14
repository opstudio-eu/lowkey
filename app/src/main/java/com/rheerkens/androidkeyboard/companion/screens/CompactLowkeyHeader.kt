package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.R
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.ui.SlimIcons

/** The Home identity, shortened to leave room for settings and setup. */
@Composable
internal fun CompactLowkeyHeader(backLabel: String?, onBack: () -> Unit) {
    val dark = MaterialTheme.colorScheme.isDark
    val compact = LocalConfiguration.current.screenHeightDp < 480
    val fontScale = LocalDensity.current.fontScale
    val titleScale = fontScale.coerceAtMost(1.3f) / fontScale
    Box(Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.welcome_sky), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            colorFilter = if (dark) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                .30f, 0f, 0f, 0f, 0f, 0f, .39f, 0f, 0f, 0f,
                0f, 0f, .56f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
            ))) else null)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            if (dark) listOf(Color(0xCC06163A), Color(0x730A2250))
            else listOf(Color(0x1A052746), Color(0x8C052746)),
        )))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(
            start = if (backLabel == null) 24.dp else 8.dp, end = 24.dp, top = 8.dp, bottom = if (compact) 16.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (backLabel != null) IconButton(onClick = onBack) {
                Icon(SlimIcons.Back, backLabel, tint = Color.White)
            }
            Icon(painterResource(R.drawable.opstudio_logo_blue), null,
                Modifier.size(if (compact) 30.dp else 38.dp, if (compact) 24.dp else 30.dp), tint = Color.White)
            Column {
                Text("Lowkey", style = MaterialTheme.typography.titleLarge.copy(fontSize = ((if (compact) 22 else 26) * titleScale).sp, lineHeight = ((if (compact) 24 else 28) * titleScale).sp), color = Color.White)
                if (!compact) Text("by OpStudio", fontSize = (12 * titleScale).sp, color = Color.White.copy(alpha = .88f))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (compact) 16.dp else 24.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)))
    }
}
