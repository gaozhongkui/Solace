package com.getsolace.ai.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getsolace.ai.chat.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * ViewModel for AI image creation.
 */
class AIViewModel : ViewModel() {

    // ── UI State ──────────────────────────────────────────────────────────────

    private val _step = MutableStateFlow(CreateAIStep.CONFIG)
    val step: StateFlow<CreateAIStep> = _step

    private val _selectedStyle = MutableStateFlow(AIImageStyles.first())
    val selectedStyle: StateFlow<AIImageStyle> = _selectedStyle

    private val _selectedRatio = MutableStateFlow(AspectRatioOptions.first())
    val selectedRatio: StateFlow<AspectRatioOption> = _selectedRatio

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _generatedImageUrl = MutableStateFlow<String?>(null)
    val generatedImageUrl: StateFlow<String?> = _generatedImageUrl

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectStyle(style: AIImageStyle) { _selectedStyle.value = style }
    fun selectRatio(ratio: AspectRatioOption) { _selectedRatio.value = ratio }
    fun setPrompt(text: String) { _prompt.value = text }
    fun clearError() { _errorMessage.value = null }

    fun startGeneration() {
        val promptText = _prompt.value.trim()
        if (promptText.isBlank()) {
            _errorMessage.value = "请输入创作提示词"
            return
        }
        _step.value = CreateAIStep.PROCESSING
        _isLoading.value = true
        _progress.value = 0f
        _generatedImageUrl.value = null

        viewModelScope.launch {
            try {
                // Simulate progress animation while waiting for API
                val progressJob = launch {
                    var p = 0f
                    while (p < 0.95f) {
                        delay(300)
                        p = minOf(p + (0.02f + Math.random().toFloat() * 0.05f), 0.95f)
                        _progress.value = p
                    }
                }

                val fullPrompt = buildFullPrompt()
                val model     = PollinationsApi.modelForStyle(_selectedStyle.value.id)
                val imageUrl  = PollinationsApi.generateImage(
                    prompt = fullPrompt,
                    width  = _selectedRatio.value.apiWidth(),
                    height = _selectedRatio.value.apiHeight(),
                    model  = model
                )

                progressJob.cancel()
                _progress.value = 1f
                delay(300)

                _generatedImageUrl.value = imageUrl
                _step.value = CreateAIStep.RESULT

                // Save to history
                val record = AIGeneratedImage(
                    prompt      = promptText,
                    styleTitle  = _selectedStyle.value.title,
                    imageUrl    = imageUrl,
                    aspectRatio = _selectedRatio.value.label
                )
                AIImageStore.saveImage(record)

            } catch (e: Exception) {
                _errorMessage.value = "生成失败：${e.message}"
                _step.value = CreateAIStep.CONFIG
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetToConfig() {
        _step.value = CreateAIStep.CONFIG
        _generatedImageUrl.value = null
        _progress.value = 0f
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildFullPrompt(): String {
        val base   = _prompt.value.trim()
        val suffix = _selectedStyle.value.promptSuffix
        return "$base$suffix"
    }
}

// ─── Pollinations API (image.pollinations.ai public endpoint) ─────────────────
//
// Uses the standard Hugging Face Pollinations API (image.pollinations.ai).
// No API key required. Supported models: flux, turbo, flux-realism, flux-anime,
// flux-3d, any-dark, etc.
// Style → model mapping:
//   davinci → flux-realism, turbo → turbo, seedream → flux-anime,
//   3d → flux-3d, imagineart → any-dark, others → flux

object PollinationsApi {

    private const val BASE_URL = "https://image.pollinations.ai/prompt"

    // Map style id to Pollinations model name
    fun modelForStyle(styleId: String): String = when (styleId) {
        "davinci"     -> "flux-realism"
        "turbo"       -> "turbo"
        "seedream"    -> "flux-anime"
        "3d"          -> "flux-3d"
        "imagineart"  -> "any-dark"
        else          -> "flux"
    }

    suspend fun generateImage(prompt: String, width: Int, height: Int, model: String = "flux"): String {
        val encoded = URLEncoder.encode(prompt, "UTF-8")
        val seed = (Math.random() * 100000).toInt()
        // Use image.pollinations.ai public endpoint (Hugging Face Pollinations API)
        return "$BASE_URL/$encoded?model=$model&width=$width&height=$height&seed=$seed&nologo=true&enhance=true"
    }
}
