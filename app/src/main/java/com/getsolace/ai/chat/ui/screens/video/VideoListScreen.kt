package com.getsolace.ai.chat.ui.screens.video

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.getsolace.ai.chat.data.MediaCategory
import com.getsolace.ai.chat.data.MediaItem
import com.getsolace.ai.chat.data.MediaManager
import com.getsolace.ai.chat.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─── Sort Options (mirrors iOS VideoListViews.swift: 6 sort types) ───────────

enum class SortOption(val label: String) {
    DATE_DESC("最新优先"),
    DATE_ASC("最旧优先"),
    SIZE_DESC("最大优先"),
    SIZE_ASC("最小优先"),
    DURATION_DESC("最长优先"),
    DURATION_ASC("最短优先")
}

// ─── Video List Screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    categoryName: String,
    navController: NavController
) {
    val category = try {
        MediaCategory.valueOf(categoryName)
    } catch (e: Exception) {
        MediaCategory.ALL_VIDEOS
    }

    val scanResult   by MediaManager.scanResult.collectAsStateWithLifecycle()
    var sortOption   by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    val rawItems = when (category) {
        MediaCategory.ALL_VIDEOS        -> scanResult.allVideos
        MediaCategory.SHORT_VIDEOS      -> scanResult.shortVideos
        MediaCategory.SCREEN_RECORDINGS -> scanResult.screenRecordings
        MediaCategory.SCREENSHOTS       -> scanResult.screenshots
    }

    val sortedItems = remember(rawItems, sortOption) {
        when (sortOption) {
            SortOption.DATE_DESC     -> rawItems.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC      -> rawItems.sortedBy { it.dateAdded }
            SortOption.SIZE_DESC     -> rawItems.sortedByDescending { it.size }
            SortOption.SIZE_ASC      -> rawItems.sortedBy { it.size }
            SortOption.DURATION_DESC -> rawItems.sortedByDescending { it.durationMs }
            SortOption.DURATION_ASC  -> rawItems.sortedBy { it.durationMs }
        }
    }

    // Stats
    val totalSize     = rawItems.sumOf { it.size }
    val totalDuration = rawItems.sumOf { it.durationMs }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BgGradientStart, BgGradientEnd)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ───────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = AppSpacing.md, end = AppSpacing.md, top = AppSpacing.xs, bottom = AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
                }
                Text(
                    category.title,
                    style    = MaterialTheme.typography.titleLarge.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "排序", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded         = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        containerColor   = CardBgAlt
                    ) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text    = {
                                    Text(
                                        opt.label,
                                        color = if (opt == sortOption) AccentPrimary else TextPrimary
                                    )
                                },
                                onClick = {
                                    sortOption   = opt
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // ── Stats Card (mirrors iOS StatsCardView) ────────────────────────
            StatsCard(
                count         = rawItems.size,
                totalSize     = totalSize,
                totalDuration = totalDuration,
                isVideo       = category != MediaCategory.SCREENSHOTS,
                modifier      = Modifier.padding(horizontal = AppSpacing.lg)
            )

            Spacer(Modifier.height(AppSpacing.md))

            // ── List ──────────────────────────────────────────────────────────
            if (sortedItems.isEmpty()) {
                Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (category == MediaCategory.SCREENSHOTS) Icons.Default.Photo else Icons.Default.VideoFile,
                            null, tint = TextTertiary, modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(AppSpacing.md))
                        Text("暂无媒体文件", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding  = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier        = Modifier.weight(1f)
                ) {
                    items(sortedItems, key = { it.id }) { item ->
                        MediaRowItem(item = item)
                    }
                }
            }
        }
    }
}

// ─── Stats Card (mirrors iOS StatsCardView) ───────────────────────────────────

@Composable
fun StatsCard(
    count: Int,
    totalSize: Long,
    totalDuration: Long,
    isVideo: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(AppRadius.md),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(label = "数量", value = "$count 项")
            VerticalDivider(modifier = Modifier.height(32.dp), color = DividerColor)
            StatCell(label = "总大小", value = formatFileSize(totalSize))
            if (isVideo) {
                VerticalDivider(modifier = Modifier.height(32.dp), color = DividerColor)
                StatCell(label = "总时长", value = formatDuration(totalDuration))
            }
        }
    }
}

@Composable
fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(
                color      = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        )
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary))
    }
}

// ─── Media Row Item (mirrors iOS VideoRowView) ────────────────────────────────

@Composable
fun MediaRowItem(item: MediaItem) {
    val dateStr = remember(item.dateAdded) {
        SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(item.dateAdded * 1000))
    }

    var pressed by remember { mutableStateOf(false) }
    val scale   by animateFloatAsState(if (pressed) 0.97f else 1f, label = "scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable { pressed = !pressed },
        shape  = RoundedCornerShape(AppRadius.sm),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier          = Modifier.padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier         = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(AppRadius.sm))
                    .background(CardBgAlt),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model              = item.uri,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
                if (item.isVideo) {
                    Box(
                        modifier         = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint     = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(AppSpacing.md))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    style    = MaterialTheme.typography.bodySmall.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    // Size
                    SurfaceChip(text = item.formattedSize(), color = AccentPrimary.copy(alpha = 0.15f), textColor = AccentPrimary)
                    if (item.isVideo && item.durationMs > 0) {
                        Spacer(Modifier.width(4.dp))
                        SurfaceChip(text = item.formattedDuration(), color = GlowCyan.copy(alpha = 0.12f), textColor = GlowCyan)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(dateStr, style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary))
            }

            // Resolution badge
            ResolutionBadge(width = item.width, height = item.height)
        }
    }
}

@Composable
fun SurfaceChip(text: String, color: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color) {
        Text(
            text     = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style    = MaterialTheme.typography.labelSmall.copy(color = textColor)
        )
    }
}

@Composable
fun ResolutionBadge(width: Int, height: Int) {
    val label = when {
        width >= 3840 || height >= 2160 -> "4K"
        width >= 2560 || height >= 1440 -> "2K"
        width >= 1920 || height >= 1080 -> "FHD"
        width >= 1280 || height >= 720  -> "HD"
        else                            -> "SD"
    }
    val color = when (label) {
        "4K"  -> Color(0xFFFFD700)
        "2K"  -> Color(0xFFFFA500)
        "FHD" -> AccentPrimary
        "HD"  -> GlowCyan
        else  -> TextTertiary
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text     = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style    = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold)
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun formatFileSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0  -> "%.1f GB".format(gb)
        mb >= 0.1  -> "%.0f MB".format(mb)
        else       -> "${bytes / 1024} KB"
    }
}

fun formatDuration(ms: Long): String {
    val secs  = ms / 1000
    val hours = secs / 3600
    val mins  = (secs % 3600) / 60
    val sec   = secs % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, sec)
    else "%d:%02d".format(mins, sec)
}

