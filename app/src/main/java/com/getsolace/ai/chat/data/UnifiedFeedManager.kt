package com.getsolace.ai.chat.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
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
// Manages a public image feed from Pollinations.ai.
// iOS used CivitAI + fallback; Android uses Pollinations public feed endpoints.
// Memory limit: 200 items (matches iOS).
// Health check: every 60 seconds.

object UnifiedFeedManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fetchJob: Job? = null
    private var healthJob: Job? = null

    private const val MAX_ITEMS = 200
    private const val HEALTH_CHECK_INTERVAL_MS = 60_000L

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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

    // ── Load feed ─────────────────────────────────────────────────────────────

    fun loadFeed() {
        if (_isLoading.value) return
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _isLoading.value = true
            try {
                val newItems = fetchPollinationsPublicFeed()
                appendItems(newItems)
            } catch (e: Exception) {
                // Fallback: generate placeholder seed items
                appendItems(generateSeedItems())
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Deduplication + append (mirrors iOS dedup logic) ─────────────────────

    private fun appendItems(newItems: List<FeedItem>) {
        val existing = _items.value
        val existingUrls = existing.map { it.imageUrl }.toSet()
        val unique = newItems.filter { it.imageUrl !in existingUrls }
        val merged = (existing + unique).takeLast(MAX_ITEMS)
        _items.value = merged
    }

    // ── Health check (60 second interval, mirrors iOS) ────────────────────────

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

    // ── Pollinations public feed ──────────────────────────────────────────────
    // Pollinations doesn't have a public listing API, so we generate a batch of
    // diverse prompts and build URLs — exactly how iOS PollinationsImageGenerator works.

    private suspend fun fetchPollinationsPublicFeed(): List<FeedItem> = withContext(Dispatchers.IO) {
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

        prompts.mapIndexed { idx, prompt ->
            val seed     = Random.nextLong(100000)
            val wIdx     = idx % widths.size
            val width    = widths[wIdx]
            val height   = heights[wIdx]
            val model    = models[idx % models.size]
            val encoded  = java.net.URLEncoder.encode(prompt, "UTF-8")
            val url      = "https://image.pollinations.ai/prompt/$encoded?width=$width&height=$height&seed=$seed&model=$model&nologo=true"

            FeedItem(
                id       = "feed_${seed}_$idx",
                imageUrl = url,
                prompt   = prompt,
                width    = width,
                height   = height,
                model    = model,
                seed     = seed
            )
        }
    }

    // ── Fallback seed items ────────────────────────────────────────────────────

    private fun generateSeedItems(): List<FeedItem> {
        return (0 until 10).map { idx ->
            val seed    = Random.nextLong(100000)
            val prompt  = "beautiful artistic illustration $idx"
            val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
            FeedItem(
                id       = "seed_$idx",
                imageUrl = "https://image.pollinations.ai/prompt/$encoded?width=512&height=512&seed=$seed&nologo=true",
                prompt   = prompt,
                seed     = seed
            )
        }
    }
}
