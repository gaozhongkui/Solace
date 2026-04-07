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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.lg)
                    .verticalScroll(rememberScrollState())
            ) {
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

                Spacer(Modifier.height(AppSpacing.xl))

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

                Spacer(Modifier.height(AppSpacing.xxxl))

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
                Spacer(Modifier.height(AppSpacing.lg))
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
        Box(
            modifier         = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(AccentPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = AccentPrimary, modifier = Modifier.size(28.dp))
        }
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
