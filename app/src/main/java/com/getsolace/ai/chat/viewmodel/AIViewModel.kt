package com.getsolace.ai.chat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.getsolace.ai.chat.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * ViewModel for AI image creation.
 */
class AIViewModel(app: Application) : AndroidViewModel(app) {

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

        val cacheDir = getApplication<Application>().cacheDir

        viewModelScope.launch {
            try {
                val startTime    = System.currentTimeMillis()
                val minDuration  = 3500L

                // Progress animation — hold at 0.92 until result ready
                val progressJob = launch {
                    var p = 0f
                    while (p < 0.92f) {
                        delay(200)
                        p = minOf(p + (0.015f + Math.random().toFloat() * 0.03f), 0.92f)
                        _progress.value = p
                    }
                }

                val fullPrompt = buildFullPrompt()
                val model      = PollinationsApi.modelForStyle(_selectedStyle.value.id)
                val width      = _selectedRatio.value.apiWidth()
                val height     = _selectedRatio.value.apiHeight()

                // ── 主线：Pollinations ─────────────────────────────────────
                val localPath = try {
                    PollinationsApi.generateImage(
                        cacheDir = cacheDir,
                        prompt   = fullPrompt,
                        width    = width,
                        height   = height,
                        model    = model
                    )
                } catch (primary: Exception) {
                    Log.w("AIViewModel", "Pollinations failed, trying HuggingFace: ${primary.message}")
                    // ── 兜底：HuggingFace ──────────────────────────────────
                    HuggingFaceApi.generateImage(
                        cacheDir = cacheDir,
                        prompt   = fullPrompt
                    )
                }

                // Ensure minimum animation time
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < minDuration) delay(minDuration - elapsed)

                progressJob.cancel()
                _progress.value = 1f
                delay(400)

                _generatedImageUrl.value = localPath
                _step.value = CreateAIStep.RESULT

                AIImageStore.saveImage(
                    AIGeneratedImage(
                        prompt      = promptText,
                        styleTitle  = _selectedStyle.value.title,
                        imageUrl    = localPath,
                        aspectRatio = _selectedRatio.value.label
                    )
                )

            } catch (e: Exception) {
                Log.e("AIViewModel", "Both APIs failed", e)
                _errorMessage.value = "生成失败，请稍后重试"
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

// ─── Shared OkHttp client ─────────────────────────────────────────────────────

private val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)   // image gen can be slow
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}

// ─── Pollinations API ─────────────────────────────────────────────────────────
//
// image.pollinations.ai — free public endpoint, no key needed.
// Actually downloads the image bytes to a local cache file so failures
// are detected in the ViewModel rather than silently in Coil.
//
// Style → model: davinci→flux-realism, turbo→turbo, seedream→flux-anime,
//                3d→flux-3d, imagineart→any-dark, others→flux

object PollinationsApi {

    private const val BASE_URL = "https://image.pollinations.ai/prompt"

    fun modelForStyle(styleId: String): String = when (styleId) {
        "davinci"    -> "flux-realism"
        "turbo"      -> "turbo"
        "seedream"   -> "flux-anime"
        "3d"         -> "flux-3d"
        "imagineart" -> "any-dark"
        else         -> "flux"
    }

    /**
     * Generates an image via Pollinations, downloads the bytes, saves to
     * [cacheDir] and returns a `file://` URI string for Coil to load.
     * Throws on any network or HTTP error.
     */
    suspend fun generateImage(
        cacheDir : File,
        prompt   : String,
        width    : Int,
        height   : Int,
        model    : String = "flux"
    ): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(prompt, "UTF-8")
        val seed    = (Math.random() * 100000).toInt()
        val url     = "$BASE_URL/$encoded?model=$model&width=$width&height=$height" +
                      "&seed=$seed&nologo=true&enhance=true"

        val request  = Request.Builder().url(url).get().build()
        val response = sharedHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Pollinations HTTP ${response.code}")
        }

        val bytes = response.body?.bytes()
            ?: throw Exception("Pollinations empty response")

        saveToCache(cacheDir, bytes, prefix = "poll")
    }
}

// ─── HuggingFace Inference API ────────────────────────────────────────────────
//
// Uses FLUX.1-schnell (fast, free tier) as the fallback model.
// POST {"inputs": "<prompt>"} → binary PNG response.

object HuggingFaceApi {

    private const val TOKEN = "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"
    private const val MODEL = "black-forest-labs/FLUX.1-schnell"
    // api-inference.huggingface.co is deprecated → use router.huggingface.co
    private const val ENDPOINT =
        "https://router.huggingface.co/hf-inference/models/$MODEL"

    /**
     * Generates an image via HuggingFace Inference API, saves to [cacheDir]
     * and returns a `file://` URI string for Coil to load.
     * Throws on network or HTTP error.
     */
    suspend fun generateImage(
        cacheDir : File,
        prompt   : String
    ): String = withContext(Dispatchers.IO) {
        val json    = JSONObject().put("inputs", prompt).toString()
        val body    = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $TOKEN")
            .post(body)
            .build()

        val response = sharedHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw Exception("HuggingFace HTTP ${response.code}: $errBody")
        }

        val bytes = response.body?.bytes()
            ?: throw Exception("HuggingFace empty response")

        saveToCache(cacheDir, bytes, prefix = "hf")
    }
}

// ─── Helper: save bytes to cache file, return file:// URI ────────────────────

private fun saveToCache(cacheDir: File, bytes: ByteArray, prefix: String): String {
    val dir  = File(cacheDir, "ai_images").also { it.mkdirs() }
    val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    file.writeBytes(bytes)
    return file.absolutePath  // Coil loads absolute paths directly
}
