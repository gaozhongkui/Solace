package com.getsolace.ai.chat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.getsolace.ai.chat.ui.theme.CardBg
import com.getsolace.ai.chat.ui.theme.CardBgAlt
import com.getsolace.ai.chat.ui.theme.TextDisabled

// ─── Shimmer Brush ────────────────────────────────────────────────────────────

@Composable
fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        CardBg,
        CardBgAlt,
        Color(0x269B7AFF),  // subtle violet glow
        CardBgAlt,
        CardBg,
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -600f,
        targetValue  = 1400f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(translateX, 0f),
        end    = Offset(translateX + 600f, 300f)
    )
}

// ─── Shimmer Placeholder Box ──────────────────────────────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(0.dp)) {
    Box(modifier = modifier.clip(shape).background(shimmerBrush()))
}

// ─── SolaceAsyncImage — unified image loader with shimmer + error state ───────

/**
 * Drop-in replacement for [AsyncImage] that shows an animated shimmer while
 * loading and a subtle broken-image icon on error.
 */
@Composable
fun SolaceAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(0.dp),
) {
    var state by remember(model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null)) }

    Box(modifier = modifier) {
        // Shimmer shown while loading
        if (state is AsyncImagePainter.State.Loading) {
            ShimmerBox(modifier = Modifier.matchParentSize(), shape = shape)
        }

        // Error state — dark bg + broken-image icon
        if (state is AsyncImagePainter.State.Error) {
            Box(
                modifier         = Modifier.matchParentSize().clip(shape).background(CardBgAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint               = TextDisabled.copy(alpha = 0.4f)
                )
            }
        }

        AsyncImage(
            model              = model,
            contentDescription = contentDescription,
            contentScale       = contentScale,
            modifier           = Modifier.matchParentSize(),
            onState            = { state = it }
        )
    }
}

// ─── Variant for video thumbnails (ImageRequest already built externally) ─────

@Composable
fun SolaceAsyncImage(
    request: coil.request.ImageRequest,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(0.dp),
) {
    SolaceAsyncImage(
        model              = request,
        contentDescription = contentDescription,
        modifier           = modifier,
        contentScale       = contentScale,
        shape              = shape,
    )
}
