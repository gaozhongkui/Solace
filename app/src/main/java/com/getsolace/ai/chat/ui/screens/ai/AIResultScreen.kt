package com.getsolace.ai.chat.ui.screens.ai

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
fun AIResultScreen(imageUrl: String, vm: AIViewModel, onFinish: () -> Unit = {}) {
    val prompt        by vm.prompt.collectAsStateWithLifecycle()
    val style         by vm.selectedStyle.collectAsStateWithLifecycle()
    var showSuccess   by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }

    if (showSuccess) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            onFinish()
        }
    }

    // 底部按钮区高度：按钮本身 + 16dp 上边距 + 16dp 下边距 + 导航栏
    val bottomBarHeight = 88.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 滚动内容区（底部留出按钮空间）────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.lg)
                .padding(bottom = bottomBarHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            // 生成图片（点击进入全屏）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(CardBg)
                    .border(2.dp, Brush.linearGradient(listOf(GlowPurple, GlowCyan)), RoundedCornerShape(AppRadius.lg))
                    .clickable { showFullscreen = true }
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
                    Text(
                        prompt,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        style    = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text("风格: ${style.title}", style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary))
                }
            }
        }

        // ── 固定底部按钮区 ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.lg)
                .padding(bottom = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            OutlinedButton(
                onClick  = { vm.resetToConfig() },
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

        if (showSuccess) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)) {
                RunMateToast(message = "已保存到相册", isSuccess = true)
            }
        }

        // 全屏查看器
        if (showFullscreen) {
            FullscreenImageViewer(
                imageUrl    = imageUrl,
                description = prompt,
                onDismiss   = { showFullscreen = false }
            )
        }
    }
}

// ─── Fullscreen Image Viewer ──────────────────────────────────────────────────

@Composable
private fun FullscreenImageViewer(
    imageUrl    : String,
    description : String,
    onDismiss   : () -> Unit
) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale  = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            // 单击关闭（未缩放时），双击放大/还原
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (scale <= 1.05f) onDismiss() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
    ) {
        SolaceAsyncImage(
            model              = imageUrl,
            contentDescription = description,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxSize()
                .transformable(state = transformState)
                .graphicsLayer {
                    scaleX       = scale
                    scaleY       = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        // 关闭按钮
        AnimatedVisibility(
            visible = scale <= 1.05f,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint     = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(50)
                        )
                        .padding(4.dp)
                )
            }
        }
    }
}
