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
    val displayPrompt: String get() = promptCn.ifBlank { prompt }
}

// ─── UnifiedFeedManager ───────────────────────────────────────────────────────

object UnifiedFeedManager {
    private const val TAG = "UnifiedFeedManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null
    private var translateJob: Job? = null

    private const val MAX_ITEMS          = 200
    private const val INITIAL_FILL_COUNT = 6
    private const val FEED_URL           = "https://image.pollinations.ai/feed"
    private const val MAX_RETRIES        = 3
    private const val RETRY_DELAY_MS     = 3_000L

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items

    private val bufferLock = Any()
    private val buffer = mutableListOf<FeedItem>()
    private val _bufferCount = MutableStateFlow(0)
    val bufferCount: StateFlow<Int> = _bufferCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private var isInitialized = false
    private val pendingLock = Any()
    private val pendingItems = mutableListOf<FeedItem>()
    @Volatile private var initialFilled = false

    // ── Public API ────────────────────────────────────────────────────────────

    fun start() {
        if (isInitialized) return
        isInitialized = true
        val cached = FeedCache.load()
        if (cached.isNotEmpty()) {
            _items.value = cached
            _isLoading.value = false
        }
        connectStream()
    }

    fun stop() {
        streamJob?.cancel()
        translateJob?.cancel()
        isInitialized = false
    }

    fun loadFeed() {
        streamJob?.cancel()
        translateJob?.cancel()
        initialFilled = false
        synchronized(pendingLock) { pendingItems.clear() }
        synchronized(bufferLock) {
            buffer.clear()
            _bufferCount.value = 0
        }
        connectStream()
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        scope.launch {
            if (_bufferCount.value == 0) {
                _isLoadingMore.value = true
                withTimeoutOrNull(30_000) { _bufferCount.first { it > 0 } }
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
                if (_items.value.isEmpty()) _isLoading.value = true
                else _isRefreshing.value = true
                val success = readStream()
                _isLoading.value = false
                if (success) failCount = 0
                else {
                    failCount++
                    if (isActive && failCount < MAX_RETRIES) delay(RETRY_DELAY_MS)
                }
            }
            if (_items.value.isEmpty()) _items.value = generateFallbackItems()
        }
    }

    private suspend fun readStream(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(FEED_URL)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        Log.w(TAG, "连接 Pollinations SSE proxy=${SingBoxManager.isRunning()}")

        try {
            AppNetworkClient.execute(request, connectSec = 15, readSec = 3600, writeSec = 15).use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "SSE 连接失败: HTTP ${response.code} ${response.message}")
                    return@withContext false
                }
                val source = response.body?.source() ?: run {
                    Log.e(TAG, "SSE body source 为空")
                    return@withContext false
                }
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

    /** parseItem 不再翻译，直接解析原文，翻译由 translatePending 异步补充 */
    private fun parseItem(json: String): FeedItem? {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("status") != "end_generating") return null
            if (obj.optBoolean("nsfw", false)) return null
            val imageUrl = obj.optString("imageURL").takeIf { it.isNotEmpty() } ?: return null
            FeedItem(
                id       = imageUrl.hashCode().toString(),
                imageUrl = imageUrl,
                prompt   = obj.optString("prompt", ""),
                promptCn = "",  // 先留空，代理就绪后异步翻译
                width    = obj.optInt("width", 512).coerceIn(64, 2048),
                height   = obj.optInt("height", 512).coerceIn(64, 2048),
                model    = obj.optString("model", "flux").ifEmpty { "flux" },
                seed     = obj.optLong("seed", 0L)
            )
        } catch (e: Exception) {
            null
        }
    }

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
            if (pendingItems.none { it.imageUrl == item.imageUrl }) pendingItems.add(item)
            if (pendingItems.size >= INITIAL_FILL_COUNT) {
                val snapshot = pendingItems.toList()
                pendingItems.clear()
                snapshot
            } else null
        }

        if (readyItems != null) {
            initialFilled = true
            _isLoading.value = false
            _isRefreshing.value = false
            _items.value = readyItems
            FeedCache.save(readyItems)
            // 数据展示后，异步补翻译
            translateItems(readyItems)
        }
    }

    /**
     * 代理就绪后异步翻译列表中 promptCn 为空的条目，翻译完一条更新一次 _items。
     * 只有代理运行时才执行，避免直连被墙导致阻塞。
     */
    private fun translateItems(targets: List<FeedItem>) {
        translateJob?.cancel()
        translateJob = scope.launch {
            // 等待代理就绪，最多等 60s
            val proxyReady = withTimeoutOrNull(60_000) {
                SingBoxManager.isRunningFlow.first { it }
            }
            if (proxyReady != true) return@launch  // 代理没启动，跳过翻译

            for (item in targets) {
                if (!isActive) break
                if (item.promptCn.isNotBlank()) continue
                val cn = runCatching {
                    withTimeoutOrNull(5_000) { TranslateUtil.toZh(item.prompt) }
                }.getOrNull() ?: continue
                if (cn == item.prompt) continue  // 翻译失败退回原文，不更新

                // 在当前 _items 和 buffer 中找到该条目并替换
                _items.value = _items.value.map {
                    if (it.id == item.id) it.copy(promptCn = cn) else it
                }
                synchronized(bufferLock) {
                    val idx = buffer.indexOfFirst { it.id == item.id }
                    if (idx >= 0) buffer[idx] = buffer[idx].copy(promptCn = cn)
                }
            }
            // 翻译完成后更新缓存
            FeedCache.save(_items.value)
        }
    }

    private fun generateFallbackItems(): List<FeedItem> {
        return listOf("beautiful landscape", "cyberpunk city", "anime girl").mapIndexed { idx, p ->
            FeedItem("fb_$idx", "https://image.pollinations.ai/prompt/$p", p)
        }
    }
}
