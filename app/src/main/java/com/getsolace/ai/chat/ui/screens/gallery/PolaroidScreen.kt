package com.getsolace.ai.chat.ui.screens.gallery

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.getsolace.ai.chat.ui.components.SolaceAsyncImage
import com.getsolace.ai.chat.data.Photo
import com.getsolace.ai.chat.ui.screens.ScreenScaffold
import com.getsolace.ai.chat.data.rememberGalleryPhotos

data class PolaroidState(
    val photo: Photo,
    var x: Float,
    var y: Float,
    val rotation: Float
)

@Composable
fun PolaroidScreen(navController: NavController) {
    ScreenScaffold(title = "拍立得墙", navController = navController) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val photos = rememberGalleryPhotos(max = 8)
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                PolaroidWall(photos = photos)
            }
        }
    }
}

@Composable
fun PolaroidWall(photos: List<Photo>) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val polaroids = remember(containerSize, photos) {
        if (containerSize == IntSize.Zero || photos.isEmpty()) return@remember mutableStateListOf()
        val rotations = listOf(-8f, 5f, -3f, 7f, -6f, 4f, -2f, 9f)
        val w = containerSize.width.toFloat()
        val h = containerSize.height.toFloat()
        val positions = listOf(
            Pair(0.05f, 0.03f), Pair(0.45f, 0.0f), Pair(0.72f, 0.06f),
            Pair(0.08f, 0.38f), Pair(0.55f, 0.33f),
            Pair(0.02f, 0.65f), Pair(0.38f, 0.62f), Pair(0.68f, 0.58f),
        )
        photos.take(8).mapIndexed { i, photo ->
            val (px, py) = positions[i]
            PolaroidState(
                photo = photo,
                x = w * px,
                y = h * py,
                rotation = rotations[i % rotations.size]
            )
        }.toMutableList()
    }.let { remember { it } }

    var topIndex by remember { mutableIntStateOf(polaroids.size - 1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
    ) {
        polaroids.forEachIndexed { index, state ->
            DraggablePolaroid(
                state = state,
                isTop = index == topIndex,
                zIndex = if (index == topIndex) 10f else index.toFloat(),
                onDragStart = { topIndex = index },
                onDrag = { dx, dy ->
                    state.x += dx
                    state.y += dy
                }
            )
        }

        Text(
            text = "长按拖动照片",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

@Composable
fun DraggablePolaroid(
    state: PolaroidState,
    isTop: Boolean,
    zIndex: Float,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 20f else zIndex)
            .offset { IntOffset(state.x.toInt(), state.y.toInt()) }
            .rotate(state.rotation)
            .shadow(if (isDragging) 16.dp else 4.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .width(140.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true; onDragStart() },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
                )
            }
    ) {
        Column {
            SolaceAsyncImage(
                model = state.photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(2.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.photo.date,
                    fontSize = 11.sp,
                    color = Color(0xFF555555)
                )
            }
        }
    }
}
