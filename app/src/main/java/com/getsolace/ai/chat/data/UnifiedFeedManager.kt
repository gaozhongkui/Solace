package com.getsolace.ai.chat.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.random.Random

// ─── Feed Item (mirrors iOS PollinationFeedItem) ──────────────────────────────

data class FeedItem(
    val id: String,
    val imageUrl: String,
    val prompt: String,
    val width: Int    = 512,
    val height: Int   = 512,
    val model: String = "flux",
    val seed: Long    = 0L
) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}

// ─── UnifiedFeedManager (mirrors iOS UnifiedFeedManager.swift) ────────────────
//
// Manages a public image feed from CivitAI (primary) with Pollinations fallback.
// Mirrors iOS: CivitAI cursor-based pagination, 200-item memory limit,
// 60s health check, dedup by URL.

object UnifiedFeedManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fetchJob: Job? = null
    private var healthJob: Job? = null

    private const val MAX_ITEMS = 200
    private const val PAGE_SIZE = 20
    private const val HEALTH_CHECK_INTERVAL_MS = 60_000L
    private const val CIVITAI_BASE = "https://civitai.com/api/v1/images"

    private val httpClient = OkHttpClient()

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    // Cursor for CivitAI pagination (null = first page)
    private var nextCursor: String? = null

    private var isInitialized = false

    // ── Start / Stop ──────────────────────────────────────────────────────────

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

    // ── Refresh (reset cursor, reload from first page) ────────────────────────

    fun loadFeed() {
        if (_isLoading.value) return
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _isLoading.value = true
            nextCursor = null
            try {
                val newItems = fetchCivitAIFeed(cursor = null)
                if (newItems.isNotEmpty()) {
                    prependItems(newItems)
                } else {
                    appendItems(generateFallbackItems())
                }
            } catch (e: Exception) {
                appendItems(generateFallbackItems())
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Load more (next page via cursor) ─────────────────────────────────────

    fun loadMore() {
        if (_isLoadingMore.value || _isLoading.value) return
        scope.launch {
            _isLoadingMore.value = true
            try {
                val moreItems = fetchCivitAIFeed(cursor = nextCursor)
                appendItems(moreItems)
            } catch (e: Exception) {
                // ignore load-more failures silently
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    // ── CivitAI API fetch (mirrors iOS CivitAIDataSource.swift) ──────────────
    // GET https://civitai.com/api/v1/images?limit=20&sort=Newest&cursor={cursor}

    private suspend fun fetchCivitAIFeed(cursor: String?): List<FeedItem> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$CIVITAI_BASE?limit=$PAGE_SIZE&sort=Newest&nsfw=false")
            if (!cursor.isNullOrEmpty()) append("&cursor=$cursor")
        }

        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) return@withContext emptyList()

        val body = response.body?.string() ?: return@withContext emptyList()
        val json = JSONObject(body)

        // Save next cursor for pagination
        val metadata = json.optJSONObject("metadata")
        nextCursor = metadata?.optString("nextCursor")?.takeIf { it.isNotEmpty() }

        val items = json.optJSONArray("items") ?: return@withContext emptyList()

        (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val imageUrl = item.optString("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            // Skip NSFW
            if (item.optBoolean("nsfw", false)) return@mapNotNull null

            val meta = item.optJSONObject("meta")
            val prompt = meta?.optString("prompt") ?: ""
            val width = item.optInt("width", 512).coerceIn(64, 2048)
            val height = item.optInt("height", 512).coerceIn(64, 2048)
            val model = meta?.optString("model") ?: "flux"
            val seed = meta?.optLong("seed") ?: Random.nextLong(100000)

            FeedItem(
                id       = item.optInt("id", 0).toString(),
                imageUrl = imageUrl,
                prompt   = prompt,
                width    = width,
                height   = height,
                model    = model,
                seed     = seed
            )
        }
    }

    // ── Prepend new items to top (for refresh) ────────────────────────────────

    private fun prependItems(newItems: List<FeedItem>) {
        val existing = _items.value
        val existingUrls = existing.map { it.imageUrl }.toSet()
        val unique = newItems.filter { it.imageUrl !in existingUrls }
        val merged = (unique + existing).take(MAX_ITEMS)
        _items.value = merged
    }

    // ── Append items to bottom (for load-more / fallback) ────────────────────

    private fun appendItems(newItems: List<FeedItem>) {
        val existing = _items.value
        val existingUrls = existing.map { it.imageUrl }.toSet()
        val unique = newItems.filter { it.imageUrl !in existingUrls }
        val merged = (existing + unique).takeLast(MAX_ITEMS)
        _items.value = merged
    }

    // ── Health check (60s, mirrors iOS) ──────────────────────────────────────

    private fun startHealthCheck() {
        healthJob = scope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                if (_items.value.size < 20) {
                    loadFeed()
                }
            }
        }
    }

    // ── Pollinations fallback (when CivitAI unavailable) ─────────────────────

    private fun generateFallbackItems(): List<FeedItem> {
        val prompts = listOf(
            "beautiful mountain landscape at sunset, cinematic",
            "futuristic cityscape with neon lights, cyberpunk",
            "abstract colorful watercolor painting, artistic",
            "cute anime girl in a flower field, studio ghibli style",
            "epic fantasy dragon in misty mountains",
            "underwater coral reef photography, vibrant",
            "minimalist geometric pattern, modern design",
            "vintage portrait photography, warm tones",
            "space galaxy nebula with stars, astronomy",
            "cherry blossom japanese garden, serene",
            "steampunk mechanical clockwork, detailed",
            "magical forest with glowing mushrooms",
            "surreal dreamlike landscape, Salvador Dali style",
            "photorealistic cat portrait, professional",
            "ancient ruins at golden hour, archaeological",
            "cozy coffee shop interior, warm lighting",
            "northern lights aurora borealis, Iceland",
            "crystal cave with colorful minerals",
            "traditional Chinese ink painting",
            "modern abstract art with bold colors"
        )
        val widths  = listOf(512, 768, 512, 896)
        val heights = listOf(512, 512, 768, 504)
        val models  = listOf("flux", "turbo", "flux-realism")

        return prompts.mapIndexed { idx, prompt ->
            val seed    = Random.nextLong(100000)
            val wIdx    = idx % widths.size
            val width   = widths[wIdx]
            val height  = heights[wIdx]
            val model   = models[idx % models.size]
            val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
            val url     = "https://image.pollinations.ai/prompt/$encoded?width=$width&height=$height&seed=$seed&model=$model&nologo=true"
            FeedItem(
                id       = "fallback_${seed}_$idx",
                imageUrl = url,
                prompt   = prompt,
                width    = width,
                height   = height,
                model    = model,
                seed     = seed
            )
        }
    }
}
