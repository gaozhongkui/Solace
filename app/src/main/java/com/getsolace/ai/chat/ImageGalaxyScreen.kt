package com.getsolace.ai.chat

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────
// 1. 数据模型与枚举 (Data Models)
// ─────────────────────────────────────────

private data class Vec3(val x: Double, val y: Double, val z: Double)
private data class Projected(val x: Float, val y: Float, val scale: Float, val zIndex: Double)
private data class StarParticle(val x: Float, val y: Float, val size: Float, val opacity: Float, val twinkleOffset: Float)

data class ImageParticle(
    val id: Int,
    val bitmap: ImageBitmap?,
    val index: Int,
    val seed: Int
)

enum class GalaxyShape(val displayName: String) {
    SPHERE("SPHERE"), HEART("HEART"), SPIRAL("SPIRAL"), DNA("DNA");
    fun next(): GalaxyShape = entries[(ordinal + 1) % entries.size]
}

// ─────────────────────────────────────────
// 2. 3D 数学引擎 (3D Engine Core)
// ─────────────────────────────────────────

private fun rotate(p: Vec3, rotX: Double, rotY: Double): Vec3 {
    val sY = sin(rotY); val cY = cos(rotY)
    val sX = sin(rotX); val cX = cos(rotX)
    val q = Vec3(p.x * cY + p.z * sY, p.y, -p.x * sY + p.z * cY)
    return Vec3(q.x, q.y * cX - q.z * sX, q.y * sX + q.z * cX)
}

private fun project(p: Vec3, w: Float, h: Float, zoom: Float): Projected {
    val fov = 850.0 // 焦距
    val cameraZ = 450.0 // 相机深度
    val factor = fov / (fov + p.z + cameraZ)
    val scale = (factor * zoom).toFloat()
    return Projected(
        x = (p.x * scale + w / 2f).toFloat(),
        y = (p.y * scale + h / 2f).toFloat(),
        scale = scale,
        zIndex = p.z // 用于深度排序
    )
}

private fun positionFor(p: ImageParticle, shape: GalaxyShape, total: Int, time: Double): Vec3 {
    val t = p.index.toDouble() / max(total - 1, 1)
    return when (shape) {
        GalaxyShape.SPHERE -> {
            val phi = acos(1.0 - 2.0 * p.index / max(total, 1))
            val theta = p.index * PI * (3.0 - sqrt(5.0)) // 黄金角度
            Vec3(280.0 * sin(phi) * cos(theta), 280.0 * sin(phi) * sin(theta), 280.0 * cos(phi))
        }
        GalaxyShape.HEART -> {
            val a = t * 2.0 * PI
            val s = sin(a)
            val x = 230.0 * 0.85 * (s * s * s)
            val y = -230.0 * 0.78 * (13.0 * cos(a) - 5.0 * cos(2 * a) - 2.0 * cos(3 * a) - cos(4 * a)) / 16.0
            Vec3(x, y, 0.0)
        }
        GalaxyShape.SPIRAL -> {
            val angle = t * 5.0 * PI + time * 0.1
            val r = 60.0 + 220.0 * t
            Vec3(r * cos(angle), (t - 0.5) * 520.0, r * sin(angle))
        }
        GalaxyShape.DNA -> {
            val strand = if (p.index % 2 == 0) 0.0 else PI
            val angle = t * 5.0 * PI + strand + time * 0.08
            Vec3(140.0 * cos(angle), (t - 0.5) * 520.0, 140.0 * sin(angle))
        }
    }
}

// ─────────────────────────────────────────
// 3. 粒子渲染辅助 (Rendering Helpers)
// ─────────────────────────────────────────

// 生成心形裁剪路径
private fun heartPath(cx: Float, cy: Float, size: Float): Path = Path().apply {
    val steps = 45
    for (i in 0..steps) {
        val t = i.toDouble() * 2.0 * PI / steps
        val s = sin(t)
        val px = (s * s * s * size).toFloat()
        val py = (-(13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t)) / 16.0 * size * 0.9).toFloat()
        if (i == 0) moveTo(cx + px, cy + py) else lineTo(cx + px, cy + py)
    }
    close()
}

// 绘制单个粒子 (Glow + Image + Stroke)
private fun DrawScope.drawGalaxyParticle(
    p: ImageParticle, cx: Float, cy: Float, size: Float,
    alpha: Float, glowColor: Color, shape: GalaxyShape
) {
    val half = size / 2f
    val path = if (shape == GalaxyShape.HEART) heartPath(cx, cy, half)
    else Path().apply { addOval(Rect(cx - half, cy - half, cx + half, cy + half)) }

    // 1. 【核心优化】超细外边框发光 (取代大面积光晕)
    // 我们只画一个比路径稍大一点点的描边，并带上模糊效果
    if (alpha > 0.5f) {
        drawPath(
            path = path,
            color = glowColor.copy(alpha = 0.4f * alpha),
            style = Stroke(width = 6f) // 只有 6 像素的窄边发光
        )
        // 极细的中心线，增加锐度
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.6f * alpha),
            style = Stroke(width = 1.5f)
        )
    }

    // 2. 照片主体：强制底层涂黑，彻底解决“光晕渗入图片”导致的模糊感
    drawPath(path, color = Color.Black)

    clipPath(path) {
        if (p.bitmap != null) {
            drawImage(
                p.bitmap,
                dstOffset = IntOffset((cx - half).toInt(), (cy - half).toInt()),
                dstSize = IntSize(size.toInt(), size.toInt()),
                alpha = alpha // 保持照片原本的通透度
            )
        } else {
            drawRect(glowColor.copy(0.2f), alpha = alpha)
        }
    }

    // 3. 【点睛之笔】内边缘高光 (Inner Glow)
    // 在照片内侧画一圈极细的白边，像手机屏幕边缘的倒角高光
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.2f * alpha),
        style = Stroke(width = 1.0f)
    )
}

// ─────────────────────────────────────────
// 4. 主屏幕组件 (Main Composable)
// ─────────────────────────────────────────

@Composable
fun ImageGalaxyScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- 状态变量 ---
    var particles by remember { mutableStateOf(emptyList<ImageParticle>()) }
    var stars by remember { mutableStateOf(emptyList<StarParticle>()) }
    var animationTime by remember { mutableDoubleStateOf(0.0) }
    var currentShape by remember { mutableStateOf(GalaxyShape.SPHERE) }
    var selectedParticle by remember { mutableStateOf<ImageParticle?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // --- 物理模拟：旋转与缩放 ---
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    // 氛围色配置 (根据形状改变)
    val glowColor = remember(currentShape) {
        when(currentShape) {
            GalaxyShape.HEART -> Color(0xFFFF2D55) // 霓虹红
            GalaxyShape.DNA -> Color(0xFF34C759)   // 激光绿
            GalaxyShape.SPIRAL -> Color(0xFF5856D6)// 深邃紫
            else -> Color(0xFF00D2FF)               // 赛博蓝
        }
    }

    // A. 初始化数据 (星空与相册加载)
    LaunchedEffect(Unit) {
        val rng = Random(System.nanoTime())
        // 生成随机星空背景
        stars = (0 until 180).map {
            StarParticle(rng.nextFloat(), rng.nextFloat(), 1f + rng.nextFloat() * 2.5f, 0.2f + rng.nextFloat() * 0.7f, rng.nextFloat() * 10f)
        }

        // 检查相册权限
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            val loaded = loadGalleryImages(context, 120) // 加载120张照片
            particles = loaded.mapIndexed { i, (id, bmp) ->
                ImageParticle(id, bmp?.asImageBitmap(), i, abs(id.hashCode()))
            }
        }
    }

    // B. 主动画循环 (渲染帧)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos -> animationTime = nanos / 1_000_000_000.0 }
        }
    }

    // C. 形状自动切换逻辑 (每 8 秒)
    LaunchedEffect(Unit) {
        while(true) {
            delay(8000)
            if (selectedParticle == null) {
                currentShape = currentShape.next()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF010103))) {
        // D. 核心画布 (Galaxy Canvas)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    // 处理拖拽旋转和双指缩放
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 3.5f)
                        scope.launch {
                            dragX.snapTo(dragX.value + pan.x)
                            dragY.snapTo(dragY.value + pan.y)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { currentShape = currentShape.next() },
                        onTap = { offset ->
                            if (canvasSize.width == 0f) return@detectTapGestures

                            // --- 高级命中检测 ---
                            val w = canvasSize.width
                            val h = canvasSize.height
                            val rotY = animationTime * 0.12 + dragX.value / 180.0
                            val rotX = animationTime * 0.08 + dragY.value / 180.0

                            var nearestParticle: ImageParticle? = null
                            var minDist = Float.MAX_VALUE

                            // 遍历粒子，找到点击位置最近的一个
                            particles.forEach { p ->
                                val rawPos = positionFor(p, currentShape, particles.size, animationTime)
                                val rotated = rotate(rawPos, rotX, rotY)
                                val proj = project(rotated, w, h, zoomScale)

                                val dx = proj.x - offset.x
                                val dy = proj.y - offset.y
                                val dist = sqrt(dx*dx + dy*dy)

                                // 限制点击范围，防止误触
                                val hitRadius = (90f + 70f * proj.scale) * 0.6f
                                if (dist < hitRadius && dist < minDist) {
                                    minDist = dist
                                    nearestParticle = p
                                }
                            }
                            selectedParticle = nearestParticle
                        }
                    )
                }
        ) {
            val w = size.width; val h = size.height

            // 1. 绘制视差背景 (Parallax Space)
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color(0xFF0D0D1E),
                    1f to Color(0xFF010103),
                    center = Offset(w/2 + dragX.value * 0.03f, h/2 + dragY.value * 0.03f),
                    radius = w * 1.5f
                )
            )

            // 2. 绘制星空背景 (Stars)
            stars.forEach { star ->
                val twinkle = 0.3f + 0.7f * sin(animationTime.toFloat() * 1.5f + star.twinkleOffset).absoluteValue
                drawCircle(
                    color = Color.White.copy(alpha = star.opacity * twinkle),
                    radius = star.size / 2,
                    center = Offset(star.x * w, star.y * h)
                )
            }

            // 3. 粒子渲染引擎 (带深度排序 - Z-Sorting)
            if (particles.isNotEmpty()) {
                val rotY = animationTime * 0.12 + dragX.value / 180.0
                val rotX = animationTime * 0.08 + dragY.value / 180.0

                // 核心：根据 z 轴坐标排序，解决前后遮挡穿插问题
                particles.map { p ->
                    val rawPos = positionFor(p, currentShape, particles.size, animationTime)
                    p to rotate(rawPos, rotX, rotY)
                }
                    .sortedByDescending { it.second.z } // z 轴越小离镜头越近，最后画
                    .forEach { (p, rotated) ->
                        val proj = project(rotated, w, h, zoomScale)

                        // 动态大小计算 (远处小，近处大)
                        val baseSize = 90f
                        val pSize = (baseSize + 70f * proj.scale)

                        // 深度雾化效果 (远处粒子透明度降低)
                        val alpha = (0.22f + (proj.scale * 0.78f)).coerceIn(0f, 1f)

                        drawGalaxyParticle(p, proj.x, proj.y, pSize, alpha, glowColor, currentShape)
                    }
            }
        }

        // E. UI 叠加层 (HUD)
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 标题 (右上)
            Column(modifier = Modifier.align(Alignment.TopEnd).padding(20.dp), horizontalAlignment = Alignment.End) {
                Text(
                    text = currentShape.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
                Box(Modifier.height(3.dp).width(40.dp).background(glowColor))
            }

            // 底部指示器
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GalaxyShape.entries.forEach { shape ->
                    val active = shape == currentShape
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (active) 26.dp else 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) Color.White else Color.White.copy(0.3f))
                    )
                }
            }
        }

        // F. 大图查看器 (高级 Hero 动画转场)
        AnimatedVisibility(
            visible = selectedParticle != null,
            // 从点击位置“生长”出来
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = tween(350)),
            // 缩小返回宇宙
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.9f, animationSpec = tween(250))
        ) {
            val p = selectedParticle ?: return@AnimatedVisibility

            // 遮罩层 (让宇宙变暗，突出照片)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.9f))
                    .pointerInput(Unit) { detectTapGestures { selectedParticle = null } }, // 点击背景缩小
                contentAlignment = Alignment.Center
            ) {
                if (p.bitmap != null) {
                    Image(
                        bitmap = p.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit, // 完整显示照片
                        modifier = Modifier
                            .fillMaxWidth(0.92f) // 占满屏幕宽度的92%
                            .fillMaxHeight(0.65f)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(24.dp))
                            .pointerInput(Unit) { detectTapGestures { selectedParticle = null } } // 点击照片也缩小
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────
// 6. 数据读取辅助 (MediaStore Loader)
// ─────────────────────────────────────────

private suspend fun loadGalleryImages(context: Context, max: Int): List<Pair<Int, android.graphics.Bitmap?>> = withContext(Dispatchers.IO) {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val ids = mutableListOf<Int>()
    // 按加入时间倒序读取
    context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
        val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && ids.size < max) ids.add(cursor.getInt(col))
    }
    ids.map { id ->
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toLong())
        val bmp = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 }) // 缩减采样率提升性能
            }
        } catch (e: Exception) { null }
        id to bmp
    }
}