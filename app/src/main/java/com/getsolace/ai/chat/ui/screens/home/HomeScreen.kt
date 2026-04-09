package com.getsolace.ai.chat.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.request.videoFrameMillis
import com.getsolace.ai.chat.R
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.data.MediaCategory
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.HomeCardItem
import com.getsolace.ai.chat.viewmodel.HomeViewModel
import com.google.accompanist.permissions.*

// ═══════════════════════════════════════════════════════════════════════════════
//  Design Tokens  — 与设计稿完全对齐
// ═══════════════════════════════════════════════════════════════════════════════

private val DeepBg          = Color(0xFF0A0B14)
private val CardSurface     = Color(0xFF111827)
private val CardBorder      = Color(0x0FFFFFFF)     // 6% 白

// Violet 系 (主紫)
private val VioletBright    = Color(0xFF9B7AFF)
private val VioletMid       = Color(0x558B68F7)
private val VioletFaint     = Color(0x1E8B68F7)
private val VioletBorder    = Color(0x558B68F7)

// Teal 系 (3D星系 / 短视频)
private val TealBright      = Color(0xFF00D4B4)
private val TealFaint       = Color(0x1A00D4B4)
private val TealBorder      = Color(0x3300C8AA)

// Blue 系 (AI 生图)
private val BlueBright      = Color(0xFF38B2F8)
private val BlueFaint       = Color(0x1A38B2F8)

// Accent per category
private val ColorAllVideo   = Color(0xFF5B8DEF)
private val ColorShort      = Color(0xFF2DBD9A)
private val ColorScreenRec  = Color(0xFFF08C4B)
private val ColorScreenshot = Color(0xFFE05C8A)

private val TextPri         = Color(0xFFFFFFFF)
private val TextSec         = Color(0x73FFFFFF)   // 45%
private val TextTer         = Color(0x40FFFFFF)   // 25%

// ═══════════════════════════════════════════════════════════════════════════════
//  HomeScreen
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    vm: HomeViewModel = viewModel()
) {
    val context        = LocalContext.current
    val isScanning     by vm.isScanning.collectAsStateWithLifecycle()
    val scanProgress   by vm.scanProgress.collectAsStateWithLifecycle()
    val scannedSize    by vm.scannedSize.collectAsStateWithLifecycle()
    val cardLeftItems  by vm.cardLeftItems.collectAsStateWithLifecycle()
    val cardRightItems by vm.cardRightItems.collectAsStateWithLifecycle()

    // Android 13+ 同时申请图片和视频权限（视频扫描需要 READ_MEDIA_VIDEO）
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rememberMultiplePermissionsState(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        )
    else
        rememberMultiplePermissionsState(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        )

    val permission = permissions   // 保持下方兼容使用
    var pendingGalaxyNav by remember { mutableStateOf(false) }

    LaunchedEffect(permissions.allPermissionsGranted) {
        if (permissions.allPermissionsGranted) {
            vm.startScan(context)
            if (pendingGalaxyNav) {
                pendingGalaxyNav = false
                navController.navigate("galaxy")
            }
        }
    }

    // 全屏深空背景 + 右上角紫色光晕 + 左中青绿光晕
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // 背景底色
                drawRect(color = DeepBg)
                // 右上紫色光晕
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(Color(0x2D7850FF), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.08f),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.08f),
                    radius = size.width * 0.55f
                )
                // 左中青绿光晕
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(Color(0x1A00C8AA), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.55f),
                        radius = size.width * 0.45f
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.55f),
                    radius = size.width * 0.45f
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            AuraHeader(
                onAIClick    = { navController.navigate("create") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }},
                onVaultClick = { navController.navigate("vault") {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }}
            )

            // 扫描卡片
            AuraScanCard(
                isScanning  = isScanning,
                progress    = scanProgress,
                scannedSize = scannedSize,
                onScanClick = {
                    if (permissions.allPermissionsGranted) vm.startScan(context)
                    else permissions.launchMultiplePermissionRequest()
                },
                modifier    = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(12.dp))

            // 功能双列卡片（保险箱 + 3D相册）
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AuraFeatureCard(
                    modifier    = Modifier.weight(1f),
                    icon        = { VaultIcon() },
                    title       = "私密保险箱",
                    subtitle    = "AES-256 加密\n仅本设备可读",
                    borderColor = VioletBorder,
                    bgBrush     = Brush.linearGradient(
                        listOf(Color(0x7050329A), Color(0x601A0E6D))
                    ),
                    onClick     = { navController.navigate("vault") {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }}
                )
                AuraFeatureCard(
                    modifier    = Modifier.weight(1f),
                    icon        = { GalaxyIcon() },
                    title       = "3D 星系相册",
                    subtitle    = "7 种形状\n沉浸式浏览",
                    borderColor = TealBorder,
                    bgBrush     = Brush.linearGradient(
                        listOf(Color(0x50007860), Color(0x60004D44))
                    ),
                    onClick     = {
                        if (permissions.allPermissionsGranted) navController.navigate("galaxy")
                        else { pendingGalaxyNav = true; permissions.launchMultiplePermissionRequest() }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // 媒体分类标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "媒体分类",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TextPri
                )
                Text(
                    "查看全部 ›",
                    fontSize = 12.sp,
                    color    = VioletBright.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // 媒体卡片网格
            if (!permissions.allPermissionsGranted) {
                AuraPermissionCard(
                    onRequestClick = { permissions.launchMultiplePermissionRequest() },
                    modifier       = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                AuraMediaGrid(
                    leftItems     = cardLeftItems,
                    rightItems    = cardRightItems,
                    navController = navController
                )
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Header
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AuraHeader(onAIClick: () -> Unit, onVaultClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPri,
                letterSpacing = (-0.5).sp
            )
            Text(
                "媒体管理 · AI 创作",
                fontSize = 13.sp,
                color    = TextSec,
                letterSpacing = 0.3.sp
            )
        }
        // AI 创作按钮
        HeaderIconBtn(
            bgColor     = VioletFaint,
            borderColor = VioletBorder,
            onClick     = onAIClick
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "AI创作",
                tint     = VioletBright,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        // 保险箱按钮
        HeaderIconBtn(
            bgColor     = Color(0x14FFFFFF),
            borderColor = Color(0x1FFFFFFF),
            onClick     = onVaultClick
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "保险箱",
                tint     = Color(0x99FFFFFF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun HeaderIconBtn(
    bgColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Scan Card  — 3D 倾斜 + 进度环
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AuraScanCard(
    isScanning: Boolean,
    progress: Float,
    scannedSize: Long,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    val animX by animateFloatAsState(tiltX, spring(), label = "tX")
    val animY by animateFloatAsState(tiltY, spring(), label = "tY")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .graphicsLayer {
                rotationX = -animY * 0.08f
                rotationY =  animX * 0.08f
                cameraDistance = 8 * density
            }
            .clip(RoundedCornerShape(20.dp))
            // 深紫色渐变底
            .background(
                Brush.linearGradient(
                    listOf(Color(0xCC1E193C), Color(0xCC14193B))
                )
            )
            .border(1.dp, Color(0x40784FFF), RoundedCornerShape(20.dp))
            // 右上光晕
            .drawBehind {
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(Color(0x1F784FFF), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.5f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.width * 0.5f
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd    = { tiltX = 0f; tiltY = 0f },
                    onDragCancel = { tiltX = 0f; tiltY = 0f },
                    onDrag       = { change, drag ->
                        change.consume()
                        tiltX = (tiltX + drag.x).coerceIn(-60f, 60f)
                        tiltY = (tiltY + drag.y).coerceIn(-60f, 60f)
                    }
                )
            }
            .clickable { onScanClick() }
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 进度环
            Box(
                modifier         = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                // 外环淡边
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0x337850FF), CircleShape)
                )
                CircularProgressIndicator(
                    progress      = { if (isScanning) progress else 1f },
                    modifier      = Modifier.size(64.dp),
                    color         = VioletBright,
                    trackColor    = Color(0x22FFFFFF),
                    strokeWidth   = 2.5.dp
                )
                if (isScanning) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPri
                    )
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = VioletBright,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isScanning) "正在扫描媒体库…" else "扫描完成",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TextPri
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (scannedSize > 0) "已扫描 ${formatBytes(scannedSize)} · 共 47 个文件"
                    else "点击开始扫描",
                    fontSize = 12.sp,
                    color    = TextSec
                )
                if (!isScanning && scannedSize > 0) {
                    Spacer(Modifier.height(10.dp))
                    // 重新扫描药丸
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(VioletFaint)
                            .border(1.dp, VioletBorder, RoundedCornerShape(20.dp))
                            .clickable { onScanClick() }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "重新扫描",
                            fontSize = 11.sp,
                            color    = VioletBright.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Feature Cards (保险箱 / 3D相册)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AuraFeatureCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    borderColor: Color,
    bgBrush: Brush,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(
                title,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = TextPri.copy(alpha = 0.92f)
            )
            Text(
                subtitle,
                fontSize   = 11.sp,
                color      = TextSec,
                lineHeight = 15.sp
            )
        }
        // 箭头
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint     = Color(0x40FFFFFF),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(16.dp)
        )
    }
}

// ─── 保险箱图标 ────────────────────────────────────────────────────────────────
@Composable
private fun VaultIcon() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VioletFaint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint     = VioletBright,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─── 3D 星系图标 ───────────────────────────────────────────────────────────────
@Composable
private fun GalaxyIcon() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TealFaint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint     = TealBright,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Media Category Grid
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AuraMediaGrid(
    leftItems: List<HomeCardItem>,
    rightItems: List<HomeCardItem>,
    navController: NavController
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            leftItems.forEach { item ->
                AuraMediaCard(
                    item    = item,
                    onClick = { navController.navigate("video_list/${item.category.name}") }
                )
            }
        }
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rightItems.forEach { item ->
                AuraMediaCard(
                    item    = item,
                    onClick = { navController.navigate("video_list/${item.category.name}") }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Media Card — 重新设计，与设计稿一致
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AuraMediaCard(
    item: HomeCardItem,
    onClick: () -> Unit
) {
    val accent = when (item.category) {
        MediaCategory.ALL_VIDEOS        -> ColorAllVideo
        MediaCategory.SHORT_VIDEOS      -> ColorShort
        MediaCategory.SCREEN_RECORDINGS -> ColorScreenRec
        MediaCategory.SCREENSHOTS       -> ColorScreenshot
    }
    val cardH: Dp = when (item.category) {
        MediaCategory.ALL_VIDEOS        -> 158.dp
        MediaCategory.SHORT_VIDEOS      -> 130.dp
        MediaCategory.SCREEN_RECORDINGS -> 148.dp
        MediaCategory.SCREENSHOTS       -> 118.dp
    }
    val isVideoCategory = item.category != MediaCategory.SCREENSHOTS

    // ── ExoPlayer（仅视频分类且有 URI 时创建）──────────────────────────────
    val context = LocalContext.current
    val player: ExoPlayer? = if (isVideoCategory && item.thumbnail != null) {
        remember(item.thumbnail) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(item.thumbnail))
                prepare()
                playWhenReady = true
                volume        = 0f                        // 静音
                repeatMode    = Player.REPEAT_MODE_ONE   // 循环
            }
        }
    } else null

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardH)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(CardSurface, Color(0xFF0D1117))))
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        // ── 背景层：视频自动播放 或 截图静态缩略图 或 纯色占位 ──────────────
        if (player != null) {
            // 视频分类：嵌入 PlayerView 自动播放
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update  = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            // 轻微暗色遮罩，保证文字可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        } else if (item.thumbnail != null) {
            // 截图分类：静态图片
            SolaceAsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(item.thumbnail)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        } else {
            // 无媒体：accent 色渐变占位
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.14f), Color.Transparent)
                        )
                    )
            )
        }

        // ── 底部渐变遮罩 ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // ── 左上角分类徽标 ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(10.dp)
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.22f))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            CategoryBadgeIcon(item.category, accent)
        }

        // ── 右上角"正在播放"指示点（仅视频分类）────────────────────────────
        if (isVideoCategory && player != null) {
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34C759))   // 绿点 = 正在播放
                    .align(Alignment.TopEnd)
            )
        }

        // ── 左下角文字 ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 10.dp, end = 10.dp)
        ) {
            Text(
                item.category.title,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = TextPri
            )
            Text(
                "${item.count} 项 · ${item.formattedSize()}",
                fontSize = 10.sp,
                color    = TextSec
            )
        }
    }
}

// ─── 分类徽标图标 ──────────────────────────────────────────────────────────────
@Composable
private fun CategoryBadgeIcon(category: MediaCategory, tint: Color) {
    val icon = when (category) {
        MediaCategory.ALL_VIDEOS        -> Icons.Default.VideoLibrary
        MediaCategory.SHORT_VIDEOS      -> Icons.Default.VideoLibrary // 用 Bolt 代表短视频
        MediaCategory.SCREEN_RECORDINGS -> Icons.Default.ScreenShare
        MediaCategory.SCREENSHOTS       -> Icons.Default.CropOriginal
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Permission Card
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AuraPermissionCard(onRequestClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(VioletFaint)
                    .border(1.dp, VioletBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = null,
                    tint     = VioletBright,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "需要相册权限",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                color      = TextPri
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "授权后可扫描视频、截图等媒体文件",
                fontSize = 12.sp,
                color    = TextSec
            )
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(VioletMid)
                    .border(1.dp, VioletBorder, RoundedCornerShape(12.dp))
                    .clickable { onRequestClick() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("授权访问", fontSize = 13.sp, color = TextPri, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Helpers
// ═══════════════════════════════════════════════════════════════════════════════

fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 0.1 -> "%.0f MB".format(mb)
        else      -> "${bytes / 1024} KB"
    }
}
