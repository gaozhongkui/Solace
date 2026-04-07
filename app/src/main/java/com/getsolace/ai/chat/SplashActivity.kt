package com.getsolace.ai.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.getsolace.ai.chat.ui.theme.SolaceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            SolaceTheme {
                SplashScreen(
                    onFinished = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                        // 淡入过渡，消除跳转闪烁
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                )
            }
        }
    }
}

// ─── 颜色 ────────────────────────────────────────────────────────────────────

private val DeepBg  = Color(0xFF0A0B14)
private val BgGlow  = Color(0xFF1E0D35)
private val Violet  = Color(0xFF9B7AFF)
private val Teal    = Color(0xFF00D4B4)

// ─── Splash Composable ───────────────────────────────────────────────────────

@Composable
fun SplashScreen(onFinished: () -> Unit) {

    val logoScale   = remember { Animatable(0.4f) }
    val logoAlpha   = remember { Animatable(0f) }
    val ringScale   = remember { Animatable(0.6f) }
    val textAlpha   = remember { Animatable(0f) }
    val tagAlpha    = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }

    val infinite = rememberInfiniteTransition(label = "aura")
    val ringRotation by infinite.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label         = "rotation"
    )
    val pulse by infinite.animateFloat(
        initialValue  = 0.97f,
        targetValue   = 1.03f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        // 图标弹入
        listOf(
            launch { logoScale.animateTo(1f, tween(600, easing = OvershootEasing)) },
            launch { logoAlpha.animateTo(1f, tween(400)) },
            launch { ringScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        ).forEach { it.join() }

        delay(100)
        launch { textAlpha.animateTo(1f, tween(500)) }
        delay(200)
        launch { tagAlpha.animateTo(1f, tween(500)) }

        // 停留
        delay(1600)

        // 整屏淡出
        screenAlpha.animateTo(0f, tween(400))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBg)
            .drawBehind {
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(BgGlow, Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.65f
                    ),
                    center = center,
                    radius = size.minDimension * 0.65f,
                    alpha  = logoAlpha.value * screenAlpha.value
                )
            }
    ) {
        val alpha = screenAlpha.value

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── 图标 ──────────────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .size(150.dp)
                    .scale(logoScale.value * pulse),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawAuraIcon(
                                rotation  = ringRotation,
                                ringScale = ringScale.value,
                                alpha     = logoAlpha.value * alpha
                            )
                        }
                )
            }

            Spacer(Modifier.height(36.dp))

            // ── 应用名 ────────────────────────────────────────────────────────
            Text(
                text          = "光晕AI",
                fontSize      = 38.sp,
                fontWeight    = FontWeight.Bold,
                color         = Color.White.copy(alpha = textAlpha.value * alpha),
                letterSpacing = 3.sp,
                textAlign     = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            // ── Slogan ────────────────────────────────────────────────────────
            Text(
                text          = "让每一个灵感，都有光",
                fontSize      = 14.sp,
                color         = Teal.copy(alpha = tagAlpha.value * alpha * 0.85f),
                letterSpacing = 1.sp,
                textAlign     = TextAlign.Center
            )
        }

        // ── 底部 ──────────────────────────────────────────────────────────────
        Text(
            text          = "Powered by AI",
            fontSize      = 11.sp,
            color         = Color.White.copy(alpha = tagAlpha.value * alpha * 0.3f),
            letterSpacing = 1.sp,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        )
    }
}

// ─── 图标绘制 ────────────────────────────────────────────────────────────────

private fun DrawScope.drawAuraIcon(rotation: Float, ringScale: Float, alpha: Float) {
    val cx   = size.width  / 2f
    val cy   = size.height / 2f
    val base = size.minDimension / 2f

    // 外环光晕叠加
    repeat(3) { i ->
        drawCircle(
            color  = Teal.copy(alpha = alpha * 0.05f * (3 - i)),
            center = Offset(cx, cy),
            radius = base * 0.78f * ringScale + (i + 1) * 5f,
            style  = Stroke(width = 10f + i * 3f)
        )
    }
    // 外环主体
    drawCircle(
        color  = Teal.copy(alpha = alpha * 0.9f),
        center = Offset(cx, cy),
        radius = base * 0.78f * ringScale,
        style  = Stroke(width = 5f)
    )
    // 高光弧（旋转）
    val r = base * 0.78f * ringScale
    drawArc(
        color      = Color.White.copy(alpha = alpha * 0.35f),
        startAngle = rotation + 200f,
        sweepAngle = 60f,
        useCenter  = false,
        topLeft    = Offset(cx - r, cy - r),
        size       = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        style      = Stroke(width = 2f)
    )

    // 内圆底色 + 内环
    drawCircle(color = BgGlow.copy(alpha = alpha), center = Offset(cx, cy), radius = base * 0.46f)
    repeat(2) { i ->
        drawCircle(
            color  = Violet.copy(alpha = alpha * 0.07f * (2 - i)),
            center = Offset(cx, cy),
            radius = base * 0.46f + (i + 1) * 3f,
            style  = Stroke(width = 7f + i * 2f)
        )
    }
    drawCircle(
        color  = Violet.copy(alpha = alpha * 0.9f),
        center = Offset(cx, cy),
        radius = base * 0.46f,
        style  = Stroke(width = 4f)
    )

    // 4 粒菱形（两环间，缓慢随旋转漂移）
    val midR = base * 0.62f * ringScale
    listOf(45f, 135f, 225f, 315f).forEachIndexed { i, deg ->
        val a    = Math.toRadians((deg + rotation * 0.3f).toDouble())
        val px   = cx + midR * cos(a).toFloat()
        val py   = cy + midR * sin(a).toFloat()
        val half = base * 0.07f
        val col  = if (i % 2 == 0) Teal else Violet
        drawPath(
            path  = Path().apply {
                moveTo(px, py - half); lineTo(px + half, py)
                lineTo(px, py + half); lineTo(px - half, py); close()
            },
            color = col.copy(alpha = alpha)
        )
    }

    // 4 芒星
    val outerR = base * 0.30f
    val innerR = base * 0.10f
    drawPath(
        path  = Path().apply {
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45 - 90).toDouble())
                val rr = if (i % 2 == 0) outerR else innerR
                val x = cx + rr * cos(a).toFloat()
                val y = cy + rr * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        },
        color = Color.White.copy(alpha = alpha)
    )
    // 中心亮核
    drawCircle(Color.White.copy(alpha = alpha), center = Offset(cx, cy), radius = base * 0.09f)

    // 右上角小星
    val sx = cx + base * 0.68f
    val sy = cy - base * 0.55f
    drawPath(
        path  = Path().apply {
            for (i in 0 until 8) {
                val a  = Math.toRadians((i * 45 - 90).toDouble())
                val rr = if (i % 2 == 0) base * 0.10f else base * 0.04f
                val x = sx + rr * cos(a).toFloat()
                val y = sy + rr * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        },
        color = Teal.copy(alpha = alpha * 0.7f)
    )
}

private val OvershootEasing = Easing { t ->
    val s = 3.5f
    ((s + 1) * (t - 1).let { it * it * it } + s * (t - 1).let { it * it } + 1)
        .coerceIn(-0.5f, 1.5f)
}
