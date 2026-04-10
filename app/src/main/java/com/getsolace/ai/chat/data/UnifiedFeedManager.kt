package com.getsolace.ai.chat.data

import android.util.Log
import com.getsolace.ai.chat.network.AppNetworkClient
import com.getsolace.ai.chat.network.SingBoxManager
import com.getsolace.ai.chat.network.TranslateUtil
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
    val promptCn: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val model: String = "flux",
    val seed: Long = 0L
) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
    /** 优先展示中文译文，无译文时退回英文原文 */
    val displayPrompt: String get() = promptCn.ifBlank { prompt }
}

// ─── UnifiedFeedManager ───────────────────────────────────────────────────────

object UnifiedFeedManager {
    private const val TAG = "UnifiedFeedManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null

    private const val MAX_ITEMS         = 200
    private const val INITIAL_FILL_COUNT = 6   // 新数据攒够几条后一次性替换缓存展示
    private const val FEED_URL          = "https://image.pollinations.ai/feed"
    private const val MAX_RETRIES       = 3
    private const val RETRY_DELAY_MS    = 3_000L

    // 展示给用户的主列表
    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items

    // 缓冲区：initialFilled 后 SSE 新数据进这里，等用户上拉
    private val bufferLock = Any()
    private val buffer = mutableListOf<FeedItem>()
    private val _bufferCount = MutableStateFlow(0)
    val bufferCount: StateFlow<Int> = _bufferCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private var isInitialized = false

    // SSE 数据静默预热区：攒够 INITIAL_FILL_COUNT 条后一次性替换 _items
    private val pendingLock = Any()
    private val pendingItems = mutableListOf<FeedItem>()
    @Volatile private var initialFilled = false  // true 后新数据进 buffer

    // ── Public API ────────────────────────────────────────────────────────────

    fun start() {
        if (isInitialized) return
        isInitialized = true
        // 立即展示缓存，SSE 新数据在后台静默攒够后再替换
        val cached = FeedCache.load()
        if (cached.isNotEmpty()) {
            _items.value = cached
            _isLoading.value = false
        }
        connectStream()
    }

    fun stop() {
        streamJob?.cancel()
        isInitialized = false
    }

    /** 手动刷新：清空当前内容，重新连接 SSE */
    fun loadFeed() {
        streamJob?.cancel()
        _items.value = emptyList()
        initialFilled = false
        synchronized(pendingLock) { pendingItems.clear() }
        synchronized(bufferLock) {
            buffer.clear()
            _bufferCount.value = 0
        }
        connectStream()
    }

    /** 上拉加载更多：将 buffer 追加到列表；buffer 为空时等待 SSE 新数据 */
    fun loadMore() {
        if (_isLoadingMore.value) return
        scope.launch {
            if (_bufferCount.value == 0) {
                _isLoadingMore.value = true
                withTimeoutOrNull(30_000) {
                    _bufferCount.first { it > 0 }
                }
            }
            flushBuffer()
            _isLoadingMore.value = false
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

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
            val updated = (existing + unique).take(MAX_ITEMS)
            _items.value = updated
            FeedCache.save(updated)
        }
    }

    private fun connectStream() {
        streamJob = scope.launch {
            var failCount = 0
            while (isActive && failCount < MAX_RETRIES) {
                _isLoading.value = _items.value.isEmpty()   // 有内容展示时不显示 loading
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

    /**
     * 数据分发：
     * - initialFilled = false → 新数据进 pendingItems 静默预热
     *   攒够 INITIAL_FILL_COUNT 条 → 一次性替换 _items，保存缓存
     * - initialFilled = true  → 新数据进 buffer，等用户上拉
     */
    private fun dispatchItem(item: FeedItem) {
        if (initialFilled) {
            synchronized(bufferLock) {
                if (buffer.none { it.imageUrl == item.imageUrl }) {
                    buffer.add(item)
                    _bufferCount.value = buffer.size
                }
            }
            return
        }

        val readyItems = synchronized(pendingLock) {
            if (pendingItems.none { it.imageUrl == item.imageUrl }) {
                pendingItems.add(item)
            }
            if (pendingItems.size >= INITIAL_FILL_COUNT) {
                val snapshot = pendingItems.toList()
                pendingItems.clear()
                snapshot
            } else {
                null
            }
        }

        if (readyItems != null) {
            // 攒够了，一次性替换
            initialFilled = true
            _isLoading.value = false
            _items.value = readyItems
            FeedCache.save(readyItems)
        }
    }

    private suspend fun parseItem(json: String): FeedItem? {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("status") != "end_generating") return null
            if (obj.optBoolean("nsfw", false)) return null
            val imageUrl = obj.optString("imageURL").takeIf { it.isNotEmpty() } ?: return null
            val prompt = obj.optString("prompt", "")
            val promptCn = withTimeoutOrNull(3_000) {
                TranslateUtil.toZh(prompt)
            } ?: prompt
            FeedItem(
                id       = imageUrl.hashCode().toString(),
                imageUrl = imageUrl,
                prompt   = prompt,
                promptCn = promptCn,
                width    = obj.optInt("width", 512).coerceIn(64, 2048),
                height   = obj.optInt("height", 512).coerceIn(64, 2048),
                model    = obj.optString("model", "flux").ifEmpty { "flux" },
                seed     = obj.optLong("seed", 0L)
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
