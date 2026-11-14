package com.rheerkens.androidkeyboard.companion.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rheerkens.androidkeyboard.companion.theme.isDark
import com.rheerkens.androidkeyboard.ui.SlimIcons
import com.rheerkens.androidkeyboard.voice.ui.VoiceLevelMeter

/** Decorative example: shares the real meter, but never records or requests microphone access. */
@Composable
internal fun WelcomeVoicePreview(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.isDark
    val ink = if (dark) Color(0xFFE3ECF3) else Color(0xFF101E2B)
    val muted = if (dark) Color(0xFF93A8BA) else Color(0xFF5F7385)
    val transition = rememberInfiniteTransition(label = "Speech example")
    val level by transition.animateFloat(
        initialValue = .2f, targetValue = .9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "Voice level",
    )
    Column(modifier.clip(RoundedCornerShape(18.dp)).clearAndSetSemantics {
        contentDescription = "Example of voice typing: a slim recording bar below your text, with an audio level indicator, cancel and done."
    }) {
        Text(
            buildAnnotatedString {
                append("Running a few minutes late, save me a seat")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("│") }
            }, color = ink, fontSize = 16.sp, lineHeight = 23.sp,
            modifier = Modifier.fillMaxWidth()
                .background(if (dark) Color(0xEB172636) else Color(0xEDFFFFFF))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        )
        HorizontalDivider(color = if (dark) Color(0xFF20344A) else Color(0xFFDCE5ED))
        Row(
            Modifier.fillMaxWidth().height(52.dp)
                .background(if (dark) Color(0xFF142130) else Color(0xFFEEF3F7))
                .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(SlimIcons.Close, null, Modifier.size(22.dp), tint = ink)
            }
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VoiceLevelMeter(level, if (dark) Color(0xFF5CD193) else Color(0xFF1E8A55))
                Text("0:06", color = muted, fontSize = 15.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(SlimIcons.Check, null, Modifier.size(22.dp), tint = ink)
            }
        }
    }
}
