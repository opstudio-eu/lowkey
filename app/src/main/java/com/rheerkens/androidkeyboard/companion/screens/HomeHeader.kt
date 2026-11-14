package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.R
import com.rheerkens.androidkeyboard.companion.KeyboardSetupState
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.ui.SlimIcons

@Composable
internal fun HomeHeader(setup: KeyboardSetupState) {
    val dark = MaterialTheme.colorScheme.isDark
    Box(Modifier.fillMaxWidth().heightIn(min = 156.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.welcome_sky), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
            colorFilter = if (dark) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                .30f, 0f, 0f, 0f, 0f, 0f, .39f, 0f, 0f, 0f,
                0f, 0f, .56f, 0f, 0f, 0f, 0f, 0f, 1f, 0f,
            ))) else null)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            if (dark) listOf(Color(0xCC06163A), Color(0x730A2250))
            else listOf(Color(0x1A052746), Color(0x8C052746)),
        )))
        FlowRow(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.opstudio_logo_blue), null, Modifier.size(44.dp, 35.dp), tint = Color.White)
                Column {
                    Text("Lowkey", style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, lineHeight = 32.sp), color = Color.White)
                    Text("by OpStudio", fontSize = 12.sp, color = Color.White.copy(alpha = .88f))
                }
            }
            Surface(modifier = Modifier.align(Alignment.CenterVertically), shape = CircleShape,
                color = if (dark) Color(0xEB101E2B) else Color.White.copy(alpha = .95f)) {
                Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (setup == KeyboardSetupState.READY) SlimIcons.Check else SlimIcons.Info, null,
                        Modifier.size(22.dp), tint = if (setup == KeyboardSetupState.READY) {
                            if (dark) Color(0xFF5CD193) else Color(0xFF1E8A55)
                        } else MaterialTheme.colorScheme.primary)
                    Text(when (setup) {
                        KeyboardSetupState.READY -> "Keyboard on"
                        KeyboardSetupState.NOT_ENABLED -> "Not enabled"
                        KeyboardSetupState.NOT_SELECTED -> "Choose Lowkey"
                    }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun HomeShortcut(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).background(colors.surfaceContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 14.sp, color = colors.onSurfaceVariant)
        }
        Icon(SlimIcons.ChevronRight, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
    }
}
