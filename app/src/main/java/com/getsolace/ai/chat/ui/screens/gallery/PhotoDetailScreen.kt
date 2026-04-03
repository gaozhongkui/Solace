package com.getsolace.ai.chat.ui.screens.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.getsolace.ai.chat.data.rememberGalleryPhotos
import com.getsolace.ai.chat.ui.theme.SurfaceOverlay
import kotlinx.coroutines.launch

@Composable
fun PhotoDetailScreen(
    encodedUri: String,
    navController: NavController
) {
    val photos = rememberGalleryPhotos()
    val currentUri = remember(encodedUri) {
        android.net.Uri.parse(java.net.URLDecoder.decode(encodedUri, "UTF-8"))
    }

    val pagerState = rememberPagerState(initialPage = 0) { if (photos.isEmpty()) 1 else photos.size }

    // photos 加载完后跳到被点击的那张
    var scrolledToInitial by remember { mutableStateOf(false) }
    LaunchedEffect(photos) {
        if (photos.isNotEmpty() && !scrolledToInitial) {
            val idx = photos.indexOfFirst { it.uri == currentUri }.coerceAtLeast(0)
            pagerState.scrollToPage(idx)
            scrolledToInitial = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val uri = if (photos.isEmpty()) currentUri else photos[page].uri
            ZoomablePhotoPage(uri = uri)
        }

        // ── 返回按钮 ────────────────────────────────────────────────────────
        IconButton(
            onClick  = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(SurfaceOverlay, CircleShape)
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint               = Color.White
            )
        }
    }
}

@Composable
private fun ZoomablePhotoPage(uri: android.net.Uri) {
    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    val scaleAnim   = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun constrainOffset(s: Float, raw: Offset): Offset {
        val maxX = (contentSize.width  * (s - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (contentSize.height * (s - 1f) / 2f).coerceAtLeast(0f)
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ① 双击放大/还原
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scaleAnim.value > 1f) {
                                launch { scaleAnim.animateTo(1f, tween(250)) }
                                launch { offsetXAnim.animateTo(0f, tween(250)) }
                                launch { offsetYAnim.animateTo(0f, tween(250)) }
                            } else {
                                val targetScale = 2.5f
                                val cx = contentSize.width  / 2f
                                val cy = contentSize.height / 2f
                                val clamped = constrainOffset(
                                    targetScale,
                                    Offset((cx - tapOffset.x) * (targetScale - 1f),
                                           (cy - tapOffset.y) * (targetScale - 1f))
                                )
                                launch { scaleAnim.animateTo(targetScale, tween(250)) }
                                launch { offsetXAnim.animateTo(clamped.x, tween(250)) }
                                launch { offsetYAnim.animateTo(clamped.y, tween(250)) }
                            }
                        }
                    }
                )
            }
            // ② 捏合缩放 + 平移（scale=1 单指时不消费事件，让 Pager 接管）
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val isMultiTouch = event.changes.size > 1
                        val zoomed = scaleAnim.value > 1f

                        if (isMultiTouch || zoomed) {
                            val zoomChange = event.calculateZoom()
                            val panChange  = event.calculatePan()
                            val newScale   = (scaleAnim.value * zoomChange).coerceIn(1f, 5f)
                            val newOffset  = constrainOffset(
                                newScale,
                                Offset(offsetXAnim.value, offsetYAnim.value) + panChange
                            )
                            scope.launch {
                                scaleAnim.snapTo(newScale)
                                offsetXAnim.snapTo(newOffset.x)
                                offsetYAnim.snapTo(newOffset.y)
                            }
                            // 消费事件，Pager 不再处理
                            event.changes.forEach { it.consume() }
                        }
                        // scale=1 单指：不消费 → Pager 接收横向滑动
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model              = uri,
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .fillMaxSize()
                .onSizeChanged { contentSize = it }
                .graphicsLayer(
                    scaleX       = scaleAnim.value,
                    scaleY       = scaleAnim.value,
                    translationX = offsetXAnim.value,
                    translationY = offsetYAnim.value
                )
        )
    }
}
