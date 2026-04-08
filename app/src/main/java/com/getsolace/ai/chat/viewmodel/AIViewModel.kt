package com.getsolace.ai.chat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.getsolace.ai.chat.SolaceApplication
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
import java.net.InetSocketAddress
import java.net.Proxy
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

// ─── Proxy pool + failover HTTP client ───────────────────────────────────────
//
// 代理列表从远程策略 featureFlags["proxy_list"] 读取，JSON 数组格式：
//   "proxy_list": "[{\"host\":\"1.2.3.4\",\"port\":7890,\"type\":\"HTTP\"},
//                   {\"host\":\"5.6.7.8\",\"port\":1080,\"type\":\"SOCKS\"}]"
//
// 执行策略：
//   1. 按顺序逐个尝试代理，成功则缓存该 index 作为当前可用代理
//   2. 当前代理连续失败 MAX_FAILS 次后，自动切换到下一个
//   3. 所有代理均失败则降级为直连（Proxy.NO_PROXY）
//   4. 策略配置发生变化时重置代理池

private data class ProxyEntry(val host: String, val port: Int, val type: String)

private object AppHttpClient {

    private const val MAX_FAILS   = 2    // 单个代理连续失败多少次后切换
    private const val TAG         = "AppHttpClient"

    private var cachedListKey: String    = ""
    private var proxyList: List<ProxyEntry> = emptyList()
    private var currentIndex: Int        = 0
    private var failCount: Int           = 0

    // 基础 OkHttpClient builder 参数（超时）
    private fun baseBuilder() = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)

    /** 将策略字符串解析为代理列表 */
    private fun parseProxyList(raw: String): List<ProxyEntry> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj  = arr.getJSONObject(i)
                val host = obj.optString("host", "")
                val port = obj.optInt("port", 0)
                val type = obj.optString("type", "HTTP")
                if (host.isNotBlank() && port > 0) ProxyEntry(host, port, type) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "proxy_list parse error: ${e.message}")
            emptyList()
        }
    }

    /** 根据 ProxyEntry 构建 OkHttpClient */
    private fun buildClient(entry: ProxyEntry?): OkHttpClient {
        val builder = baseBuilder()
        if (entry != null) {
            val proxyType = if (entry.type.equals("SOCKS", ignoreCase = true))
                Proxy.Type.SOCKS else Proxy.Type.HTTP
            builder.proxy(Proxy(proxyType, InetSocketAddress(entry.host, entry.port)))
            Log.i(TAG, "Using proxy [${entry.type}] ${entry.host}:${entry.port}")
        } else {
            builder.proxy(Proxy.NO_PROXY)
            Log.i(TAG, "Using direct connection (no proxy)")
        }
        return builder.build()
    }

    /**
     * 执行带代理故障切换的 HTTP 请求。
     * 内部按代理池顺序尝试，自动跳过失效代理，最终降级直连。
     */
    fun execute(request: okhttp3.Request): okhttp3.Response {
        // 检查策略是否更新，若更新则重建代理池
        val strategy = SolaceApplication.strategyFlow.value
        val rawList  = strategy?.flagString("proxy_list") ?: ""
        if (rawList != cachedListKey) {
            proxyList    = parseProxyList(rawList)
            currentIndex = 0
            failCount    = 0
            cachedListKey = rawList
            Log.i(TAG, "Proxy pool updated: ${proxyList.size} proxies")
        }

        // 无代理配置 → 直连
        if (proxyList.isEmpty()) {
            return buildClient(null).newCall(request).execute()
        }

        // 尝试从当前 index 开始，最多轮询整个列表 + 1次直连
        val total = proxyList.size + 1   // +1 = 直连兜底
        repeat(total) { attempt ->
            val entry = if (currentIndex < proxyList.size) proxyList[currentIndex] else null
            try {
                val response = buildClient(entry).newCall(request).execute()
                // 成功：重置失败计数
                failCount = 0
                return response
            } catch (e: Exception) {
                failCount++
                val label = entry?.let { "${it.host}:${it.port}" } ?: "direct"
                Log.w(TAG, "Proxy [$label] failed ($failCount/$MAX_FAILS): ${e.message}")

                if (failCount >= MAX_FAILS) {
                    // 切换到下一个代理
                    currentIndex = (currentIndex + 1) % total
                    failCount    = 0
                    val next = if (currentIndex < proxyList.size)
                        proxyList[currentIndex].let { "${it.host}:${it.port}" }
                    else "direct"
                    Log.i(TAG, "Switching to next proxy: $next")
                }

                if (attempt == total - 1) throw e   // 全部失败才抛出
            }
        }
        // unreachable
        throw Exception("All proxies and direct connection failed")
    }
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
        val response = AppHttpClient.execute(request)

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
// Token is NOT hardcoded — it is read at runtime from the remote Gitee strategy
// config via SolaceApplication.strategyFlow (featureFlags["hf_token"]).
// This way the token never appears in the APK and can be rotated without
// a new app release.
//
// Gitee JSON example:
//   { "featureFlags": { "hf_token": "hf_xxx..." } }

object HuggingFaceApi {

    private const val MODEL    = "black-forest-labs/FLUX.1-schnell"
    private const val ENDPOINT = "https://router.huggingface.co/hf-inference/models/$MODEL"


    /**
     * Generates an image via HuggingFace Inference API, saves to [cacheDir]
     * and returns an absolute file path for Coil to load.
     * Throws if token is missing or on any network / HTTP error.
     */
    suspend fun generateImage(
        cacheDir : File,
        prompt   : String
    ): String = withContext(Dispatchers.IO) {
        val token = SolaceApplication.strategyFlow.value?.flagString("hf_token") ?: ""
        if (token.isBlank()) throw Exception("HuggingFace token not configured")

        val json    = JSONObject().put("inputs", prompt).toString()
        val body    = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        val response = AppHttpClient.execute(request)

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
