package com.getsolace.ai.chat.api

import android.content.Context
import androidx.core.content.edit
import com.getsolace.ai.chat.data.AppStrategy
import com.google.gson.Gson

class StrategyCacheManager(context: Context) {

    companion object {
        private const val PREF_NAME = "strategy_cache"
        private const val KEY_DATA  = "strategy_data"
        private const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L  // 2 小时
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson  = Gson()

    /** 读取缓存，若过期则返回 null */
    fun load(): AppStrategy? {
        val json = prefs.getString(KEY_DATA, null) ?: return null
        return runCatching {
            val strategy = gson.fromJson(json, AppStrategy::class.java)
            val age = System.currentTimeMillis() - strategy.fetchTimeMs
            if (age < CACHE_TTL_MS) strategy else null
        }.getOrNull()
    }

    /** 写入缓存 */
    fun save(strategy: AppStrategy) {
        val withTime = strategy.copy(fetchTimeMs = System.currentTimeMillis())
        prefs.edit { putString(KEY_DATA, gson.toJson(withTime)) }
    }

    /** 强制清除缓存（如强制刷新场景） */
    fun clear() = prefs.edit { remove(KEY_DATA) }
}