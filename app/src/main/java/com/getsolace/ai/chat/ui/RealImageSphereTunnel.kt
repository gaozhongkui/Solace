package com.getsolace.ai.chat.ui

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LocalImageSphereTunnel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }

    val scrollPos = remember { Animatable(0f) }
    // 存储实际 Bitmap，与 ImageGalaxyScreen 加载方式保持一致
    val bitmaps = remember { mutableStateListOf<android.graphics.Bitmap?>() }

    // 与 ImageGalaxyScreen 相同的加载方式：权限检查 + openInputStream + BitmapFactory
    LaunchedEffect(Unit) {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext() && ids.size < 60) {
                    ids.add(cursor.getLong(idCol))
                }
            }

            ids.forEach { id ->
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val bmp = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                } catch (e: Exception) { null }
                bitmaps.add(bmp)
            }
        }
    }

    val reusablePath = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010409))
            .onSizeChanged { size = it }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch { scrollPos.snapTo(scrollPos.value - delta * 3.0f) }
                }
            )
    ) {
        // 将加载好的 Bitmap 交给 Coil 管理（缓存、渲染）
        val painters = bitmaps.map { bmp ->
            rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(bmp)
                    .crossfade(true)
                    .build()
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (painters.isEmpty() || size.width == 0) return@Canvas

            val canvasWidth = size.width.toFloat()
            val canvasHeight = size.height.toFloat()
            val perspective = 1000f
            val itemSpacing = 200f
            val totalLength = painters.size * itemSpacing

            painters.forEachIndexed { i, painter ->
                var depth = (i * itemSpacing - scrollPos.value) % totalLength
                if (depth < 0) depth += totalLength

                if (depth < 2500f) {
                    val scale = perspective / (perspective + depth)
                    val angle = i * 0.55f
                    val spread = 450f * scale
                    val x = canvasWidth / 2 + (cos(angle) * spread)
                    val y = canvasHeight / 2 + (sin(angle) * spread)
                    val radius = 110f * scale
                    val alpha = (1f - depth / 2200f).coerceIn(0f, 1f)

                    if (alpha > 0.1f) {
                        reusablePath.reset()
                        reusablePath.addOval(Rect(Offset(x - radius, y - radius), Size(radius * 2, radius * 2)))

                        clipPath(reusablePath) {
                            // translate 到圆的左上角，让 painter 从该位置开始绘制
                            translate(left = x - radius, top = y - radius) {
                                with(painter) {
                                    draw(
                                        size = Size(radius * 2, radius * 2),
                                        alpha = alpha
                                    )
                                }
                            }
                        }

                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = alpha * 0.5f),
                            radius = radius,
                            center = Offset(x, y),
                            style = Stroke(width = 2f * scale)
                        )
                    }
                }
            }
        }
    }
}
