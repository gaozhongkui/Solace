package com.getsolace.ai.chat.ui.screens.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.getsolace.ai.chat.data.Photo
import com.getsolace.ai.chat.ui.screens.ScreenScaffold
import com.getsolace.ai.chat.data.rememberGalleryPhotos
import kotlinx.coroutines.delay

private const val AUTO_PLAY_DURATION = 3000L

@Composable
fun StoryScreen(navController: NavController) {
    ScreenScaffold(title = "故事模式", navController = navController) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val photos = rememberGalleryPhotos()
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                StoryGallery(photos = photos)
            }
        }
    }
}

@Composable
fun StoryGallery(photos: List<Photo>) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    val thumbnailListState = rememberLazyListState()

    LaunchedEffect(currentIndex, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        progress = 0f
        val steps = 100
        repeat(steps) {
            delay(AUTO_PLAY_DURATION / steps)
            progress = (it + 1) / steps.toFloat()
        }
        currentIndex = if (currentIndex < photos.size - 1) currentIndex + 1 else 0
    }

    LaunchedEffect(currentIndex) {
        thumbnailListState.animateScrollToItem(index = (currentIndex - 2).coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部进度条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            photos.forEachIndexed { index, _ ->
                val segmentProgress = when {
                    index < currentIndex -> 1f
                    index == currentIndex -> progress
                    else -> 0f
                }
                LinearProgressIndicator(
                    progress = { segmentProgress },
                    modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // 主图区域
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                label = "main_photo"
            ) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (currentIndex < photos.size - 1) currentIndex++
                            else currentIndex = 0
                        }
                ) {
                    AsyncImage(
                        model = photos[index].uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = photos[index].date,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 播放/暂停
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Add else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 左右点击区域切换
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                    if (currentIndex > 0) { currentIndex--; progress = 0f }
                })
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                    currentIndex = if (currentIndex < photos.size - 1) currentIndex + 1 else 0
                    progress = 0f
                })
            }
        }

        // 底部缩略图
        LazyRow(
            state = thumbnailListState,
            modifier = Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(photos) { index, photo ->
                StoryThumbnail(
                    photo = photo,
                    isSelected = index == currentIndex,
                    onClick = { currentIndex = index; progress = 0f }
                )
            }
        }
    }
}

@Composable
fun StoryThumbnail(photo: Photo, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
    }
}
