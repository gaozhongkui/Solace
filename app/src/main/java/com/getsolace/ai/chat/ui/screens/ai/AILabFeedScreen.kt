package com.getsolace.ai.chat.ui.screens.ai

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getsolace.ai.chat.CreateAIActivity
import com.getsolace.ai.chat.data.AIGeneratedImage
import com.getsolace.ai.chat.data.FeedItem
import com.getsolace.ai.chat.data.UnifiedFeedManager
import com.getsolace.ai.chat.ui.components.ShimmerBox
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.AIViewModel

// ─── AI Lab Feed Screen ───────────────────────────────────────────────────────

@Composable
fun AILabFeedScreen(vm: AIViewModel) {
    val feedItems     by UnifiedFeedManager.items.collectAsStateWithLifecycle()
    val isFeedLoading by UnifiedFeedManager.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by UnifiedFeedManager.isLoadingMore.collectAsStateWithLifecycle()
    var showConfig    by remember { mutableStateOf(false) }
    var selectedItem  by remember { mutableStateOf<FeedItem?>(null) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = AppSpacing.lg, end = AppSpacing.lg, top = AppSpacing.sm, bottom = AppSpacing.md),
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
                IconButton(
                    onClick = { UnifiedFeedManager.loadFeed() },
                    enabled = !isFeedLoading
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        "刷新",
                        tint = if (isFeedLoading) AccentPrimary.copy(alpha = 0.4f) else AccentPrimary
                    )
                }
            }

            // ── Staggered Feed ────────────────────────────────────────────────
            if (isFeedLoading && feedItems.isEmpty()) {
                // 初始加载：骨架屏瀑布流
                val skeletonRatios = listOf(0.75f, 1.25f, 1.0f, 0.6f, 1.4f, 0.85f, 1.1f, 0.7f)
                LazyVerticalStaggeredGrid(
                    columns               = StaggeredGridCells.Fixed(2),
                    contentPadding        = PaddingValues(AppSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalItemSpacing   = AppSpacing.sm,
                    modifier              = Modifier.weight(1f)
                ) {
                    items(skeletonRatios.size) { index ->
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(skeletonRatios[index]),
                            shape = RoundedCornerShape(AppRadius.md)
                        )
                    }
                }
            } else if (feedItems.isEmpty()) {
                AIEmptyState(modifier = Modifier.weight(1f))
            } else {
                val gridState = rememberLazyStaggeredGridState()

                // 滚近底部时加载更多
                LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
                    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
                    val total = gridState.layoutInfo.totalItemsCount
                    if (total > 0 && lastVisible >= total - 6) UnifiedFeedManager.loadMore()
                }

                LazyVerticalStaggeredGrid(
                    state                 = gridState,
                    columns               = StaggeredGridCells.Fixed(2),
                    contentPadding        = PaddingValues(AppSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalItemSpacing   = AppSpacing.sm,
                    modifier              = Modifier.weight(1f)
                ) {
                    items(feedItems, key = { it.id }) { feedItem ->
                        FeedImageCard(
                            item    = feedItem,
                            onClick = { selectedItem = feedItem }
                        )
                    }

                    // 加载更多时追加骨架卡片
                    if (isLoadingMore) {
                        item {
                            ShimmerBox(
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
                                shape    = RoundedCornerShape(AppRadius.md)
                            )
                        }
                        item {
                            ShimmerBox(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1.3f),
                                shape    = RoundedCornerShape(AppRadius.md)
                            )
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

        // ── 图片详情遮罩 ──────────────────────────────────────────────────────
        selectedItem?.let { item ->
            FeedItemDetailOverlay(
                item        = item,
                onDismiss   = { selectedItem = null },
                onUsePrompt = { prompt ->
                    selectedItem = null
                    context.startActivity(CreateAIActivity.newIntent(context, prompt))
                }
            )
        }

        if (showConfig) {
            AIConfigSheet(vm = vm, onDismiss = { showConfig = false })
        }
    }
}

// ─── Feed Image Card ──────────────────────────────────────────────────────────

@Composable
fun FeedImageCard(item: FeedItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(item.aspectRatio.coerceIn(0.5f, 2.0f))
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
        // 底部渐变 + prompt 文字
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .padding(AppSpacing.sm)
        ) {
            Text(
                item.prompt,
                style    = MaterialTheme.typography.bodySmall.copy(
                    color    = TextPrimary,
                    fontSize = AppFontSize.caption2
                ),
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
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val isZoomed = scale > 1.05f

    // 缩放/平移状态
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale  = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    // 缩放中按返回键 → 先复位，否则关闭
    BackHandler(enabled = true) {
        if (isZoomed) {
            scale = 1f; offset = Offset.Zero
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── 顶部栏（避开状态栏）─────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isZoomed) { scale = 1f; offset = Offset.Zero } else onDismiss()
                }) {
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

            // ── 可缩放图片区域 ────────────────────────────────────────────────
            Box(
                modifier          = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(0.dp))          // 裁剪溢出部分
                    .transformable(state = transformState)   // 双指缩放 + 平移
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // 双击切换缩放
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
                    model              = item.imageUrl,
                    contentDescription = item.prompt,
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

            // ── 底部提示词卡（缩放时隐藏）────────────────────────────────────
            AnimatedVisibility(
                visible = !isZoomed,
                enter   = fadeIn(),
                exit    = fadeOut()
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
                                item.prompt,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                            )
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
