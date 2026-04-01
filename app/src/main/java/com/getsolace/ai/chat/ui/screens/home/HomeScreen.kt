package com.getsolace.ai.chat.ui.screens.home

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.getsolace.ai.chat.data.MediaCategory
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.HomeCardItem
import com.getsolace.ai.chat.viewmodel.HomeViewModel
import com.google.accompanist.permissions.*

// ─── Home Screen (mirrors iOS HomeView.swift) ─────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    vm: HomeViewModel = viewModel()
) {
    val context          = LocalContext.current
    val isScanning       by vm.isScanning.collectAsStateWithLifecycle()
    val scanProgress     by vm.scanProgress.collectAsStateWithLifecycle()
    val scannedSize      by vm.scannedSize.collectAsStateWithLifecycle()
    val cardLeftItems    by vm.cardLeftItems.collectAsStateWithLifecycle()
    val cardRightItems   by vm.cardRightItems.collectAsStateWithLifecycle()

    // Permission handling
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    LaunchedEffect(permission.status) {
        if (permission.status.isGranted) {
            vm.startScan(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BgGradientStart, BgGradientEnd)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ────────────────────────────────────────────────────────
            HomeHeader(
                onAIClick   = { navController.navigate("create") },
                onVaultClick = { navController.navigate("vault") }
            )

            // ── Advanced Scanning Card ────────────────────────────────────────
            AdvancedScanningCard(
                isScanning   = isScanning,
                progress     = scanProgress,
                scannedSize  = scannedSize,
                onScanClick  = {
                    if (permission.status.isGranted) vm.startScan(context)
                    else permission.launchPermissionRequest()
                },
                modifier     = Modifier.padding(horizontal = AppSpacing.lg)
            )

            Spacer(Modifier.height(AppSpacing.lg))

            // ── PriSpace Banner ───────────────────────────────────────────────
            PriSpaceBanner(
                onClick  = { navController.navigate("vault") },
                modifier = Modifier.padding(horizontal = AppSpacing.lg)
            )

            Spacer(Modifier.height(AppSpacing.lg))

            // ── Galaxy Album Banner ───────────────────────────────────────────
            GalaxyAlbumBanner(
                onClick  = { navController.navigate("galaxy") },
                modifier = Modifier.padding(horizontal = AppSpacing.lg)
            )

            Spacer(Modifier.height(AppSpacing.lg))

            // ── Media Cards (staggered 2-column, mirrors iOS cardLeft/Right) ──
            if (!permission.status.isGranted) {
                PermissionRequestCard(
                    onRequestClick = { permission.launchPermissionRequest() },
                    modifier       = Modifier.padding(horizontal = AppSpacing.lg)
                )
            } else {
                MediaCategoryGrid(
                    leftItems    = cardLeftItems,
                    rightItems   = cardRightItems,
                    navController = navController
                )
            }

            Spacer(Modifier.height(AppSpacing.xxxl))
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
fun HomeHeader(onAIClick: () -> Unit, onVaultClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "AuraAI",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color      = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize   = AppFontSize.largeTitle
                )
            )
            Text(
                "媒体管理 · AI创作",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )
        }
        // AI button
        IconButton(
            onClick  = onAIClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AccentPrimary.copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.AutoAwesome, "AI创作", tint = AccentPrimary)
        }
        Spacer(Modifier.width(AppSpacing.sm))
        // Vault button
        IconButton(
            onClick  = onVaultClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlowPurple.copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.Lock, "保险箱", tint = GlowPurple)
        }
    }
}

// ─── Advanced Scanning Card (mirrors iOS AdvancedScanningCard.swift) ──────────

@Composable
fun AdvancedScanningCard(
    isScanning: Boolean,
    progress: Float,
    scannedSize: Long,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 3D tilt on drag (mirrors iOS 3D rotation gesture)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val animX by animateFloatAsState(offsetX, spring(), label = "tiltX")
    val animY by animateFloatAsState(offsetY, spring(), label = "tiltY")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .graphicsLayer {
                rotationX = -animY * 0.1f
                rotationY =  animX * 0.1f
                cameraDistance = 8 * density
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd    = { offsetX = 0f; offsetY = 0f },
                    onDragCancel = { offsetX = 0f; offsetY = 0f },
                    onDrag       = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(-60f, 60f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(-60f, 60f)
                    }
                )
            }
            .clickable { onScanClick() },
        shape  = RoundedCornerShape(AppRadius.lg),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(AccentStart.copy(alpha = 0.15f), AccentEnd.copy(alpha = 0.05f)))
                    )
            )

            Row(
                modifier          = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress ring
                Box(
                    modifier         = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress      = { if (isScanning) progress else 1f },
                        modifier      = Modifier.size(80.dp),
                        color         = AccentPrimary,
                        trackColor    = DividerColor,
                        strokeWidth   = 4.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isScanning) {
                            Text(
                                "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color      = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint     = AccentPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(AppSpacing.xl))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isScanning) "正在扫描媒体库…" else "扫描完成",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        if (scannedSize > 0) "已扫描 ${formatBytes(scannedSize)}" else "点击开始扫描",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    if (!isScanning && scannedSize > 0) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        Surface(
                            shape = RoundedCornerShape(AppRadius.xxl),
                            color = AccentPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "重新扫描",
                                modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = 4.dp),
                                style    = MaterialTheme.typography.labelSmall.copy(color = AccentPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── PriSpace Banner (mirrors iOS PriSpaceBanner.swift) ──────────────────────

@Composable
fun PriSpaceBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "glow"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable { onClick() },
        shape  = RoundedCornerShape(AppRadius.lg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF2D1B69), Color(0xFF1A0E3D)))
                )
        ) {
            // Glow circle (breathing effect)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(80.dp)
                    .offset(x = 20.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = glowAlpha * 0.3f))
            )

            Row(
                modifier          = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lock icon in circle
                Box(
                    modifier         = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = AccentPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(AppSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "私密保险箱",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "AES-256 银行级加密 · 仅本设备可读",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextTertiary)
            }
        }
    }
}

// ─── Galaxy Album Banner ──────────────────────────────────────────────────────

@Composable
fun GalaxyAlbumBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() },
        shape  = RoundedCornerShape(AppRadius.lg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B2838)))
                )
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlowCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, null, tint = GlowCyan, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(AppSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "3D 星系相册",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "7 种形状 · 沉浸式 3D 浏览",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextTertiary)
            }
        }
    }
}

// ─── Media Category Grid (staggered, mirrors iOS 2-column card grid) ──────────

@Composable
fun MediaCategoryGrid(
    leftItems: List<HomeCardItem>,
    rightItems: List<HomeCardItem>,
    navController: NavController
) {
    Text(
        "媒体分类",
        style    = MaterialTheme.typography.titleMedium.copy(
            color      = TextPrimary,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(horizontal = AppSpacing.lg)
    )
    Spacer(Modifier.height(AppSpacing.md))

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        // Left column
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            leftItems.forEach { item ->
                HomeItemCard(
                    item    = item,
                    onClick = { navController.navigate("video_list/${item.category.name}") }
                )
            }
        }
        // Right column
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            rightItems.forEach { item ->
                HomeItemCard(
                    item    = item,
                    onClick = { navController.navigate("video_list/${item.category.name}") }
                )
            }
        }
    }
}

// ─── Home Item Card (mirrors iOS HomeItemCard.swift) ──────────────────────────

@Composable
fun HomeItemCard(item: HomeCardItem, onClick: () -> Unit) {
    val cardHeight = when (item.category) {
        MediaCategory.ALL_VIDEOS        -> 160.dp
        MediaCategory.SHORT_VIDEOS      -> 130.dp
        MediaCategory.SCREEN_RECORDINGS -> 150.dp
        MediaCategory.SCREENSHOTS       -> 120.dp
    }

    val accentColor = when (item.category) {
        MediaCategory.ALL_VIDEOS        -> Color(0xFF5B8DEF)
        MediaCategory.SHORT_VIDEOS      -> Color(0xFF2DBD9A)
        MediaCategory.SCREEN_RECORDINGS -> Color(0xFFF08C4B)
        MediaCategory.SCREENSHOTS       -> Color(0xFFE05C8A)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clickable { onClick() },
        shape  = RoundedCornerShape(AppRadius.md),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail
            if (item.thumbnail != null) {
                AsyncImage(
                    model              = item.thumbnail,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                // Dark overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(accentColor.copy(alpha = 0.15f), CardBg))
                        )
                )
            }

            // Content overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.md),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Category icon
                Box(
                    modifier         = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.category.icon, style = MaterialTheme.typography.labelSmall)
                }

                Column {
                    Text(
                        item.category.title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "${item.count} 项 · ${item.formattedSize()}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
            }

            // Video indicator
            if (item.category == MediaCategory.ALL_VIDEOS || item.category == MediaCategory.SHORT_VIDEOS) {
                Icon(
                    Icons.Default.PlayCircle,
                    null,
                    tint     = TextPrimary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                )
            }
        }
    }
}

// ─── Permission Request Card ──────────────────────────────────────────────────

@Composable
fun PermissionRequestCard(onRequestClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(AppRadius.lg),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier            = Modifier.padding(AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Photo,
                null,
                tint     = AccentPrimary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                "需要相册权限",
                style = MaterialTheme.typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                "授权后可扫描视频、截图等媒体文件",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )
            Spacer(Modifier.height(AppSpacing.lg))
            Button(
                onClick = onRequestClick,
                colors  = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape   = RoundedCornerShape(AppRadius.md)
            ) {
                Text("授权访问")
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0  -> "%.1f GB".format(gb)
        mb >= 0.1  -> "%.0f MB".format(mb)
        else       -> "${bytes / 1024} KB"
    }
}

