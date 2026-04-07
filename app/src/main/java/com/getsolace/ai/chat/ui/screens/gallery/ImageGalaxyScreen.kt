package com.getsolace.ai.chat.ui.screens.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Size as CameraSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.getsolace.ai.chat.ui.components.HandGestureController
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────
// 1. 数据模型与枚举
// ─────────────────────────────────────────

private data class Vec3(val x: Double, val y: Double, val z: Double)
private data class Projected(val x: Float, val y: Float, val scale: Float, val zIndex: Double)
private data class StarParticle(val x: Float, val y: Float, val size: Float, val opacity: Float, val twinkleOffset: Float)

data class ImageParticle(val id: Int, val bitmap: ImageBitmap?, val index: Int, val seed: Int)

enum class GalaxyShape(val displayName: String) {
    SPHERE("SPHERE"), HEART("HEART"), SPIRAL("SPIRAL"), DNA("DNA"),
    VORTEX("VORTEX"), COSMOS("COSMOS"), GRID("GALLERY");
    fun next(): GalaxyShape = entries[(ordinal + 1) % entries.size]
}

// ─────────────────────────────────────────
// 2. 3D 引擎计算
// ─────────────────────────────────────────

private fun rotate(p: Vec3, rotX: Double, rotY: Double): Vec3 {
    val sY = sin(rotY); val cY = cos(rotY)
    val sX = sin(rotX); val cX = cos(rotX)
    val q = Vec3(p.x * cY + p.z * sY, p.y, -p.x * sY + p.z * cY)
    return Vec3(q.x, q.y * cX - q.z * sX, q.y * sX + q.z * cX)
}

private fun project(p: Vec3, w: Float, h: Float, zoom: Float): Projected {
    val fov = 850.0
    val cameraZ = 450.0
    val factor = fov / (fov + p.z + cameraZ)
    val scale = (factor * zoom).toFloat()
    return Projected(
        x = (p.x * scale + w / 2f).toFloat(),
        y = (p.y * scale + h / 2f).toFloat(),
        scale = scale,
        zIndex = p.z
    )
}

private fun positionFor(p: ImageParticle, shape: GalaxyShape, total: Int, time: Double, w: Float, h: Float): Vec3 {
    val t = p.index.toDouble() / max(total - 1, 1)
    return when (shape) {
        GalaxyShape.SPHERE -> {
            val phi = acos(1.0 - 2.0 * (p.index + 0.5) / max(total, 1))
            val theta = p.index * PI * (3.0 - sqrt(5.0))
            Vec3(420.0 * sin(phi) * cos(theta), 420.0 * sin(phi) * sin(theta), 420.0 * cos(phi))
        }
        GalaxyShape.HEART -> {
            val a = t * 2.0 * PI
            val s = sin(a)
            val r = 18.0
            val x = r * (16.0 * s * s * s)
            val y = -r * (13.0 * cos(a) - 5.0 * cos(2 * a) - 2.0 * cos(3 * a) - cos(4 * a))
            Vec3(x * 1.2, y * 1.2, 0.0)
        }
        GalaxyShape.SPIRAL -> {
            val angle = t * 6.0 * PI + time * 0.15
            val r = 50.0 + 250.0 * t
            Vec3(r * cos(angle), (t - 0.5) * 520.0, r * sin(angle))
        }
        GalaxyShape.DNA -> {
            val strandOffset = if (p.index % 2 == 0) 0.0 else PI
            val angle = t * 4.0 * PI + strandOffset + time * 0.1
            Vec3(150.0 * cos(angle), (t - 0.5) * 600.0, 150.0 * sin(angle))
        }
        GalaxyShape.VORTEX -> {
            val angle = t * 8.0 * PI + time * 0.5
            val r = 20.0 + 350.0 * t
            Vec3(r * cos(angle), r * sin(angle), -500.0 + t * 1000.0)
        }
        GalaxyShape.COSMOS -> {
            val random = Random(p.seed)
            Vec3((random.nextFloat() - 0.5) * w * 1.5, (random.nextFloat() - 0.5) * h * 1.5, (random.nextFloat() - 0.5) * 600.0)
        }
        GalaxyShape.GRID -> {
            val cols = 5; val rows = 8
            val col = p.index % cols; val row = (p.index / cols) % rows; val layer = p.index / (cols * rows)
            Vec3((col - (cols - 1) / 2.0) * (w / (cols + 1)) * 0.8, (row - (rows - 1) / 2.0) * (h / (rows + 1)) * 0.8, (layer - 1.0) * 200.0)
        }
    }
}

// ─────────────────────────────────────────
// 3. 优化后的核心渲染函数
// ─────────────────────────────────────────

private fun heartPath(cx: Float, cy: Float, size: Float): Path = Path().apply {
    val steps = 45
    for (i in 0..steps) {
        val t = i.toDouble() * 2.0 * PI / steps
        val px = (sin(t).let { it * it * it } * size).toFloat()
        val py = (-(13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t)) / 16.0 * size * 0.9).toFloat()
        if (i == 0) moveTo(cx + px, cy + py) else lineTo(cx + px, cy + py)
    }
    close()
}

/**
 * 带有景深感知(DOF)的粒子渲染
 */
private fun DrawScope.drawGalaxyParticle(
    p: ImageParticle, cx: Float, cy: Float, size: Float,
    alpha: Float, glowColor: Color, shape: GalaxyShape,
    zDepth: Float // 0.0 (最远) -> 1.0 (最近)
) {
    val half = size / 2f
    val path = if (shape == GalaxyShape.HEART) heartPath(cx, cy, half)
    else Path().apply { addOval(Rect(cx - half, cy - half, cx + half, cy + half)) }

    // 核心优化：只有距离镜头较近(zDepth > 0.6)的粒子才绘制清晰的边缘和发光
    if (zDepth > 0.6f) {
        val focusEffect = ((zDepth - 0.6f) * 2.5f).coerceIn(0f, 1f)
        drawPath(path, glowColor.copy(alpha = 0.4f * alpha * focusEffect), style = Stroke(width = 6f))
        drawPath(path, Color.White.copy(alpha = 0.5f * alpha * focusEffect), style = Stroke(width = 1.2f))
    }

    // 绘制底部遮罩，避免重叠处的透明度叠加得过于刺眼
    drawPath(path, color = Color.Black.copy(alpha = alpha))

    clipPath(path) {
        if (p.bitmap != null) {
            // 核心优化：模拟光影压暗。远处的粒子亮度降低
            val brightness = (0.35f + 0.65f * zDepth).coerceIn(0f, 1f)
            drawImage(
                p.bitmap,
                dstOffset = IntOffset((cx - half).toInt(), (cy - half).toInt()),
                dstSize = IntSize(size.toInt(), size.toInt()),
                alpha = alpha,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 1f - brightness), BlendMode.SrcOver)
            )
        } else {
            drawRect(glowColor.copy(0.2f), alpha = alpha)
        }
    }

    // 远处的边框线弱化
    drawPath(path, Color.White.copy(alpha = 0.15f * alpha * zDepth), style = Stroke(width = 1.0f))
}

// ─────────────────────────────────────────
// 4. UI 界面
// ─────────────────────────────────────────

@Composable
fun ImageGalaxyScreen(navController: androidx.navigation.NavController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var particles by remember { mutableStateOf(emptyList<ImageParticle>()) }
    var stars by remember { mutableStateOf(emptyList<StarParticle>()) }
    var animationTime by remember { mutableDoubleStateOf(0.0) }
    var currentShape by remember { mutableStateOf(GalaxyShape.SPHERE) }
    var selectedParticle by remember { mutableStateOf<ImageParticle?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cursorPos by remember { mutableStateOf<Offset?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val lifecycleOwner = context as androidx.lifecycle.LifecycleOwner

    val glowColor = remember(currentShape) {
        when(currentShape) {
            GalaxyShape.HEART -> Color(0xFFFF2D55)
            GalaxyShape.DNA -> Color(0xFF34C759)
            GalaxyShape.VORTEX -> Color(0xFFFF9500)
            GalaxyShape.SPIRAL -> Color(0xFF5856D6)
            else -> Color(0xFF00D2FF)
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    val gestureController = remember {
        HandGestureController(
            context = context,
            onZoom = { factor -> zoomScale = (zoomScale * factor).coerceIn(0.5f, 3.5f) },
            onTap = { nx, ny ->
                if (canvasSize.width > 0f) {
                    val tapOffset = Offset(nx * canvasSize.width, ny * canvasSize.height)
                    val rotY = animationTime * 0.12 + dragX.value / 180.0
                    val rotX = animationTime * 0.08 + dragY.value / 180.0
                    var nearest: ImageParticle? = null
                    var minDist = Float.MAX_VALUE
                    particles.forEach { p ->
                        val pos = rotate(positionFor(p, currentShape, particles.size, animationTime, canvasSize.width, canvasSize.height), rotX, rotY)
                        val proj = project(pos, canvasSize.width, canvasSize.height, zoomScale)
                        val dist = sqrt((proj.x - tapOffset.x).pow(2) + (proj.y - tapOffset.y).pow(2))
                        if (dist < 100f * proj.scale && dist < minDist) { minDist = dist; nearest = p }
                    }
                    selectedParticle = nearest
                }
            },
            onCursor = { nx, ny ->
                cursorPos = if (nx != null && ny != null && canvasSize.width > 0f)
                    Offset(nx * canvasSize.width, ny * canvasSize.height)
                else null
            }
        )
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        withContext(Dispatchers.IO) { gestureController.init() }
        @Suppress("DEPRECATION")
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(CameraSize(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(cameraExecutor, gestureController::processFrame) }
        try {
            val provider = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        } catch (e: Exception) {
            android.util.Log.e("GalaxyScreen", "CameraX Error: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 先停止相机 executor，等待已入队的帧处理完毕，再关闭 landmarker
            // 避免相机线程在 landmarker 已销毁后仍调用 detectAsync → SIGSEGV
            cameraExecutor.shutdown()
            runCatching { cameraExecutor.awaitTermination(300, TimeUnit.MILLISECONDS) }
            gestureController.close()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraPermLauncher.launch(Manifest.permission.CAMERA)
        val rng = Random(System.nanoTime())
        stars = (0 until 180).map { StarParticle(rng.nextFloat(), rng.nextFloat(), 1f + rng.nextFloat() * 2.5f, 0.2f + rng.nextFloat() * 0.7f, rng.nextFloat() * 10f) }
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            val loaded = loadGalleryImages(context, 40)
            particles = loaded.mapIndexed { i, (id, bmp) -> ImageParticle(id, bmp?.asImageBitmap(), i, abs(id.hashCode())) }
        }
    }

    LaunchedEffect(Unit) {
        while (true) { withFrameNanos { nanos -> animationTime = nanos / 1_000_000_000.0 } }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF010103))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 3.5f)
                        scope.launch { dragX.snapTo(dragX.value + pan.x); dragY.snapTo(dragY.value + pan.y) }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { currentShape = currentShape.next() },
                        onTap = { offset ->
                            if (canvasSize.width == 0f) return@detectTapGestures
                            val rotY = animationTime * 0.12 + dragX.value / 180.0
                            val rotX = animationTime * 0.08 + dragY.value / 180.0
                            var nearest: ImageParticle? = null
                            var minDist = Float.MAX_VALUE
                            particles.forEach { p ->
                                val pos = rotate(positionFor(p, currentShape, particles.size, animationTime, canvasSize.width, canvasSize.height), rotX, rotY)
                                val proj = project(pos, canvasSize.width, canvasSize.height, zoomScale)
                                val dist = sqrt((proj.x - offset.x).pow(2) + (proj.y - offset.y).pow(2))
                                if (dist < (100f * proj.scale) && dist < minDist) { minDist = dist; nearest = p }
                            }
                            selectedParticle = nearest
                        }
                    )
                }
        ) {
            // 背景渐变
            drawRect(Color(0xFF010103))
            drawRect(Brush.radialGradient(0f to Color(0xFF0D0D1E), 1f to Color(0xFF010103), center = Offset(size.width/2 + dragX.value * 0.05f, size.height/2 + dragY.value * 0.05f), radius = size.width * 1.5f))
            stars.forEach { star ->
                val twinkle = 0.3f + 0.7f * sin(animationTime.toFloat() * 1.5f + star.twinkleOffset).absoluteValue
                drawCircle(Color.White.copy(alpha = star.opacity * twinkle), radius = star.size / 2, center = Offset(star.x * size.width, star.y * size.height))
            }

            if (particles.isNotEmpty()) {
                val rotY = animationTime * 0.12 + dragX.value / 180.0
                val rotX = animationTime * 0.08 + dragY.value / 180.0

                // 核心优化：画家算法。由远及近排序绘制。
                val sortedList = particles.map { p ->
                    p to rotate(positionFor(p, currentShape, particles.size, animationTime, size.width, size.height), rotX, rotY)
                }.sortedBy { it.second.z } // z 轴越小越远

                sortedList.forEach { (p, rotated) ->
                    val proj = project(rotated, size.width, size.height, zoomScale)

                    // 核心优化：计算深度归一化值 (0.0=远, 1.0=近)
                    // 映射范围取决于球体半径(420)，我们大致做一个归一化
                    val zNormalized = ((rotated.z + 420.0) / 840.0).coerceIn(0.0, 1.0).toFloat()

                    // 核心优化：非线性透明度。远处的粒子平方倍淡出。
                    val alpha = (zNormalized * zNormalized).coerceIn(0.08f, 1f)

                    val baseSize = 48f + 42f * proj.scale
                    val pSize = baseSize * (0.65f + 0.35f * zNormalized) * (1f + 0.04f * sin(animationTime * 2.5 + p.seed % 10).toFloat())

                    drawGalaxyParticle(p, proj.x, proj.y, pSize, alpha, glowColor, currentShape, zNormalized)
                }
            }

            cursorPos?.let { pos ->
                drawCircle(glowColor.copy(alpha = 0.6f), radius = 22f, center = pos, style = Stroke(width = 2.5f))
                drawCircle(Color.White.copy(alpha = 0.35f), radius = 8f, center = pos)
            }
        }

        // 顶层UI控件
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f))
                    .clickable { navController?.popBackStack("home", inclusive = false) }.padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.align(Alignment.TopEnd).padding(20.dp), horizontalAlignment = Alignment.End) {
                Text(text = currentShape.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
                Box(Modifier.height(2.dp).width(30.dp).background(glowColor))
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GalaxyShape.entries.forEach { Box(Modifier.height(4.dp).width(if (it == currentShape) 20.dp else 6.dp).clip(RoundedCornerShape(2.dp)).background(if (it == currentShape) Color.White else Color.White.copy(0.3f))) }
            }
        }

        // 选中图片预览
        AnimatedVisibility(visible = selectedParticle != null, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            val p = selectedParticle ?: return@AnimatedVisibility
            Box(Modifier.fillMaxSize().blur(15.dp).background(Color.Black.copy(0.7f)).pointerInput(Unit) { detectTapGestures { selectedParticle = null } })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                p.bitmap?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f).clip(RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(20.dp)).pointerInput(Unit) { detectTapGestures { selectedParticle = null } }) }
            }
        }
    }
}

private suspend fun loadGalleryImages(context: Context, max: Int): List<Pair<Int, android.graphics.Bitmap?>> = withContext(Dispatchers.IO) {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val ids = mutableListOf<Int>()
    context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && ids.size < max) ids.add(cursor.getInt(idCol))
    }
    ids.map { id ->
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toLong())
        val bmp = try { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) } } catch (e: Exception) { null }
        id to bmp
    }
}