package com.getsolace.ai.chat.ui.screens.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.getsolace.ai.chat.data.AIGeneratedImage
import com.getsolace.ai.chat.data.AIImageStore
import com.getsolace.ai.chat.ui.screens.ai.RunMateToast
import com.getsolace.ai.chat.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─── Me Screen ────────────────────────────────────────────────────────────────

@Composable
fun MeScreen() {
    val history by AIImageStore.images.collectAsStateWithLifecycle()

    var selectedImage    by remember { mutableStateOf<AIGeneratedImage?>(null) }
    var showSettings     by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BgGradientStart, BgGradientEnd)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "我的",
                    style    = MaterialTheme.typography.headlineLarge.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = AppFontSize.largeTitle
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick  = { showSettings = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceOverlay)
                ) {
                    Icon(Icons.Default.Settings, "设置", tint = TextSecondary)
                }
            }

            // ── Profile hero card ──────────────────────────────────────────────
            ProfileHeroCard(
                imageCount = history.size,
                modifier   = Modifier.padding(horizontal = AppSpacing.lg)
            )

            Spacer(Modifier.height(AppSpacing.xl))

            // ── Section title ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint               = AccentPrimary,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    "我的创作",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${history.size} 件作品",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary)
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            // ── Creations grid ────────────────────────────────────────────────
            if (history.isEmpty()) {
                CreationsEmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    contentPadding        = PaddingValues(AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement   = Arrangement.spacedBy(AppSpacing.sm),
                    modifier              = Modifier.weight(1f)
                ) {
                    items(history, key = { it.id }) { img ->
                        AIHistoryCard(
                            image   = img,
                            onClick = { selectedImage = img }
                        )
                    }
                }
            }
        }

        // ── Image detail overlay ───────────────────────────────────────────────
        selectedImage?.let { img ->
            AIImageDetailOverlay(
                image     = img,
                onDismiss = { selectedImage = null },
                onDelete  = {
                    AIImageStore.deleteImage(img.id)
                    selectedImage = null
                }
            )
        }

        // ── Settings sheet ────────────────────────────────────────────────────
        if (showSettings) {
            SettingsSheet(onDismiss = { showSettings = false })
        }
    }
}

// ─── Profile Hero Card ────────────────────────────────────────────────────────

@Composable
fun ProfileHeroCard(imageCount: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(AppRadius.lg),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier          = Modifier.padding(AppSpacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier         = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(AccentStart, AccentEnd))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint               = TextPrimary,
                    modifier           = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.width(AppSpacing.lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "创作者",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    "AI艺术爱好者",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }

        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = AppSpacing.xl))

        // Stats row
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "AI创作", value = "$imageCount")
            VerticalDivider(
                modifier  = Modifier.height(32.dp),
                color     = DividerColor,
                thickness = 1.dp
            )
            StatItem(label = "风格探索", value = "5")
            VerticalDivider(
                modifier  = Modifier.height(32.dp),
                color     = DividerColor,
                thickness = 1.dp
            )
            StatItem(label = "保险箱", value = "安全")
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                color      = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
        )
    }
}

// ─── AI History Card ──────────────────────────────────────────────────────────

@Composable
fun AIHistoryCard(image: AIGeneratedImage, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(CardBg)
            .clickable(onClick = onClick)
    ) {
        if (image.imageUrl.isNotBlank()) {
            AsyncImage(
                model              = image.imageUrl,
                contentDescription = image.prompt,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(CardBgAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint               = TextTertiary
                )
            }
        }
        // Style badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(AppSpacing.xs)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = AccentPrimary.copy(alpha = 0.85f)
            ) {
                Text(
                    image.styleTitle,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style    = MaterialTheme.typography.labelSmall.copy(
                        color    = TextPrimary,
                        fontSize = AppFontSize.caption2
                    )
                )
            }
        }
    }
}

@Composable
fun CreationsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint               = TextTertiary,
            modifier           = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            "还没有创作记录",
            style     = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Image Detail Overlay ─────────────────────────────────────────────────────

@Composable
fun AIImageDetailOverlay(
    image: AIGeneratedImage,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateStr = remember(image.createdAt) {
        SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA).format(Date(image.createdAt))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭", tint = TextPrimary)
                }
                Text(
                    "作品详情",
                    style    = MaterialTheme.typography.titleMedium.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, "删除", tint = ErrorRed)
                }
            }

            // Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = AppSpacing.lg)
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(CardBg)
            ) {
                if (image.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model              = image.imageUrl,
                        contentDescription = image.prompt,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.xl))

            // Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg),
                shape  = RoundedCornerShape(AppRadius.md),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    InfoRow(label = "风格", value = image.styleTitle)
                    Spacer(Modifier.height(AppSpacing.sm))
                    InfoRow(label = "比例", value = image.aspectRatio)
                    Spacer(Modifier.height(AppSpacing.sm))
                    InfoRow(label = "创作时间", value = dateStr)
                    if (image.prompt.isNotBlank()) {
                        Spacer(Modifier.height(AppSpacing.md))
                        HorizontalDivider(color = DividerColor)
                        Spacer(Modifier.height(AppSpacing.md))
                        Text("提示词", style = MaterialTheme.typography.labelMedium.copy(color = AccentPrimary))
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(image.prompt, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.xxxl))
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title            = { Text("删除作品", color = TextPrimary) },
                text             = { Text("确定要删除这件作品吗？", color = TextSecondary) },
                confirmButton    = {
                    TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                        Text("删除", color = ErrorRed)
                    }
                },
                dismissButton    = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消", color = TextSecondary)
                    }
                },
                containerColor   = CardBgAlt,
                shape            = RoundedCornerShape(AppRadius.lg)
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style    = MaterialTheme.typography.bodySmall.copy(color = TextTertiary),
            modifier = Modifier.width(80.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
    }
}

// ─── Settings Sheet ───────────────────────────────────────────────────────────

@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .align(Alignment.BottomCenter)
                .clickable { /* absorb */ },
            shape  = RoundedCornerShape(topStart = AppRadius.xl, topEnd = AppRadius.xl),
            colors = CardDefaults.cardColors(containerColor = BgDeepAlt)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.xl)
                    .verticalScroll(rememberScrollState())
            ) {
                // Handle bar
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TextTertiary)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(AppSpacing.xl))

                Text(
                    "设置",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(AppSpacing.xl))

                // Settings items
                SettingsItem(
                    icon    = Icons.Default.Feedback,
                    title   = "意见反馈",
                    onClick = { }
                )
                SettingsItem(
                    icon    = Icons.Default.PrivacyTip,
                    title   = "隐私政策",
                    onClick = { }
                )
                SettingsItem(
                    icon    = Icons.Default.Description,
                    title   = "服务条款",
                    onClick = { }
                )
                SettingsItem(
                    icon    = Icons.Default.Star,
                    title   = "给应用评分",
                    onClick = { }
                )
                SettingsItem(
                    icon    = Icons.Default.Info,
                    title   = "关于 AuraAI",
                    subtitle = "版本 1.0",
                    onClick  = { }
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AccentPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary))
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint               = TextTertiary,
            modifier           = Modifier.size(18.dp)
        )
    }
    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f))
}

