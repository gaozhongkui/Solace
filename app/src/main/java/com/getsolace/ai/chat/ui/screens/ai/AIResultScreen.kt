package com.getsolace.ai.chat.ui.screens.ai

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.AIViewModel

// ─── Result Screen ────────────────────────────────────────────────────────────

@Composable
fun AIResultScreen(imageUrl: String, vm: AIViewModel) {
    val prompt      by vm.prompt.collectAsStateWithLifecycle()
    val style       by vm.selectedStyle.collectAsStateWithLifecycle()
    var showSuccess by remember { mutableStateOf(false) }

    if (showSuccess) {
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(2000); showSuccess = false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.lg)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.resetToConfig() }) {
                    Icon(Icons.Default.Close, "关闭", tint = TextSecondary)
                }
                Text(
                    "生成结果",
                    style     = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold),
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(AppSpacing.xl))

            // 生成图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(CardBg)
                    .border(2.dp, Brush.linearGradient(listOf(GlowPurple, GlowCyan)), RoundedCornerShape(AppRadius.lg))
            ) {
                SolaceAsyncImage(
                    model              = imageUrl,
                    contentDescription = prompt,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                    shape              = RoundedCornerShape(AppRadius.lg)
                )
            }
            Spacer(Modifier.height(AppSpacing.lg))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(AppRadius.md),
                colors   = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Text("提示词", style = MaterialTheme.typography.labelMedium.copy(color = AccentPrimary))
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(prompt, maxLines = 6,
                        overflow = TextOverflow.Ellipsis,style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text("风格: ${style.title}", style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary))
                }
            }
            Spacer(Modifier.height(AppSpacing.xl))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                OutlinedButton(
                    onClick = { vm.resetToConfig() },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(AppRadius.md),
                    border   = BorderStroke(1.dp, AccentPrimary)
                ) {
                    Text("重新创作", color = AccentPrimary)
                }
                Button(
                    onClick  = { showSuccess = true },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(AppRadius.md),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("保存相册")
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
        }

        if (showSuccess) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)) {
                RunMateToast(message = "已保存到相册", isSuccess = true)
            }
        }
    }
}
