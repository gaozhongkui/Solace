package com.getsolace.ai.chat.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.getsolace.ai.chat.Photo
import com.getsolace.ai.chat.ScreenScaffold
import com.getsolace.ai.chat.rememberGalleryPhotos
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun StackedCardsScreen(navController: NavController) {
    ScreenScaffold(title = "叠卡翻阅", navController = navController) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            val photos = rememberGalleryPhotos()
            if (photos.isEmpty()) {
                CircularProgressIndicator()
            } else {
                StackedCardGallery(photos = photos)
            }
        }
    }
}

@Composable
fun StackedCardGallery(photos: List<Photo>) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) offsetX else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_offset"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景卡片 (最底层 +2)
            if (currentIndex + 2 < photos.size) {
                StackedCard(photo = photos[currentIndex + 2], rotation = -6f, scale = 0.88f, offsetY = (-20).dp, zIndex = 1f)
            }
            // 背景卡片 (中间层 +1)
            if (currentIndex + 1 < photos.size) {
                StackedCard(photo = photos[currentIndex + 1], rotation = 3f, scale = 0.94f, offsetY = (-10).dp, zIndex = 2f)
            }
            // 顶层当前卡片（可拖拽）
            val swipeRotation = (animatedOffset / 30f).coerceIn(-15f, 15f)
            Box(
                modifier = Modifier
                    .zIndex(3f)
                    .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                    .rotate(swipeRotation)
                    .size(width = 280.dp, height = 380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .pointerInput(currentIndex) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                if (offsetX.absoluteValue > 120f && currentIndex < photos.size - 1) {
                                    currentIndex++
                                }
                                offsetX = 0f
                            },
                            onDragCancel = { isDragging = false; offsetX = 0f },
                            onHorizontalDrag = { _, dragAmount -> offsetX += dragAmount }
                        )
                    },
                contentAlignment = Alignment.BottomStart
            ) {
                AsyncImage(
                    model = photos[currentIndex].uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                // 底部渐变遮罩 + 日期
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = photos[currentIndex].date,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                if (animatedOffset.absoluteValue < 10f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text("← 左右滑动 →", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        // 进度指示器
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            photos.take(8).forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentIndex) 20.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (index == currentIndex)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                )
            }
        }

        Text(
            text = "${currentIndex + 1} / ${photos.size}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun StackedCard(
    photo: Photo,
    rotation: Float,
    scale: Float,
    offsetY: androidx.compose.ui.unit.Dp,
    zIndex: Float
) {
    Box(
        modifier = Modifier
            .zIndex(zIndex)
            .offset(y = offsetY)
            .scale(scale)
            .rotate(rotation)
            .size(width = 280.dp, height = 380.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
    }
}
