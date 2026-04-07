package com.getsolace.ai.chat.ui.screens.ai

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getsolace.ai.chat.CreateAIActivity
import com.getsolace.ai.chat.data.AIGeneratedImage
import com.getsolace.ai.chat.data.AIImageStore
import com.getsolace.ai.chat.data.FeedItem
import com.getsolace.ai.chat.data.UnifiedFeedManager
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.AIViewModel

// ─── AI Lab Feed Screen ───────────────────────────────────────────────────────

@Composable
fun AILabFeedScreen(vm: AIViewModel) {
    val feedItems     by UnifiedFeedManager.items.collectAsStateWithLifecycle()
    val isFeedLoading by UnifiedFeedManager.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by UnifiedFeedManager.isLoadingMore.collectAsStateWithLifecycle()
    val myHistory     by AIImageStore.images.collectAsStateWithLifecycle()
    var showConfig    by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = AppSpacing.lg, end = AppSpacing.lg, top = AppSpacing.sm, bottom = AppSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AuraAI",
                    style    = MaterialTheme.typography.headlineLarge.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = AppFontSize.largeTitle
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (isFeedLoading) {
                    CircularProgressIndicator(
                        color       = AccentPrimary,
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { UnifiedFeedManager.loadFeed() }) {
                        Icon(Icons.Default.Refresh, "刷新", tint = AccentPrimary)
                    }
                }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            var selectedTab by remember { mutableIntStateOf(0) }
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.Transparent,
                contentColor     = AccentPrimary,
                indicator        = { tabPositions ->
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(2.dp)
                            .background(AccentPrimary)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text     = { Text("发现", color = if (selectedTab == 0) AccentPrimary else TextTertiary) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text     = { Text("我的创作 (${myHistory.size})", color = if (selectedTab == 1) AccentPrimary else TextTertiary) }
                )
            }

            // ── Grid ──────────────────────────────────────────────────────────
            when (selectedTab) {
                0 -> {
                    if (feedItems.isEmpty() && !isFeedLoading) {
                        AIEmptyState(modifier = Modifier.weight(1f))
                    } else {
                        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                        LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
                            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
                            val total = gridState.layoutInfo.totalItemsCount
                            if (total > 0 && lastVisible >= total - 4) UnifiedFeedManager.loadMore()
                        }
                        LazyVerticalGrid(
                            state                 = gridState,
                            columns               = GridCells.Fixed(2),
                            contentPadding        = PaddingValues(AppSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalArrangement   = Arrangement.spacedBy(AppSpacing.sm),
                            modifier              = Modifier.weight(1f)
                        ) {
                            items(feedItems, key = { it.id }) { feedItem ->
                                FeedImageCard(
                                    item    = feedItem,
                                    onClick = {
                                        context.startActivity(
                                            CreateAIActivity.newIntent(context, feedItem.prompt)
                                        )
                                    }
                                )
                            }
                            if (isLoadingMore) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier         = Modifier.fillMaxWidth().padding(AppSpacing.md),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color       = AccentPrimary,
                                            modifier    = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (myHistory.isEmpty()) {
                        AIEmptyState(modifier = Modifier.weight(1f))
                    } else {
                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(2),
                            contentPadding        = PaddingValues(AppSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalArrangement   = Arrangement.spacedBy(AppSpacing.sm),
                            modifier              = Modifier.weight(1f)
                        ) {
                            items(myHistory, key = { it.id }) { img ->
                                AIFeedCard(image = img)
                            }
                        }
                    }
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        ExtendedFloatingActionButton(
            onClick        = { showConfig = true },
            containerColor = AccentPrimary,
            contentColor   = TextPrimary,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppSpacing.xl)
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(AppSpacing.sm))
            Text("AI 创作")
        }

        if (showConfig) {
            AIConfigSheet(vm = vm, onDismiss = { showConfig = false })
        }
    }
}

// ─── Feed Image Card ──────────────────────────────────────────────────────────

@Composable
fun FeedImageCard(item: FeedItem, onClick: () -> Unit) {
    val cardHeight = (120 + (1f / item.aspectRatio.coerceIn(0.5f, 2f)) * 80).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(AppRadius.md))
            .background(CardBg)
            .clickable(onClick = onClick)
    ) {
        SolaceAsyncImage(
            model              = item.imageUrl,
            contentDescription = item.prompt,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
            shape              = RoundedCornerShape(AppRadius.md)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(AppSpacing.sm)
        ) {
            Text(
                item.prompt,
                style    = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = AppFontSize.caption2),
                maxLines = 2
            )
        }
    }
}

// ─── Feed Item Detail Overlay ─────────────────────────────────────────────────

@Composable
fun FeedItemDetailOverlay(
    item: FeedItem,
    onDismiss: () -> Unit,
    onUsePrompt: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭", tint = TextPrimary)
                }
                Text(
                    "图片详情",
                    style     = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold),
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SolaceAsyncImage(
                    model              = item.imageUrl,
                    contentDescription = item.prompt,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize()
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
                shape    = RoundedCornerShape(AppRadius.md),
                colors   = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Text("提示词", style = MaterialTheme.typography.labelMedium.copy(color = AccentPrimary))
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(item.prompt, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    Spacer(Modifier.height(AppSpacing.md))
                    Button(
                        onClick  = { onUsePrompt(item.prompt) },
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
            Spacer(Modifier.height(AppSpacing.xxxl))
        }
    }
}

// ─── My History Card ──────────────────────────────────────────────────────────

@Composable
fun AIFeedCard(image: AIGeneratedImage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(AppRadius.md))
            .background(CardBg)
    ) {
        SolaceAsyncImage(
            model              = if (image.imageUrl.isNotBlank()) image.imageUrl else null,
            contentDescription = image.prompt,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
            shape              = RoundedCornerShape(AppRadius.md)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(AppSpacing.sm)
        ) {
            Column {
                Text(image.styleTitle, style = MaterialTheme.typography.labelSmall.copy(color = AccentPrimary), maxLines = 1)
                Text(image.prompt,     style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),   maxLines = 2)
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
fun AIEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = AccentPrimary.copy(alpha = 0.4f), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            "开始你的AI创作",
            style     = MaterialTheme.typography.titleMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            "点击右下角按钮生成你的第一张AI艺术作品",
            style     = MaterialTheme.typography.bodySmall.copy(color = TextTertiary),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = AppSpacing.xxxl)
        )
    }
}
