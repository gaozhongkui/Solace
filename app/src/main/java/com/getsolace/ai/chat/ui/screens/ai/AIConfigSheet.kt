package com.getsolace.ai.chat.ui.screens.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getsolace.ai.chat.data.AIImageStyle
import com.getsolace.ai.chat.data.AIImageStyles
import com.getsolace.ai.chat.data.AspectRatioOption
import com.getsolace.ai.chat.data.AspectRatioOptions
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.AIViewModel

// ─── Config Sheet ─────────────────────────────────────────────────────────────

@Composable
fun AIConfigSheet(vm: AIViewModel, onDismiss: () -> Unit) {
    val selectedStyle by vm.selectedStyle.collectAsStateWithLifecycle()
    val selectedRatio by vm.selectedRatio.collectAsStateWithLifecycle()
    val prompt        by vm.prompt.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .align(Alignment.BottomCenter)
                .clickable { /* absorb */ },
            shape  = RoundedCornerShape(topStart = AppRadius.xl, topEnd = AppRadius.xl),
            colors = CardDefaults.cardColors(containerColor = BgDeepAlt)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── 可滚动内容区 ──────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppSpacing.lg)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(AppSpacing.sm))
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(TextTertiary)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(AppSpacing.lg))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "AI 创作",
                            style    = MaterialTheme.typography.headlineSmall.copy(
                                color = TextPrimary, fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭", tint = TextSecondary)
                        }
                    }

                    Spacer(Modifier.height(AppSpacing.xl))

                    // 提示词输入
                    Text("创作提示词", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
                    Spacer(Modifier.height(AppSpacing.sm))
                    OutlinedTextField(
                        value         = prompt,
                        onValueChange = vm::setPrompt,
                        placeholder   = { Text("描述你想要的画面…", color = TextDisabled) },
                        modifier      = Modifier.fillMaxWidth().height(120.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AccentPrimary,
                            unfocusedBorderColor = DividerColor,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary,
                            cursorColor          = AccentPrimary
                        ),
                        shape = RoundedCornerShape(AppRadius.sm)
                    )

                    Spacer(Modifier.height(AppSpacing.xl))

                    // 风格选择
                    Text("风格选择", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
                    Spacer(Modifier.height(AppSpacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        items(AIImageStyles) { style ->
                            StyleOptionCard(
                                style      = style,
                                isSelected = style.id == selectedStyle.id,
                                onClick    = { vm.selectStyle(style) }
                            )
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.sm))
                }

                // ── 固定底部：画面比例 + 开始生成 ────────────────────────
                HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg)
                        .padding(top = AppSpacing.lg, bottom = AppSpacing.xl)
                ) {
                    // 画面比例
                    Text("画面比例", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
                    Spacer(Modifier.height(AppSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        AspectRatioOptions.forEach { ratio ->
                            AspectRatioButton(
                                option     = ratio,
                                isSelected = ratio.id == selectedRatio.id,
                                onClick    = { vm.selectRatio(ratio) },
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(AppSpacing.lg))

                    Button(
                        onClick  = { onDismiss(); vm.startGeneration() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape    = RoundedCornerShape(AppRadius.lg),
                        colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text("开始生成", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

// ─── Style Option Card ────────────────────────────────────────────────────────

@Composable
fun StyleOptionCard(style: AIImageStyle, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor by animateColorAsState(
        if (isSelected) AccentPrimary else DividerColor, label = "border"
    )
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(AppRadius.sm))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(AppRadius.sm)
            )
            .background(if (isSelected) AccentPrimary.copy(alpha = 0.12f) else CardBg)
            .clickable(onClick = onClick)
            .padding(AppSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StylePreviewBox(styleId = style.id)
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            style.title,
            style = MaterialTheme.typography.labelMedium.copy(
                color      = if (isSelected) AccentPrimary else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            textAlign = TextAlign.Center,
            maxLines  = 1
        )
    }
}

// ─── Aspect Ratio Button ──────────────────────────────────────────────────────

@Composable
fun AspectRatioButton(
    option: AspectRatioOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor   by animateColorAsState(if (isSelected) AccentPrimary else CardBg,      label = "bg")
    val textColor by animateColorAsState(if (isSelected) TextPrimary   else TextSecondary, label = "text")
    Box(
        modifier         = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(bgColor)
            .border(1.dp, if (isSelected) AccentPrimary else DividerColor, RoundedCornerShape(AppRadius.sm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            option.label,
            style = MaterialTheme.typography.labelSmall.copy(
                color      = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

// ─── Style Preview Box ────────────────────────────────────────────────────────

@Composable
fun StylePreviewBox(styleId: String) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(AppRadius.sm))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w  = size.width
            val h  = size.height
            val cx = w / 2f
            val cy = h / 2f

            when (styleId) {

                // ── DaVinci：暖金色 + 同心弧 ──────────────────────────────
                "davinci" -> {
                    drawRect(brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1C0A00), Color(0xFF7A4500), Color(0xFFD4950A)),
                        start  = Offset(0f, h), end = Offset(w, 0f)
                    ))
                    val arcColor = Color(0xFFFFD060)
                    for (i in 1..3) {
                        val r = w * (0.14f + i * 0.11f)
                        drawArc(
                            color      = arcColor.copy(alpha = 0.9f - i * 0.22f),
                            startAngle = -210f, sweepAngle = 150f,
                            useCenter  = false,
                            topLeft    = Offset(cx - r, cy - r),
                            size       = Size(r * 2f, r * 2f),
                            style      = Stroke(width = 2.5f)
                        )
                    }
                    drawCircle(color = Color(0xFFFFD060), radius = w * 0.07f,
                        center = Offset(cx - w * 0.05f, cy - h * 0.12f))
                }

                // ── 3D渲染：深蓝 + 等轴测正方体 ──────────────────────────
                "3d" -> {
                    drawRect(brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF00101E), Color(0xFF003060), Color(0xFF005580)),
                        start  = Offset(0f, h), end = Offset(w, 0f)
                    ))
                    val cs = w * 0.26f
                    val topPath = Path().apply {
                        moveTo(cx,        cy - cs * 0.72f)
                        lineTo(cx + cs,   cy - cs * 0.36f)
                        lineTo(cx,        cy)
                        lineTo(cx - cs,   cy - cs * 0.36f)
                        close()
                    }
                    val leftPath = Path().apply {
                        moveTo(cx - cs,   cy - cs * 0.36f)
                        lineTo(cx,        cy)
                        lineTo(cx,        cy + cs * 0.72f)
                        lineTo(cx - cs,   cy + cs * 0.36f)
                        close()
                    }
                    val rightPath = Path().apply {
                        moveTo(cx,        cy)
                        lineTo(cx + cs,   cy - cs * 0.36f)
                        lineTo(cx + cs,   cy + cs * 0.36f)
                        lineTo(cx,        cy + cs * 0.72f)
                        close()
                    }
                    drawPath(topPath,   color = Color(0xFF00CFFF).copy(alpha = 0.95f))
                    drawPath(leftPath,  color = Color(0xFF005888).copy(alpha = 0.95f))
                    drawPath(rightPath, color = Color(0xFF007AB8).copy(alpha = 0.95f))
                    val edgeColor = Color(0xFF40E0FF)
                    drawPath(topPath,   color = edgeColor, style = Stroke(width = 1.2f))
                    drawPath(leftPath,  color = edgeColor, style = Stroke(width = 1.2f))
                    drawPath(rightPath, color = edgeColor, style = Stroke(width = 1.2f))
                }

                // ── Turbo：紫电 + 闪电 ────────────────────────────────────
                "turbo" -> {
                    drawRect(brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF120020), Color(0xFF5500AA), Color(0xFFAA00EE)),
                        start  = Offset(0f, h), end = Offset(w, 0f)
                    ))
                    drawCircle(color = Color(0xFFDD00FF).copy(alpha = 0.25f),
                        radius = w * 0.38f, center = Offset(cx, cy))
                    val bolt = Path().apply {
                        moveTo(cx + w * 0.09f, cy - h * 0.37f)
                        lineTo(cx - w * 0.11f, cy - h * 0.02f)
                        lineTo(cx + w * 0.04f, cy - h * 0.02f)
                        lineTo(cx - w * 0.09f, cy + h * 0.37f)
                        lineTo(cx + w * 0.17f, cy + h * 0.02f)
                        lineTo(cx + w * 0.03f, cy + h * 0.02f)
                        close()
                    }
                    drawPath(bolt, color = Color(0xFFFFE040))
                    drawPath(bolt, color = Color.White.copy(alpha = 0.45f), style = Stroke(width = 1f))
                }

                // ── ImagineArt：珊瑚渐变 + 八芒星 ────────────────────────
                "imagineart" -> {
                    drawRect(brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1A0020), Color(0xFF880044), Color(0xFFEE4400)),
                        start  = Offset(0f, h), end = Offset(w, 0f)
                    ))
                    val outerR = w * 0.34f
                    val innerR = w * 0.14f
                    val star   = Path()
                    val pts    = 8
                    for (i in 0 until pts * 2) {
                        val angle = (i * Math.PI / pts - Math.PI / 2).toFloat()
                        val r     = if (i % 2 == 0) outerR else innerR
                        val x     = cx + r * kotlin.math.cos(angle)
                        val y     = cy + r * kotlin.math.sin(angle)
                        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                    }
                    star.close()
                    drawPath(star, brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFEE40), Color(0xFFFF6600), Color(0xFFFF0077)),
                        center = Offset(cx, cy), radius = outerR
                    ))
                    drawPath(star, color = Color(0xFFFFCC44).copy(alpha = 0.6f), style = Stroke(width = 1f))
                }

                // ── SeeDream：深邃星空 + 月牙 ────────────────────────────
                "seedream" -> {
                    drawRect(brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF020012), Color(0xFF0A0040), Color(0xFF160066)),
                        start  = Offset(0f, 0f), end = Offset(w, h)
                    ))
                    // 星星
                    listOf(
                        Offset(cx - w*0.30f, cy - h*0.33f) to 2.0f,
                        Offset(cx + w*0.26f, cy - h*0.26f) to 1.5f,
                        Offset(cx - w*0.12f, cy + h*0.30f) to 1.8f,
                        Offset(cx + w*0.30f, cy + h*0.18f) to 1.2f,
                        Offset(cx + w*0.08f, cy - h*0.12f) to 1.0f,
                        Offset(cx - w*0.28f, cy + h*0.10f) to 1.3f,
                    ).forEach { (pos, r) ->
                        drawCircle(color = Color.White.copy(alpha = 0.88f), radius = r, center = pos)
                    }
                    // 月牙（差集）
                    val moonR  = w * 0.22f
                    val mc     = Offset(cx - w * 0.05f, cy + h * 0.06f)
                    val fullMoon = Path().apply {
                        addOval(Rect(mc.x - moonR, mc.y - moonR, mc.x + moonR, mc.y + moonR))
                    }
                    val cutR = moonR * 0.80f
                    val cutC = Offset(mc.x + moonR * 0.52f, mc.y - moonR * 0.08f)
                    val cutCircle = Path().apply {
                        addOval(Rect(cutC.x - cutR, cutC.y - cutR, cutC.x + cutR, cutC.y + cutR))
                    }
                    val crescent = Path().apply { op(fullMoon, cutCircle, PathOperation.Difference) }
                    drawPath(crescent, brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFDDCCFF), Color(0xFFAA88FF)),
                        start  = Offset(mc.x - moonR, mc.y - moonR),
                        end    = Offset(mc.x + moonR, mc.y + moonR)
                    ))
                }

                else -> drawRect(color = AccentPrimary.copy(alpha = 0.2f))
            }
        }
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 800)
@Composable
private fun AIConfigSheetPreview() {
    AIConfigSheet(
        vm        = AIViewModel(),
        onDismiss = {}
    )
}
