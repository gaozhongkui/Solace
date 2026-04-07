package com.getsolace.ai.chat.ui.screens.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.AIViewModel

// ─── Processing Screen ────────────────────────────────────────────────────────

@Composable
fun AIProcessingScreen(vm: AIViewModel) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val rotation by infiniteTransition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "rotation"
    )

    Column(
        modifier            = Modifier.fillMaxSize().padding(AppSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(GlowPurple, GlowCyan, GlowPurple)))
                    .rotate(rotation)
                    .blur(16.dp)
            )
            Box(
                modifier         = Modifier.size(140.dp).clip(CircleShape).background(BgDeep),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoAwesome, null, tint = AccentPrimary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = TextPrimary, fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.xxxl))
        Text(
            "AI 正在创作中…",
            style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            "基于你的提示词生成独特作品",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
        Spacer(Modifier.height(AppSpacing.xxxl))
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color      = AccentPrimary,
            trackColor = DividerColor
        )
    }
}
