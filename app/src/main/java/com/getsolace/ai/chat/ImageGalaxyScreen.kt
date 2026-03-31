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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────
// Shape Mode
// ─────────────────────────────────────────

enum class GalaxyShape(val displayName: String, val icon: String) {
    SPHERE("SPHERE", "○"),
    HEART("HEART", "♥"),
    SPIRAL("SPIRAL", "◎"),
    DNA("DNA", "⚕"),
    SCATTERED("COSMOS", "✦");

    fun next(): GalaxyShape = entries[(ordinal + 1) % entries.size]
}

// ─────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────

private data class Vec3(val x: Double, val y: Double, val z: Double)

private data class StarParticle(
    val x: Float, val y: Float,
    val size: Float, val opacity: Float,
    val twinkleOffset: Float
)

data class ImageParticle(
    val id: Int,
    val bitmap: ImageBitmap?,
    val index: Int,
    val seed: Int
)

// ─────────────────────────────────────────
// 3D Math  (mirrors the Swift SIMD3 logic)
// ─────────────────────────────────────────

private fun spherePoint(index: Int, total: Int, radius: Double = 155.0): Vec3 {
    val phi   = acos(1.0 - 2.0 * index / max(total, 1))
    val theta = index * PI * (3.0 - sqrt(5.0))
    return Vec3(
        radius * sin(phi) * cos(theta),
        radius * sin(phi) * sin(theta),
        radius * cos(phi)
    )
}

private fun heartPoint(t: Double, radius: Double = 125.0): Vec3 {
    val a = t * 2.0 * PI
    val s = sin(a); val s3 = s * s * s
    val x = radius * 0.85 * s3
    val y = -radius * 0.78 * (13.0 * cos(a) - 5.0 * cos(2 * a) - 2.0 * cos(3 * a) - cos(4 * a)) / 16.0
    return Vec3(x, y, 0.0)
}

private fun spiralPoint(index: Int, total: Int, time: Double): Vec3 {
    val t     = index.toDouble() / max(total - 1, 1)
    val angle = t * 5.0 * PI + time * 0.08
    val r     = 40.0 + 130.0 * t
    return Vec3(r * cos(angle), (t - 0.5) * 320.0, r * sin(angle))
}

private fun dnaPoint(index: Int, total: Int, time: Double): Vec3 {
    val t      = index.toDouble() / max(total - 1, 1)
    val strand = if (index % 2 == 0) 0.0 else PI
    val angle  = t * 5.0 * PI + strand + time * 0.06
    val r      = 80.0
    return Vec3(r * cos(angle), (t - 0.5) * 320.0, r * sin(angle))
}

private fun scatteredPoint(seed: Int, width: Float, height: Float): Vec3 {
    val x = (seed % 1000) / 1000.0 * width - width / 2.0
    val y = ((seed / 1000) % 1000) / 1000.0 * height - height / 2.0
    val z = ((seed / 1_000_000) % 1000) / 1000.0 * 200.0 - 100.0
    return Vec3(x, y, z)
}

private fun ringPoint(index: Int, total: Int): Vec3 {
    val angle = 2.0 * PI * index / max(total, 1)
    val r     = 160.0
    return Vec3(r * sin(angle), 0.0, r * cos(angle))
}

private fun rotate(p: Vec3, rotX: Double, rotY: Double): Vec3 {
    val sY = sin(rotY); val cY = cos(rotY)
    val sX = sin(rotX); val cX = cos(rotX)
    val q  = Vec3(p.x * cY + p.z * sY, p.y, -p.x * sY + p.z * cY)
    return Vec3(q.x, q.y * cX - q.z * sX, q.y * sX + q.z * cX)
}

private data class Projected(val x: Float, val y: Float, val scale: Float)

private fun project(p: Vec3, w: Float, h: Float): Projected {
    val fov = 700.0
    val s   = fov / (fov + p.z + 100.0)
    return Projected((p.x * s + w / 2.0).toFloat(), (p.y * s + h / 2.0).toFloat(), s.toFloat())
}

private fun positionFor(
    p: ImageParticle, shape: GalaxyShape,
    total: Int, time: Double,
    isRingMode: Boolean, w: Float, h: Float
): Vec3 {
    if (isRingMode) return ringPoint(p.index, total)
    val t = p.index.toDouble() / max(total - 1, 1)
    return when (shape) {
        GalaxyShape.SPHERE    -> spherePoint(p.index, total)
        GalaxyShape.HEART     -> heartPoint(t)
        GalaxyShape.SPIRAL    -> spiralPoint(p.index, total, time)
        GalaxyShape.DNA       -> dnaPoint(p.index, total, time)
        GalaxyShape.SCATTERED -> scatteredPoint(p.seed, w, h)
    }
}

// ─────────────────────────────────────────
// Clip Paths
// ─────────────────────────────────────────

private fun circleClipPath(cx: Float, cy: Float, r: Float) = Path().apply {
    addOval(Rect(cx - r, cy - r, cx + r, cy + r))
}

private fun heartClipPath(cx: Float, cy: Float, halfSize: Float): Path {
    return Path().apply {
        val steps = 60
        for (i in 0..steps) {
            val t  = i.toDouble() * 2.0 * PI / steps
            val s  = sin(t); val s3 = s * s * s
            val px = (s3 * halfSize).toFloat()
            val py = (-(13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t)) / 16.0 * halfSize * 0.9).toFloat()
            if (i == 0) moveTo(cx + px, cy + py) else lineTo(cx + px, cy + py)
        }
        close()
    }
}

private fun roundRectClipPath(cx: Float, cy: Float, size: Float, r: Float = 7f) = Path().apply {
    addRoundRect(RoundRect(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f, r, r))
}

// ─────────────────────────────────────────
// Particle Rendering  (Canvas DrawScope)
// ─────────────────────────────────────────

private fun DrawScope.drawParticle(
    p: ImageParticle, shape: GalaxyShape,
    cx: Float, cy: Float, size: Float,
    alpha: Float, glowColor: Color
) {
    val half = size / 2f
    val clip = when {
        shape == GalaxyShape.HEART -> heartClipPath(cx, cy, half * 0.9f)
        shape == GalaxyShape.SCATTERED -> when (p.index % 3) {
            0    -> circleClipPath(cx, cy, half)
            1    -> roundRectClipPath(cx, cy, size)
            else -> heartClipPath(cx, cy, half * 0.9f)
        }
        else -> circleClipPath(cx, cy, half)
    }

    // Soft glow halo
    drawCircle(
        color = glowColor.copy(alpha = 0.30f * alpha),
        radius = half + 5f,
        center = Offset(cx, cy),
        blendMode = BlendMode.Screen
    )

    val bmp = p.bitmap
    if (bmp != null) {
        clipPath(clip) {
            drawImage(
                image  = bmp,
                dstOffset = IntOffset((cx - half).toInt(), (cy - half).toInt()),
                dstSize   = IntSize(size.toInt(), size.toInt()),
                alpha  = alpha
            )
        }
    } else {
        // Placeholder gradient circle
        clipPath(clip) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF1D4ED8)),
                    center = Offset(cx, cy), radius = half
                ),
                radius = half, center = Offset(cx, cy),
                alpha = 0.5f * alpha
            )
        }
    }

    // Thin border overlay
    drawPath(clip, color = Color.White.copy(alpha = 0.22f * alpha), style = Stroke(width = 1f))
}

// ─────────────────────────────────────────
// Photo Loading (MediaStore)
// ─────────────────────────────────────────

private suspend fun loadGalleryImages(context: Context, max: Int): List<Pair<Int, android.graphics.Bitmap?>> =
    withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder  = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val ids = mutableListOf<Int>()

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && ids.size < max) ids.add(cursor.getInt(col))
        }

        ids.map { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toLong())
            val bmp = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null,
                        BitmapFactory.Options().apply { inSampleSize = 4 })
                }
            } catch (_: Exception) { null }
            id to bmp
        }
    }

// ─────────────────────────────────────────
// Main Composable
// ─────────────────────────────────────────

@Composable
fun ImageGalaxyScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var particles         by remember { mutableStateOf(emptyList<ImageParticle>()) }
    var stars             by remember { mutableStateOf(emptyList<StarParticle>()) }
    var animationTime     by remember { mutableDoubleStateOf(0.0) }
    var currentShape      by remember { mutableStateOf(GalaxyShape.SPHERE) }
    var selectedParticle  by remember { mutableStateOf<ImageParticle?>(null) }
    var lastInteraction   by remember { mutableDoubleStateOf(0.0) }
    var isTransitioning   by remember { mutableStateOf(false) }
    var canvasSize        by remember { mutableStateOf(Size.Zero) }
    var hasPermission     by remember { mutableStateOf(false) }

    // Spring-damped drag offset (mirrors iOS)
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }

    val isRingMode = particles.isNotEmpty() && particles.size < 10

    val glowColor = remember(currentShape) {
        when (currentShape) {
            GalaxyShape.HEART -> Color(0xFFEC4899)
            GalaxyShape.DNA   -> Color(0xFF22C55E)
            else              -> Color(0xFF22D3EE)
        }
    }

    fun switchShape() {
        if (isTransitioning) return
        isTransitioning = true
        lastInteraction = animationTime
        currentShape = currentShape.next()
        scope.launch { delay(1400); isTransitioning = false }
    }

    // ── Permission ──
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) hasPermission = true }

    LaunchedEffect(Unit) {
        // Stars
        val rng = Random(System.nanoTime())
        stars = (0 until 130).map {
            StarParticle(
                x            = rng.nextFloat(),
                y            = rng.nextFloat(),
                size         = 0.5f + rng.nextFloat() * 2f,
                opacity      = 0.2f + rng.nextFloat() * 0.65f,
                twinkleOffset = rng.nextFloat() * 2f * PI.toFloat()
            )
        }
        // Check permission
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
        } else {
            permLauncher.launch(perm)
        }
    }

    // ── Load Photos ──
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val loaded = loadGalleryImages(context, 200)
        particles = loaded.mapIndexed { i, (id, bmp) ->
            ImageParticle(
                id     = id,
                bitmap = bmp?.asImageBitmap(),
                index  = i,
                seed   = abs(id.hashCode())
            )
        }
        // If no photos, show placeholder particles so the galaxy is visible
        if (particles.isEmpty()) {
            particles = (0 until 20).map { i ->
                ImageParticle(id = i, bitmap = null, index = i, seed = i * 137 + 42)
            }
        }
    }

    // ── Animation Loop ──
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                animationTime = nanos / 1_000_000_000.0
                if (animationTime - lastInteraction > 6.0
                    && !isTransitioning
                    && selectedParticle == null
                    && !isRingMode
                ) switchShape()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050510))
    ) {

        // ── Galaxy Canvas ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sz -> canvasSize = Size(sz.width.toFloat(), sz.height.toFloat()) }
                .pointerInput(Unit) {
                    coroutineScope {
                        // Drag → rotate
                        launch {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        launch {
                                            dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 450f))
                                        }
                                        dragY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 450f))
                                    }
                                }
                            ) { _, delta ->
                                scope.launch {
                                    dragX.snapTo(dragX.value + delta.x)
                                    dragY.snapTo(dragY.value + delta.y)
                                }
                                lastInteraction = animationTime
                            }
                        }
                        // Tap / double-tap
                        launch {
                            detectTapGestures(
                                onDoubleTap = { if (!isRingMode) switchShape() },
                                onTap = { offset ->
                                    if (selectedParticle != null) {
                                        selectedParticle = null
                                        return@detectTapGestures
                                    }
                                    val w = canvasSize.width; val h = canvasSize.height
                                    val total = particles.size
                                    val t = animationTime
                                    val dxVal = dragX.value.toDouble()
                                    val dyVal = dragY.value.toDouble()
                                    val rotY = if (isRingMode) t * 0.5 + dxVal / 55.0
                                               else t * 0.10 + dxVal / 160.0
                                    val rotX = if (isRingMode) 0.38
                                               else t * 0.07 + dyVal / 160.0

                                    var nearest: ImageParticle? = null
                                    var minDist = Float.MAX_VALUE
                                    var nearestSz = 0f

                                    for (p in particles) {
                                        val pos3 = positionFor(p, currentShape, total, t, isRingMode, w, h)
                                        val rot  = rotate(pos3, rotX, rotY)
                                        val proj = project(rot, w, h)
                                        val sz   = if (isRingMode) 90f + 55f * proj.scale
                                                   else 55f + 25f * proj.scale
                                        val dx = proj.x - offset.x; val dy = proj.y - offset.y
                                        val dist = sqrt(dx * dx + dy * dy)
                                        if (dist < minDist) { minDist = dist; nearest = p; nearestSz = sz }
                                    }
                                    if (nearest != null && minDist < nearestSz * 0.7f) {
                                        selectedParticle = nearest
                                        lastInteraction  = animationTime
                                    }
                                }
                            )
                        }
                    }
                }
        ) {
            val w = size.width; val h = size.height
            val time = animationTime.toFloat()

            // ── Deep-space background ──
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFF050530),
                        0.45f to Color(0xFF0D0851),
                        1.00f to Color(0xFF000015)
                    ),
                    start = Offset.Zero, end = Offset(w, h)
                )
            )

            // ── Twinkling stars ──
            for (star in stars) {
                val twinkle = 0.3f + 0.7f * abs(sin(time * 0.9f + star.twinkleOffset))
                drawCircle(
                    color  = Color.White.copy(alpha = star.opacity * twinkle),
                    radius = star.size / 2f,
                    center = Offset(star.x * w, star.y * h)
                )
            }

            // ── Nebula glow ──
            val pulse = 0.12f + 0.05f * sin(time * 0.25f)
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(Color(0xFF9333EA).copy(alpha = pulse), Color.Transparent),
                    center = Offset(w / 2f, h / 2f), radius = w * 0.55f
                ),
                radius = w * 0.55f, center = Offset(w / 2f, h / 2f)
            )
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(Color(0xFF3B82F6).copy(alpha = pulse * 0.7f), Color.Transparent),
                    center = Offset(w * 0.18f, h * 0.25f), radius = w * 0.4f
                ),
                radius = w * 0.4f, center = Offset(w * 0.18f, h * 0.25f)
            )
            if (currentShape == GalaxyShape.HEART) {
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(Color(0xFFEC4899).copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(w / 2f, h / 2f), radius = w * 0.65f
                    ),
                    radius = w * 0.65f, center = Offset(w / 2f, h / 2f)
                )
            }

            // ── Particles ──
            val total = particles.size
            if (total > 0) {
                val dxD = dragX.value.toDouble()
                val dyD = dragY.value.toDouble()
                val rotY = if (isRingMode) animationTime * 0.5 + dxD / 55.0
                           else animationTime * 0.10 + dxD / 160.0
                val rotX = if (isRingMode) 0.38
                           else animationTime * 0.07 + dyD / 160.0

                // Depth-sort: farthest first (z descending → render back-to-front)
                val sorted = particles.sortedByDescending { p ->
                    rotate(positionFor(p, currentShape, total, animationTime, isRingMode, w, h), rotX, rotY).z
                }

                for (p in sorted) {
                    val pos3   = positionFor(p, currentShape, total, animationTime, isRingMode, w, h)
                    val rotated = rotate(pos3, rotX, rotY)
                    val proj   = project(rotated, w, h)
                    val pSize  = if (isRingMode) 90f + 55f * proj.scale else 55f + 25f * proj.scale
                    val alpha  = (0.25f + proj.scale * 0.75f).coerceIn(0f, 1f)
                    drawParticle(p, currentShape, proj.x, proj.y, pSize, alpha, glowColor)
                }
            }
        }

        // ── HUD Overlay ──
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(56.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    val titleColor = if (currentShape == GalaxyShape.HEART)
                        Color(0xFFEC4899) else Color(0xFF22D3EE)
                    Text(
                        text       = "IMAGE GALAXY",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Black,
                        color      = titleColor
                    )
                    if (isRingMode) {
                        Text("◎  RING MODE", fontSize = 11.sp, color = Color.White.copy(0.65f))
                        Text("Drag to spin", fontSize = 10.sp, color = Color.White.copy(0.38f))
                    } else {
                        Text(
                            "${currentShape.icon}  ${currentShape.displayName}",
                            fontSize = 11.sp, color = Color.White.copy(0.65f)
                        )
                        Text("Double-tap to transform", fontSize = 10.sp, color = Color.White.copy(0.38f))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Pager dots
            if (!isRingMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 44.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GalaxyShape.entries.forEach { shape ->
                        val active = shape == currentShape
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (active) 18.dp else 6.dp, 6.dp)
                                .background(
                                    color = if (active) Color.White else Color.White.copy(0.28f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }

        // ── Full-screen Viewer ──
        AnimatedVisibility(
            visible = selectedParticle != null,
            enter   = fadeIn() + scaleIn(initialScale = 0.96f),
            exit    = fadeOut() + scaleOut(targetScale = 0.96f)
        ) {
            val particle = selectedParticle
            if (particle != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.88f))
                        .pointerInput(Unit) {
                            detectTapGestures { selectedParticle = null }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Close button
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                        Text(
                            text     = "✕",
                            fontSize = 28.sp,
                            color    = Color.White.copy(0.8f),
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 32.dp, end = 20.dp)
                                .pointerInput(Unit) { detectTapGestures { selectedParticle = null } }
                        )
                    }

                    if (particle.bitmap != null) {
                        Image(
                            bitmap           = particle.bitmap,
                            contentDescription = null,
                            contentScale     = ContentScale.Fit,
                            modifier         = Modifier
                                .fillMaxWidth(0.85f)
                                .fillMaxHeight(0.62f)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(18.dp))
                        )
                    } else {
                        Box(
                            Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.radialGradient(listOf(Color(0xFF4C1D95), Color(0xFF1E1B4B)))
                                )
                        )
                    }
                }
            }
        }
    }
}
