package com.getsolace.ai.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getsolace.ai.chat.data.*
import com.getsolace.ai.chat.ui.screens.ai.AIProcessingScreen
import com.getsolace.ai.chat.ui.screens.ai.AIResultScreen
import com.getsolace.ai.chat.ui.screens.ai.AspectRatioButton
import com.getsolace.ai.chat.ui.screens.ai.RunMateToast
import com.getsolace.ai.chat.ui.screens.ai.StyleOptionCard
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.AIViewModel

class CreateAIActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PROMPT = "extra_prompt"

        fun newIntent(context: Context, prompt: String = ""): Intent =
            Intent(context, CreateAIActivity::class.java).apply {
                putExtra(EXTRA_PROMPT, prompt)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialPrompt = intent.getStringExtra(EXTRA_PROMPT) ?: ""
        setContent {
            SolaceTheme {
                val vm: AIViewModel = viewModel()
                LaunchedEffect(initialPrompt) {
                    if (initialPrompt.isNotBlank()) vm.setPrompt(initialPrompt)
                }
                CreateAIActivityScreen(vm = vm, onBack = { finish() })
            }
        }
    }
}

// ─── Activity-level screen ────────────────────────────────────────────────────

@Composable
fun CreateAIActivityScreen(vm: AIViewModel, onBack: () -> Unit) {
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
            label = "CreateAIActivityStep"
        ) { currentStep ->
            when (currentStep) {
                CreateAIStep.CONFIG     -> CreateAIConfigScreen(vm = vm, onBack = onBack)
                CreateAIStep.PROCESSING -> AIProcessingScreen(vm = vm)
                CreateAIStep.RESULT     -> AIResultScreen(imageUrl = generatedUrl ?: "", vm = vm)
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

// ─── Full-screen config form ──────────────────────────────────────────────────

@Composable
fun CreateAIConfigScreen(vm: AIViewModel, onBack: () -> Unit) {
    val selectedStyle by vm.selectedStyle.collectAsStateWithLifecycle()
    val selectedRatio by vm.selectedRatio.collectAsStateWithLifecycle()
    val prompt        by vm.prompt.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
            }
            Text(
                "AI 创作",
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

        // ── Prompt input ──────────────────────────────────────────────────────
        Text("创作提示词", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
        Spacer(Modifier.height(AppSpacing.sm))
        OutlinedTextField(
            value         = prompt,
            onValueChange = vm::setPrompt,
            placeholder   = { Text("描述你想要的画面…", color = TextDisabled) },
            modifier      = Modifier
                .fillMaxWidth()
                .height(120.dp),
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

        // ── Style selector ────────────────────────────────────────────────────
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

        // ── Aspect ratio ──────────────────────────────────────────────────────
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

        // ── Start button ──────────────────────────────────────────────────────
        Button(
            onClick  = { vm.startGeneration() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape    = RoundedCornerShape(AppRadius.lg),
            colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
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
