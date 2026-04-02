@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.getsolace.ai.chat.ui.screens.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    title: String,
    navController: NavController
) {
    val context   = LocalContext.current
    val activity  = context as? Activity

    // ── ExoPlayer setup ───────────────────────────────────────────────────────
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            // 退出时恢复竖屏
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ── Playback state ────────────────────────────────────────────────────────
    var isPlaying       by remember { mutableStateOf(true) }
    var showControls    by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration        by remember { mutableLongStateOf(1L) }
    var isDragging      by remember { mutableStateOf(false) }
    var isLandscape     by remember { mutableStateOf(false) }
    var isEnded         by remember { mutableStateOf(false) }

    // Sync position every 500ms
    LaunchedEffect(player) {
        while (true) {
            if (!isDragging) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
                duration        = player.duration.coerceAtLeast(1L)
                isPlaying       = player.isPlaying
                isEnded         = player.playbackState == Player.STATE_ENDED
            }
            delay(500)
        }
    }

    // Auto-hide controls after 3s when playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication     = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { showControls = !showControls }
    ) {
        // ── PlayerView ────────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player     = player
                    useController   = false
                    resizeMode      = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )

        // ── Replay icon when ended ─────────────────────────────────────────
        if (isEnded) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .align(Alignment.Center)
                    .clickable(
                        indication     = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        player.seekTo(0)
                        player.play()
                        isEnded = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Replay,
                    contentDescription = "重播",
                    tint     = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // ── Controls overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Top gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                )

                // Bottom gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )

                // ── Top bar ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                    Text(
                        title,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color      = Color.White,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    // Rotate / landscape toggle
                    IconButton(
                        onClick = {
                            isLandscape = !isLandscape
                            activity?.requestedOrientation = if (isLandscape)
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    ) {
                        Icon(
                            if (isLandscape) Icons.Default.StayCurrentPortrait
                            else Icons.Default.StayCurrentLandscape,
                            contentDescription = "旋转",
                            tint = Color.White
                        )
                    }
                }

                // ── Center play/pause (shown only when paused) ─────────────────
                if (!isPlaying && !isEnded) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .align(Alignment.Center)
                            .clickable(
                                indication     = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                player.play()
                                isPlaying = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint     = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // ── Bottom controls ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                ) {
                    // Time labels
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDurationMs(currentPosition),
                            fontSize = 12.sp,
                            color    = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            formatDurationMs(duration),
                            fontSize = 12.sp,
                            color    = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Seek bar
                    Slider(
                        value         = currentPosition.toFloat() / duration.toFloat(),
                        onValueChange = { fraction ->
                            isDragging      = true
                            currentPosition = (fraction * duration).toLong()
                        },
                        onValueChangeFinished = {
                            player.seekTo(currentPosition)
                            isDragging = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = SliderDefaults.colors(
                            thumbColor          = Color.White,
                            activeTrackColor    = Color.White,
                            inactiveTrackColor  = Color.White.copy(alpha = 0.3f)
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    )

                    // Play/Pause + Skip row
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // -10s
                        IconButton(onClick = {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        }) {
                            Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        // Play / Pause
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable(
                                    indication     = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (isPlaying) player.pause() else player.play()
                                    isPlaying = !isPlaying
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint     = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // +10s
                        IconButton(onClick = {
                            player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration))
                        }) {
                            Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Helper ─────────────────────────────────────────────────────────────────────

private fun formatDurationMs(ms: Long): String {
    val totalSec = ms / 1000
    val h  = totalSec / 3600
    val m  = (totalSec % 3600) / 60
    val s  = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
