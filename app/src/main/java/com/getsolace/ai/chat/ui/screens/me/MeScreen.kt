package com.getsolace.ai.chat.ui.screens.me

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getsolace.ai.chat.AIImageDetailActivity
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.data.AIGeneratedImage
import com.getsolace.ai.chat.data.AIImageStore
import com.getsolace.ai.chat.ui.theme.*

// ─── Local design tokens ──────────────────────────────────────────────────────

private val DeepBg        = Color(0xFF0A0B14)
private val CardSurface   = Color(0xFF131722)
private val CardBorder    = Color(0x0FFFFFFF)
private val VioletBright  = Color(0xFF9B7AFF)
private val VioletFaint   = Color(0x1E9B7AFF)
private val VioletMid     = Color(0x409B7AFF)
private val TealBright    = Color(0xFF00D4B4)
private val TealFaint     = Color(0x1A00D4B4)
private val TextPri       = Color(0xFFFFFFFF)
private val TextSec       = Color(0x73FFFFFF)
private val TextTer       = Color(0x40FFFFFF)

// ─── Me Screen ────────────────────────────────────────────────────────────────

@Composable
fun MeScreen() {
    val history  by AIImageStore.images.collectAsStateWithLifecycle()
    val context  = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = DeepBg)
                // top-right violet glow
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(Color(0x207850FF), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.6f
                    ),
                    center = Offset(size.width * 0.9f, 0f),
                    radius = size.width * 0.6f
                )
                // bottom-left teal glow
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(Color(0x1400C8AA), Color.Transparent),
                        center = Offset(0f, size.height * 0.85f),
                        radius = size.width * 0.5f
                    ),
                    center = Offset(0f, size.height * 0.85f),
                    radius = size.width * 0.5f
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "我的",
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPri,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "创作中心",
                        fontSize = 13.sp,
                        color    = TextSec,
                        letterSpacing = 0.3.sp
                    )
                }
                // Settings button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
                        .clickable { showSettings = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        tint     = Color(0x99FFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Profile hero card ──────────────────────────────────────────────
            ProfileHeroCard(
                imageCount = history.size,
                modifier   = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Section title ──────────────────────────────────────────────────
            Row(
                modifier          = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VioletFaint)
                        .border(1.dp, VioletMid, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint     = VioletBright,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "我的创作",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TextPri
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${history.size} 件作品",
                    fontSize = 12.sp,
                    color    = VioletBright.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Creations grid ────────────────────────────────────────────────
            if (history.isEmpty()) {
                CreationsEmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    contentPadding        = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    items(history, key = { it.id }) { img ->
                        AIHistoryCard(
                            image   = img,
                            onClick = {
                                context.startActivity(
                                    AIImageDetailActivity.newIntent(context, img.id)
                                )
                            }
                        )
                    }
                }
            }
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A1530), Color(0xFF111827))
                )
            )
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
            .drawBehind {
                // violet glow top-right inside card
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(Color(0x259B7AFF), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.5f
                    ),
                    center = Offset(size.width * 0.9f, 0f),
                    radius = size.width * 0.5f
                )
            }
    ) {
        Column {
            // Avatar + info row
            Row(
                modifier          = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with glow ring
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(VioletBright.copy(alpha = 0.3f), Color.Transparent)
                                )
                            )
                    )
                    // Avatar circle
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF9B7AFF), Color(0xFF6A4FCC))
                                )
                            )
                            .border(2.dp, VioletBright.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "创作者",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPri
                    )
                    Spacer(Modifier.height(3.dp))
                    // Tag badges
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagBadge("AI艺术", VioletBright)
                        TagBadge("创作者", TealBright)
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x14FFFFFF))
            )

            // Stats row
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    value = "$imageCount",
                    label = "AI创作",
                    accentColor = VioletBright
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color(0x14FFFFFF))
                )
                StatCard(
                    value = "5",
                    label = "探索风格",
                    accentColor = TealBright
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color(0x14FFFFFF))
                )
                StatCard(
                    value = "安全",
                    label = "保险箱",
                    accentColor = Color(0xFF34C759)
                )
            }
        }
    }
}

@Composable
private fun TagBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatCard(value: String, label: String, accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            value,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = accentColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            color    = TextSec
        )
    }
}

// Keep old StatItem for compatibility (unused now but avoid removing it abruptly)
@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPri)
        Text(label, fontSize = 11.sp, color = TextTer)
    }
}

// ─── AI History Card ──────────────────────────────────────────────────────────

@Composable
fun AIHistoryCard(image: AIGeneratedImage, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        SolaceAsyncImage(
            model              = image.imageUrl.ifBlank { null },
            contentDescription = image.prompt,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
            shape              = RoundedCornerShape(12.dp)
        )
        if (image.imageUrl.isBlank()) {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color(0xFF1C2135)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint     = Color(0x40FFFFFF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        // Style badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(VioletBright.copy(alpha = 0.88f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(image.styleTitle, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Medium)
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
        // Glowing icon container
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(VioletBright.copy(alpha = 0.2f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VioletFaint)
                    .border(1.dp, VioletMid, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint     = VioletBright,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "还没有创作记录",
            fontSize   = 15.sp,
            fontWeight = FontWeight.Medium,
            color      = TextPri,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "去「创作」Tab 生成你的第一张 AI 画作",
            fontSize  = 12.sp,
            color     = TextSec,
            textAlign = TextAlign.Center
        )
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF111827))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color(0x33FFFFFF), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable { /* absorb */ }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Handle bar
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0x40FFFFFF))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    "设置",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPri
                )
                Spacer(Modifier.height(20.dp))

                SettingsItem(icon = Icons.Default.Feedback,   title = "意见反馈",      onClick = { })
                SettingsItem(icon = Icons.Default.PrivacyTip, title = "隐私政策",      onClick = { })
                SettingsItem(icon = Icons.Default.Description,title = "服务条款",      onClick = { })
                SettingsItem(icon = Icons.Default.Star,       title = "给应用评分",    onClick = { })
                SettingsItem(icon = Icons.Default.Info,       title = "关于 AuraAI",   subtitle = "版本 1.0", onClick = { })
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VioletFaint)
                .border(1.dp, VioletMid, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = VioletBright, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = TextPri)
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = TextSec)
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint     = Color(0x40FFFFFF),
            modifier = Modifier.size(18.dp)
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x0DFFFFFF)))
}
