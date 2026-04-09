package com.getsolace.ai.chat.data

import android.util.Log
import com.getsolace.ai.chat.network.AppNetworkClient
import com.getsolace.ai.chat.network.SingBoxManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import org.json.JSONObject
import kotlin.random.Random

// ─── Feed Item (mirrors iOS PollinationFeedItem) ──────────────────────────────

data class FeedItem(
    val id: String,
    val imageUrl: String,
    val prompt: String,
    val width: Int = 512,
    val height: Int = 512,
    val model: String = "flux",
    val seed: Long = 0L
) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}

// ─── UnifiedFeedManager (mirrors iOS UnifiedFeedManager.swift) ────────────────

object UnifiedFeedManager {
    private const val TAG = "UnifiedFeedManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fetchJob: Job? = null
    private var healthJob: Job? = null

    private const val MAX_ITEMS = 200
    private const val PAGE_SIZE = 20
    private const val HEALTH_CHECK_INTERVAL_MS = 5 * 60_000L
    private const val CIVITAI_BASE = "https://civitai.com/api/v1/images"

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private var nextCursor: String? = null
    private var isInitialized = false

    fun start() {
        if (isInitialized) return
        isInitialized = true
        loadFeed()
        startHealthCheck()
    }

    fun stop() {
        fetchJob?.cancel()
        healthJob?.cancel()
        isInitialized = false
    }

    fun loadFeed() {
        if (_isLoading.value) return
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _isLoading.value = true
            nextCursor = null
            try {
                val newItems = fetchCivitAIFeed(cursor = null)
                if (newItems.isNotEmpty()) {
                    _items.value = newItems
                } else if (_items.value.isEmpty()) {
                    appendItems(generateFallbackItems())
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadFeed 异常: ${e.message}")
                if (_items.value.isEmpty()) appendItems(generateFallbackItems())
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isLoading.value) return
        scope.launch {
            _isLoadingMore.value = true
            try {
                val moreItems = fetchCivitAIFeed(cursor = nextCursor)
                appendItems(moreItems)
            } catch (e: Exception) {
                Log.e(TAG, "loadMore 失败: ${e.message}")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun fetchCivitAIFeed(cursor: String?): List<FeedItem> =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$CIVITAI_BASE?limit=$PAGE_SIZE&sort=Newest&nsfw=false")
                if (!cursor.isNullOrEmpty()) append("&cursor=$cursor")
            }

            Log.d(TAG, "fetchCivitAI proxy=${SingBoxManager.isRunning()} url=$url")
            
            // 关键：模拟真实浏览器请求头，防止被 Cloudflare/CivitAI 拦截并重置连接
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Referer", "https://civitai.com/")
                .build()

            val response = try {
                AppNetworkClient.execute(request, connectSec = 15, readSec = 20, writeSec = 15)
            } catch (e: Exception) {
                Log.e(TAG, "fetchCivitAI 网络异常 (${e.javaClass.simpleName}): ${e.message}")
                return@withContext emptyList()
            }

            if (!response.isSuccessful) {
                Log.e(TAG, "fetchCivitAI HTTP ${response.code} ${response.message}")
                response.close()
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            try {
                val json = JSONObject(body)
                val metadata = json.optJSONObject("metadata")
                nextCursor = metadata?.optString("nextCursor")?.takeIf { it.isNotEmpty() }

                val itemsArray = json.optJSONArray("items") ?: return@withContext emptyList()
                val parsedItems = mutableListOf<FeedItem>()
                
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.optJSONObject(i) ?: continue
                    val imageUrl = item.optString("url").takeIf { it.isNotEmpty() } ?: continue
                    if (item.optBoolean("nsfw", false)) continue

                    val meta = item.optJSONObject("meta")
                    val width = item.optInt("width", 512).coerceIn(64, 2048)
                    val height = item.optInt("height", 512).coerceIn(64, 2048)
                    
                    parsedItems.add(FeedItem(
                        id = item.optInt("id", 0).toString(),
                        imageUrl = civitaiThumbnailUrl(imageUrl, width),
                        prompt = meta?.optString("prompt") ?: "",
                        width = width,
                        height = height,
                        model = meta?.optString("model") ?: "flux",
                        seed = meta?.optLong("seed") ?: Random.nextLong(100000)
                    ))
                }
                parsedItems
            } catch (e: Exception) {
                Log.e(TAG, "解析 CivitAI JSON 失败: ${e.message}")
                emptyList()
            }
        }

    private fun prependItems(newItems: List<FeedItem>) {
        val existing = _items.value
        val existingUrls = existing.map { it.imageUrl }.toSet()
        val unique = newItems.filter { it.imageUrl !in existingUrls }
        _items.value = (unique + existing).take(MAX_ITEMS)
    }

    private fun appendItems(newItems: List<FeedItem>) {
        val existing = _items.value
        val existingUrls = existing.map { it.imageUrl }.toSet()
        val unique = newItems.filter { it.imageUrl !in existingUrls }
        _items.value = (existing + unique).takeLast(MAX_ITEMS)
    }

    private fun startHealthCheck() {
        healthJob = scope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                if (_items.value.size < 10) loadFeed()
            }
        }
    }

    private fun civitaiThumbnailUrl(url: String, originalWidth: Int): String {
        if (!url.contains("image.civitai.com") || originalWidth <= 450) return url
        val lastSlash = url.lastIndexOf('/')
        if (lastSlash < 0) return url
        return url.substring(0, lastSlash) + "/width=450/" + url.substring(lastSlash + 1)
    }

    private fun generateFallbackItems(): List<FeedItem> {
        return listOf("beautiful landscape", "cyberpunk city", "anime girl").mapIndexed { idx, p ->
            FeedItem("fb_$idx", "https://image.pollinations.ai/prompt/$p", p)
        }
    }
}
