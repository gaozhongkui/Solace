package com.getsolace.ai.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.ui.theme.*

class FeedItemDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_IMAGE_URL = "extra_image_url"
        private const val EXTRA_PROMPT    = "extra_prompt"
        private const val EXTRA_PROMPT_CN = "extra_prompt_cn"
        private const val EXTRA_WIDTH     = "extra_width"
        private const val EXTRA_HEIGHT    = "extra_height"

        fun newIntent(
            context   : Context,
            imageUrl  : String,
            prompt    : String,
            promptCn  : String = "",
            width     : Int = 512,
            height    : Int = 512
        ): Intent = Intent(context, FeedItemDetailActivity::class.java).apply {
            putExtra(EXTRA_IMAGE_URL, imageUrl)
            putExtra(EXTRA_PROMPT,    prompt)
            putExtra(EXTRA_PROMPT_CN, promptCn)
            putExtra(EXTRA_WIDTH,     width)
            putExtra(EXTRA_HEIGHT,    height)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val imageUrl  = intent.getStringExtra(EXTRA_IMAGE_URL) ?: ""
        val prompt    = intent.getStringExtra(EXTRA_PROMPT)    ?: ""
        val promptCn  = intent.getStringExtra(EXTRA_PROMPT_CN) ?: ""
        val width     = intent.getIntExtra(EXTRA_WIDTH,  512)
        val height    = intent.getIntExtra(EXTRA_HEIGHT, 512)
        // 展示中文译文，无译文时退回英文原文
        val displayPrompt = promptCn.ifBlank { prompt }

        setContent {
            SolaceTheme {
                FeedItemDetailScreen(
                    imageUrl      = imageUrl,
                    prompt        = displayPrompt,
                    promptForEdit = promptCn.ifBlank { prompt },
                    width         = width,
                    height        = height,
                    onBack        = { finish() },
                    onUsePrompt   = { p ->
                        startActivity(CreateAIActivity.newIntent(this, p))
                        finish()
                    }
                )
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
private fun FeedItemDetailScreen(
    imageUrl      : String,
    prompt        : String,   // 用于展示（中文译文或原文）
    promptForEdit : String,   // 传给 CreateAI 的提示词（中文，ViewModel 翻英文）
    width         : Int,
    height        : Int,
    onBack        : () -> Unit,
    onUsePrompt   : (String) -> Unit
) {
    var scale        by remember { mutableFloatStateOf(1f) }
    var offset       by remember { mutableStateOf(Offset.Zero) }
    var isFullscreen by remember { mutableStateOf(false) }
    val isZoomed = scale > 1.05f
    val showChrome = !isFullscreen && !isZoomed

    // 同步系统状态栏 / 导航栏的显示隐藏
    val activity = LocalContext.current as Activity
    LaunchedEffect(isFullscreen) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 离开页面时恢复系统栏
    DisposableEffect(Unit) {
        onDispose {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale  = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    // 返回键：全屏 → 退全屏；缩放 → 复位；否则关闭
    BackHandler {
        when {
            isFullscreen -> isFullscreen = false
            isZoomed     -> { scale = 1f; offset = Offset.Zero }
            else         -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── 顶部栏（全屏 / 缩放时隐藏）──────────────────────────────────
            AnimatedVisibility(
                visible = showChrome,
                enter   = slideInVertically(tween(250)) { -it } + fadeIn(tween(250)),
                exit    = slideOutVertically(tween(250)) { -it } + fadeOut(tween(250))
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "关闭", tint = TextPrimary)
                    }
                    Text(
                        "图片详情",
                        style     = MaterialTheme.typography.titleMedium.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier  = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.width(48.dp))
                }
            }

            // ── 可缩放图片区域 ────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(0.dp))
                    .transformable(state = transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // 单击：切换全屏（缩放状态下单击不触发，避免误操作）
                                if (!isZoomed) isFullscreen = !isFullscreen
                            },
                            onDoubleTap = {
                                // 双击：缩放切换（同时退出全屏模式）
                                if (isZoomed) {
                                    scale = 1f; offset = Offset.Zero
                                } else {
                                    isFullscreen = false
                                    scale = 2.5f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                SolaceAsyncImage(
                    model              = imageUrl,
                    contentDescription = prompt,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX       = scale
                            scaleY       = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            // ── 底部提示词卡（全屏 / 缩放时隐藏）────────────────────────────
            AnimatedVisibility(
                visible = showChrome,
                enter   = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
                exit    = slideOutVertically(tween(250)) { it } + fadeOut(tween(250))
            ) {
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                        shape    = RoundedCornerShape(AppRadius.md),
                        colors   = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(modifier = Modifier.padding(AppSpacing.lg)) {
                            Text(
                                "提示词",
                                style = MaterialTheme.typography.labelMedium.copy(color = AccentPrimary)
                            )
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text(
                                prompt,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                            )
                            Spacer(Modifier.height(AppSpacing.md))
                            Button(
                                onClick  = { onUsePrompt(promptForEdit) },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(AppRadius.sm),
                                colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                            ) {
                                Icon(Icons.Default.AutoAwesome, null)
                                Spacer(Modifier.width(AppSpacing.sm))
                                Text("用此提示词创作")
                            }
                        }
                    }
                    Spacer(
                        Modifier
                            .navigationBarsPadding()
                            .height(AppSpacing.md)
                    )
                }
            }
        }
    }
}
