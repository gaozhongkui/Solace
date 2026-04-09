package com.getsolace.ai.chat.data

import android.util.Log
import com.getsolace.ai.chat.network.AppNetworkClient
import com.getsolace.ai.chat.network.SingBoxManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import okhttp3.Request
import org.json.JSONObject

// ─── Feed Item ────────────────────────────────────────────────────────────────

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

// ─── UnifiedFeedManager ───────────────────────────────────────────────────────

object UnifiedFeedManager {
    private const val TAG = "UnifiedFeedManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null

    private const val MAX_ITEMS = 200
    private const val INITIAL_FILL_COUNT = 10   // 首次填充条数
    private const val FEED_URL = "https://image.pollinations.ai/feed"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 3_000L

    // 主列表：首次 10 条展示给用户
    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items

    // 缓冲区：首次填充后收到的新数据
    private val bufferLock = Any()
    private val buffer = mutableListOf<FeedItem>()

    // UI 可观察缓冲区数量，用于显示「加载更多」按钮或角标
    private val _bufferCount = MutableStateFlow(0)
    val bufferCount: StateFlow<Int> = _bufferCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private var isInitialized = false
    @Volatile private var initialFilled = false

    fun start() {
        if (isInitialized) return
        isInitialized = true
        connectStream()
    }

    fun stop() {
        streamJob?.cancel()
        isInitialized = false
    }

    fun loadFeed() {
        streamJob?.cancel()
        _items.value = emptyList()
        initialFilled = false
        synchronized(bufferLock) {
            buffer.clear()
            _bufferCount.value = 0
        }
        connectStream()
    }

    /** 将缓冲区数据追加到列表末尾
     *  - 缓冲区有数据：直接追加，无 loading
     *  - 缓冲区为空：显示 loading，等待 SSE 推送新数据后再追加（最多等 30s）
     */
    fun loadMore() {
        if (_isLoadingMore.value) return
        scope.launch {
            if (_bufferCount.value == 0) {
                _isLoadingMore.value = true
                // 等待 SSE 推送至少一条进入缓冲区，超时 30s 自动放弃
                withTimeoutOrNull(30_000) {
                    _bufferCount.first { it > 0 }
                }
            }
            flushBuffer()
            _isLoadingMore.value = false
        }
    }

    private fun flushBuffer() {
        val toAdd = synchronized(bufferLock) {
            val copy = buffer.toList()
            buffer.clear()
            _bufferCount.value = 0
            copy
        }
        if (toAdd.isEmpty()) return
        val existing = _items.value
        val existingUrls = existing.map { it.imageUrl }.toSet()
        val unique = toAdd.filter { it.imageUrl !in existingUrls }
        if (unique.isNotEmpty()) {
            _items.value = (existing + unique).take(MAX_ITEMS)
        }
    }

    private fun connectStream() {
        streamJob = scope.launch {
            var failCount = 0
            while (isActive && failCount < MAX_RETRIES) {
                _isLoading.value = true
                val success = readStream()
                _isLoading.value = false
                if (success) {
                    failCount = 0
                } else {
                    failCount++
                    if (isActive && failCount < MAX_RETRIES) delay(RETRY_DELAY_MS)
                }
            }
            if (_items.value.isEmpty()) {
                _items.value = generateFallbackItems()
            }
        }
    }

    private suspend fun readStream(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(FEED_URL)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        Log.d(TAG, "连接 Pollinations SSE proxy=${SingBoxManager.isRunning()}")

        try {
            AppNetworkClient.execute(request, connectSec = 15, readSec = 3600, writeSec = 15).use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "SSE 连接失败: HTTP ${response.code}")
                    return@withContext false
                }

                val source = response.body?.source() ?: return@withContext false

                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue

                    val jsonStr = line.removePrefix("data:").trim()
                    if (jsonStr.isEmpty()) continue

                    val item = parseItem(jsonStr) ?: continue
                    dispatchItem(item)
                }
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SSE 异常: ${e.javaClass.simpleName} - ${e.message}")
            false
        }
    }

    /** 未完成首次填充 → 追加到主列表；首次填满后所有新数据 → 缓冲区 */
    private fun dispatchItem(item: FeedItem) {
        if (!initialFilled) {
            val current = _items.value
            if (current.any { it.imageUrl == item.imageUrl }) return
            val updated = current + item
            _items.value = updated
            if (updated.size >= INITIAL_FILL_COUNT) {
                initialFilled = true
                _isLoading.value = false
            }
        } else {
            synchronized(bufferLock) {
                if (buffer.none { it.imageUrl == item.imageUrl }) {
                    buffer.add(item)
                    _bufferCount.value = buffer.size
                }
            }
        }
    }

    private fun parseItem(json: String): FeedItem? {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("status") != "end_generating") return null
            if (obj.optBoolean("nsfw", false)) return null

            val imageUrl = obj.optString("imageURL").takeIf { it.isNotEmpty() } ?: return null
            FeedItem(
                id = imageUrl.hashCode().toString(),
                imageUrl = imageUrl,
                prompt = obj.optString("prompt", ""),
                width = obj.optInt("width", 512).coerceIn(64, 2048),
                height = obj.optInt("height", 512).coerceIn(64, 2048),
                model = obj.optString("model", "flux").ifEmpty { "flux" },
                seed = obj.optLong("seed", 0L)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun generateFallbackItems(): List<FeedItem> {
        return listOf("beautiful landscape", "cyberpunk city", "anime girl").mapIndexed { idx, p ->
            FeedItem("fb_$idx", "https://image.pollinations.ai/prompt/$p", p)
        }
    }
}
