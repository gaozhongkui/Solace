package com.getsolace.ai.chat.ui.screens.ai

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.getsolace.ai.chat.data.*
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.AIViewModel

// ─── Top-level Create AI Screen ───────────────────────────────────────────────

@Composable
fun CreateAIScreen(
    navController: NavController,
    vm: AIViewModel = viewModel()
) {
    val step         by vm.step.collectAsStateWithLifecycle()
    val generatedUrl by vm.generatedImageUrl.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(2500)
            vm.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BgDeepAlt, BgDeep)))
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            },
            label = "CreateAIStep"
        ) { currentStep ->
            when (currentStep) {
                CreateAIStep.CONFIG     -> AILabFeedScreen(vm = vm)
                CreateAIStep.PROCESSING -> AIProcessingScreen(vm = vm)
                CreateAIStep.RESULT     -> AIResultScreen(
                    imageUrl = generatedUrl ?: "",
                    vm       = vm
                )
            }
        }

        errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                RunMateToast(message = msg, isSuccess = false)
            }
        }
    }
}

// ─── AI Lab Feed Screen ───────────────────────────────────────────────────────

@Composable
fun AILabFeedScreen(vm: AIViewModel) {
    val history    by AIImageStore.images.collectAsStateWithLifecycle()
    var showConfig by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AuraAI",
                    style    = MaterialTheme.typography.headlineLarge.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = AppFontSize.largeTitle
                    ),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint     = AccentPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Feed
            if (history.isEmpty()) {
                AIEmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(2),
                    contentPadding        = PaddingValues(AppSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement   = Arrangement.spacedBy(AppSpacing.sm),
                    modifier              = Modifier.weight(1f)
                ) {
                    items(history, key = { it.id }) { img ->
                        AIFeedCard(image = img)
                    }
                }
            }
        }

        // FAB
        ExtendedFloatingActionButton(
            onClick         = { showConfig = true },
            containerColor  = AccentPrimary,
            contentColor    = TextPrimary,
            modifier        = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppSpacing.xl)
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(AppSpacing.sm))
            Text("AI 创作")
        }

        // Config sheet
        if (showConfig) {
            AIConfigSheet(vm = vm, onDismiss = { showConfig = false })
        }
    }
}

@Composable
fun AIEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            null,
            tint     = AccentPrimary.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            "开始你的AI创作",
            style     = MaterialTheme.typography.titleMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            "点击右下角按钮生成你的第一张AI艺术作品",
            style     = MaterialTheme.typography.bodySmall.copy(color = TextTertiary),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = AppSpacing.xxxl)
        )
    }
}

@Composable
fun AIFeedCard(image: AIGeneratedImage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(AppRadius.md))
            .background(CardBg)
    ) {
        if (image.imageUrl.isNotBlank()) {
            AsyncImage(
                model              = image.imageUrl,
                contentDescription = image.prompt,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                )
                .padding(AppSpacing.sm)
        ) {
            Column {
                Text(
                    image.styleTitle,
                    style    = MaterialTheme.typography.labelSmall.copy(color = AccentPrimary),
                    maxLines = 1
                )
                Text(
                    image.prompt,
                    style    = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    maxLines = 2
                )
            }
        }
    }
}

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
                .clickable { /* absorb touches */ },
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

                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AI 创作",
                        style    = MaterialTheme.typography.headlineSmall.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭", tint = TextSecondary)
                    }
                }

                Spacer(Modifier.height(AppSpacing.xl))

                // Prompt
                Text("创作提示词", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
                Spacer(Modifier.height(AppSpacing.sm))
                OutlinedTextField(
                    value         = prompt,
                    onValueChange = vm::setPrompt,
                    placeholder   = { Text("描述你想要的画面…", color = TextDisabled) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentPrimary,
                        unfocusedBorderColor = DividerColor,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        cursorColor          = AccentPrimary
                    ),
                    shape = RoundedCornerShape(AppRadius.sm)
                )

                Spacer(Modifier.height(AppSpacing.xl))

                // Style selector
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

                // Aspect ratio
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape  = RoundedCornerShape(AppRadius.lg),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(
                        "开始生成",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.height(AppSpacing.lg))
            }
        }
    }
}

// ─── Style Option Card ────────────────────────────────────────────────────────

@Composable
fun StyleOptionCard(
    style: AIImageStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) AccentPrimary else DividerColor,
        label       = "border"
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
    val bgColor   by animateColorAsState(if (isSelected) AccentPrimary else CardBg, label = "bg")
    val textColor by animateColorAsState(if (isSelected) TextPrimary else TextSecondary, label = "text")

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

// ─── Processing Screen ────────────────────────────────────────────────────────

@Composable
fun AIProcessingScreen(vm: AIViewModel) {
    val progress by vm.progress.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label         = "rotation"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(AppSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(GlowPurple, GlowCyan, GlowPurple)))
                    .rotate(rotation)
                    .blur(16.dp)
            )
            Box(
                modifier         = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(BgDeep),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoAwesome, null, tint = AccentPrimary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(AppSpacing.xxxl))
        Text(
            "AI 正在创作中…",
            style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            "基于你的提示词生成独特作品",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
        Spacer(Modifier.height(AppSpacing.xxxl))
        LinearProgressIndicator(
            progress      = { progress },
            modifier      = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color         = AccentPrimary,
            trackColor    = DividerColor
        )
    }
}

// ─── Result Screen ────────────────────────────────────────────────────────────

@Composable
fun AIResultScreen(imageUrl: String, vm: AIViewModel) {
    val prompt      by vm.prompt.collectAsStateWithLifecycle()
    val style       by vm.selectedStyle.collectAsStateWithLifecycle()
    var showSuccess by remember { mutableStateOf(false) }

    if (showSuccess) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            showSuccess = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.lg)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.resetToConfig() }) {
                    Icon(Icons.Default.Close, "关闭", tint = TextSecondary)
                }
                Text(
                    "生成结果",
                    style     = MaterialTheme.typography.titleLarge.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(AppSpacing.xl))

            // Generated image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(AppRadius.lg))
                    .background(CardBg)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(GlowPurple, GlowCyan)),
                        shape = RoundedCornerShape(AppRadius.lg)
                    )
            ) {
                AsyncImage(
                    model              = imageUrl,
                    contentDescription = prompt,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(AppSpacing.lg))

            // Prompt info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(AppRadius.md),
                colors   = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Text("提示词", style = MaterialTheme.typography.labelMedium.copy(color = AccentPrimary))
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(prompt, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        "风格: ${style.title}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary)
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.xl))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                OutlinedButton(
                    onClick  = { vm.resetToConfig() },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(AppRadius.md),
                    border   = BorderStroke(1.dp, AccentPrimary)
                ) {
                    Text("重新创作", color = AccentPrimary)
                }
                Button(
                    onClick  = { showSuccess = true },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(AppRadius.md),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("保存相册")
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
        }

        if (showSuccess) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
            ) {
                RunMateToast(message = "已保存到相册", isSuccess = true)
            }
        }
    }
}

// ─── Toast Component ──────────────────────────────────────────────────────────

@Composable
fun RunMateToast(message: String, isSuccess: Boolean) {
    Surface(
        shape           = RoundedCornerShape(AppRadius.xxl),
        color           = if (isSuccess) SuccessGreen.copy(alpha = 0.95f) else ErrorRed.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Text(
            text     = message,
            modifier = Modifier.padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
            style    = MaterialTheme.typography.bodyMedium.copy(
                color      = Color.White,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
