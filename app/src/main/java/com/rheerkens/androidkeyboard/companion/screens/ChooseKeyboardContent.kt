package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.ui.SlimIcons

/** The first setup action succeeded; the keyboard still needs to be selected in Android. */
@Composable
internal fun ChooseKeyboardConfirmation(compact: Boolean) {
    Column {
        Box(Modifier.size(60.dp).background(Color.White.copy(alpha = .96f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(SlimIcons.Check, null, Modifier.size(32.dp), tint = Color(0xFF1E8A55))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Lowkey is\nenabled.",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (compact) 34.sp else 40.sp,
                lineHeight = if (compact) 36.sp else 42.sp,
                letterSpacing = (-.6).sp,
            ),
            color = Color.White, modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        Text("Step 1 of 2 · Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .9f))
    }
}

/** Keep the pending action beside its button instead of repeating the welcome explanation. */
@Composable
internal fun ChooseKeyboardInstruction(compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(14.dp).border(2.dp, colors.primary, CircleShape))
        Text("STEP 2 OF 2 · TO DO", color = colors.primary, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
    }
    Spacer(Modifier.height(10.dp))
    Text("Choose Lowkey as your keyboard",
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = if (compact) 25.sp else 28.sp,
            lineHeight = if (compact) 28.sp else 31.sp,
            letterSpacing = (-.3).sp,
        ), color = colors.onSurface, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(10.dp))
    Text(buildAnnotatedString {
        append("Android will show your keyboards. Tap ")
        withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.SemiBold)) { append("Lowkey") }
        append(".")
    }, fontSize = 16.sp, lineHeight = 25.sp, color = colors.onSurfaceVariant)
    Spacer(Modifier.height(22.dp))
}
