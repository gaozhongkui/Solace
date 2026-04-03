package com.getsolace.ai.chat.ui.screens.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
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
import com.getsolace.ai.chat.ui.theme.SurfaceOverlay
import kotlinx.coroutines.launch

@Composable
fun PhotoDetailScreen(
    encodedUri: String,
    navController: NavController
) {
    val uri = remember(encodedUri) {
        android.net.Uri.parse(java.net.URLDecoder.decode(encodedUri, "UTF-8"))
    }

    var scale       by remember { mutableStateOf(1f) }
    var offset      by remember { mutableStateOf(Offset.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    val scaleAnim  = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 同步动画值到实际状态
    scale  = scaleAnim.value
    offset = Offset(offsetXAnim.value, offsetYAnim.value)

    fun constrainOffset(s: Float, raw: Offset): Offset {
        val maxX = (contentSize.width  * (s - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (contentSize.height * (s - 1f) / 2f).coerceAtLeast(0f)
        return Offset(
            raw.x.coerceIn(-maxX, maxX),
            raw.y.coerceIn(-maxY, maxY)
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scaleAnim.value * zoomChange).coerceIn(1f, 5f)
        val newOffset = constrainOffset(newScale, Offset(offsetXAnim.value, offsetYAnim.value) + panChange)
        scope.launch {
            scaleAnim.snapTo(newScale)
            offsetXAnim.snapTo(newOffset.x)
            offsetYAnim.snapTo(newOffset.y)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // ── 图片区域 ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                if (scaleAnim.value > 1f) {
                                    // 双击还原
                                    launch { scaleAnim.animateTo(1f, tween(250)) }
                                    launch { offsetXAnim.animateTo(0f, tween(250)) }
                                    launch { offsetYAnim.animateTo(0f, tween(250)) }
                                } else {
                                    // 双击放大到 2.5x，以点击位置为中心
                                    val targetScale = 2.5f
                                    val cx = contentSize.width  / 2f
                                    val cy = contentSize.height / 2f
                                    val rawX = (cx - it.x) * (targetScale - 1f)
                                    val rawY = (cy - it.y) * (targetScale - 1f)
                                    val clamped = constrainOffset(targetScale, Offset(rawX, rawY))
                                    launch { scaleAnim.animateTo(targetScale, tween(250)) }
                                    launch { offsetXAnim.animateTo(clamped.x, tween(250)) }
                                    launch { offsetYAnim.animateTo(clamped.y, tween(250)) }
                                }
                            }
                        }
                    )
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
                        scaleX       = scale,
                        scaleY       = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }

        // ── 返回按钮 ─────────────────────────────────────────────────────────
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
