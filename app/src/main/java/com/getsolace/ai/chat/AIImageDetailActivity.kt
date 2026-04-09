package com.getsolace.ai.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getsolace.ai.chat.data.AIGeneratedImage
import com.getsolace.ai.chat.data.AIImageStore
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.ui.theme.SolaceTheme
import java.text.SimpleDateFormat
import java.util.*

// ─── Design tokens ────────────────────────────────────────────────────────────

private val DeepBg       = Color(0xFF0A0B14)
private val CardSurface  = Color(0xFF131722)
private val CardBorder   = Color(0x0FFFFFFF)
private val VioletBright = Color(0xFF9B7AFF)
private val TextPri      = Color(0xFFFFFFFF)
private val TextSec      = Color(0x73FFFFFF)

// ─── Activity ─────────────────────────────────────────────────────────────────

class AIImageDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_IMAGE_ID = "extra_image_id"

        fun newIntent(context: Context, imageId: String): Intent =
            Intent(context, AIImageDetailActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_ID, imageId)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialId = intent.getStringExtra(EXTRA_IMAGE_ID) ?: ""

        setContent {
            SolaceTheme {
                AIImageDetailScreen(
                    initialImageId = initialId,
                    onBack         = { finish() }
                )
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
private fun AIImageDetailScreen(
    initialImageId: String,
    onBack: () -> Unit
) {
    val images by AIImageStore.images.collectAsStateWithLifecycle()

    // Close if all images deleted
    LaunchedEffect(images) {
        if (images.isEmpty()) onBack()
    }

    if (images.isEmpty()) return

    val initialIndex = remember(initialImageId) {
        images.indexOfFirst { it.id == initialImageId }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFullscreen    by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showFullscreen    -> showFullscreen = false
            showDeleteConfirm -> showDeleteConfirm = false
            else              -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPri
                    )
                }

                // Page indicator + title
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "作品详情",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPri
                    )
                    AnimatedVisibility(
                        visible = images.size > 1,
                        enter   = fadeIn(),
                        exit    = fadeOut()
                    ) {
                        Text(
                            "${pagerState.currentPage + 1} / ${images.size}",
                            fontSize = 12.sp,
                            color    = TextSec
                        )
                    }
                }

                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFFF3B30)
                    )
                }
            }

            // ── Dot indicators ────────────────────────────────────────────────
            if (images.size > 1) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    images.indices.forEach { idx ->
                        val isSelected = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isSelected) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) VioletBright
                                    else Color(0x40FFFFFF)
                                )
                        )
                    }
                }
            }

            // ── Pager ─────────────────────────────────────────────────────────
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val img = images.getOrNull(page)
                if (img != null) {
                    ImageDetailPage(
                        image       = img,
                        onImageClick = { showFullscreen = true }
                    )
                }
            }
        }

        // ── Fullscreen image viewer ───────────────────────────────────────────
        if (showFullscreen) {
            val currentImage = images.getOrNull(pagerState.currentPage)
            if (currentImage != null) {
                FullscreenImageViewer(
                    imageUrl  = currentImage.imageUrl,
                    onDismiss = { showFullscreen = false }
                )
            }
        }

        // ── Delete confirm dialog ──────────────────────────────────────────────
        if (showDeleteConfirm) {
            val currentImage = images.getOrNull(pagerState.currentPage)
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title            = { Text("删除作品", color = TextPri) },
                text             = { Text("确定要删除这件作品吗？此操作无法撤销。", color = TextSec) },
                confirmButton    = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        currentImage?.let { AIImageStore.deleteImage(it.id) }
                    }) {
                        Text("删除", color = Color(0xFFFF3B30))
                    }
                },
                dismissButton    = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消", color = TextSec)
                    }
                },
                containerColor = Color(0xFF1C2135),
                shape          = RoundedCornerShape(20.dp)
            )
        }
    }
}

// ─── Single page content ──────────────────────────────────────────────────────

@Composable
private fun ImageDetailPage(image: AIGeneratedImage, onImageClick: () -> Unit = {}) {
    val dateStr = remember(image.createdAt) {
        SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA).format(Date(image.createdAt))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .clickable(onClick = onImageClick)
        ) {
            SolaceAsyncImage(
                model              = image.imageUrl.ifBlank { null },
                contentDescription = image.prompt,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
                shape              = RoundedCornerShape(20.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Info card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                DetailInfoRow(label = "风格",   value = image.styleTitle)
                Spacer(Modifier.height(10.dp))
                DetailInfoRow(label = "比例",   value = image.aspectRatio)
                Spacer(Modifier.height(10.dp))
                DetailInfoRow(label = "创作时间", value = dateStr)

                if (image.prompt.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x14FFFFFF))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "提示词",
                        fontSize   = 12.sp,
                        color      = VioletBright,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        image.prompt,
                        fontSize = 13.sp,
                        color    = TextPri,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Fullscreen image viewer ──────────────────────────────────────────────────

@Composable
private fun FullscreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomed = scale > 1.05f

    val activity = LocalContext.current as Activity

    // 进入时隐藏系统栏
    LaunchedEffect(Unit) {
        val ctrl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    // 离开时恢复系统栏
    DisposableEffect(Unit) {
        onDispose {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale  = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Box(
        modifier          = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // 未缩放时单击关闭；已缩放时单击复位
                        if (isZoomed) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            onDismiss()
                        }
                    },
                    onDoubleTap = {
                        if (isZoomed) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            },
        contentAlignment  = Alignment.Center
    ) {
        SolaceAsyncImage(
            model              = imageUrl,
            contentDescription = null,
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
}

// ─── Detail info row ──────────────────────────────────────────────────────────

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color    = TextSec,
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            fontSize = 13.sp,
            color    = TextPri,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}
