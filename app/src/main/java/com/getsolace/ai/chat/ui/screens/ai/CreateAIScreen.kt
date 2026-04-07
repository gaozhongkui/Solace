package com.getsolace.ai.chat.ui.screens.ai

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.getsolace.ai.chat.data.CreateAIStep
import com.getsolace.ai.chat.data.UnifiedFeedManager
import com.getsolace.ai.chat.ui.theme.*
import com.getsolace.ai.chat.viewmodel.AIViewModel

@Composable
fun CreateAIScreen(
    navController: NavController,
    vm: AIViewModel = viewModel()
) {
    val step         by vm.step.collectAsStateWithLifecycle()
    val generatedUrl by vm.generatedImageUrl.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { UnifiedFeedManager.start() }

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
