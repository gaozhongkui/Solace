package com.getsolace.ai.chat.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Feed 列表本地缓存（SharedPreferences + Gson）
 * 与 AIImageStore 保持相同模式，无需额外依赖。
 * 最多缓存 MAX_CACHED 条，保证 SharedPreferences 不过大。
 */
object FeedCache {

    private const val PREFS_NAME  = "feed_cache"
    private const val KEY_ITEMS   = "feed_items"
    private const val MAX_CACHED  = 50

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 读取缓存，未初始化或无缓存时返回空列表 */
    fun load(): List<FeedItem> {
        if (!::prefs.isInitialized) return emptyList()
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<FeedItem>>() {}.type
            gson.fromJson<List<FeedItem>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** 保存列表（最多取前 MAX_CACHED 条，异步写入） */
    fun save(items: List<FeedItem>) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items.take(MAX_CACHED))).apply()
    }

    fun clear() {
        if (!::prefs.isInitialized) return
        prefs.edit().remove(KEY_ITEMS).apply()
    }
}
