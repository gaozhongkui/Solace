package com.getsolace.ai.chat.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

/**
 * 基于 Google 免费翻译接口的工具类（无需 API Key）
 * 接口：translate.googleapis.com/translate_a/single
 * 返回格式：[[["译文","原文",...],...],...] 译文在 [0][i][0]
 */
object TranslateUtil {

    private const val BASE = "https://translate.googleapis.com/translate_a/single"

    /** 将任意语言翻译成中文，失败返回原文 */
    suspend fun toZh(text: String): String = translate(text, "zh-CN")

    /** 将任意语言翻译成英文，失败返回原文 */
    suspend fun toEn(text: String): String = translate(text, "en")

    private suspend fun translate(text: String, target: String): String {
        if (text.isBlank()) return text
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = "$BASE?client=gtx&sl=auto&tl=$target&dt=t&q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = AppNetworkClient.execute(request, connectSec = 8, readSec = 10, writeSec = 5)
                if (!response.isSuccessful) return@withContext text
                val body = response.body?.string() ?: return@withContext text
                // 拼接所有分段译文
                val arr = JSONArray(body)
                val parts = arr.getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.optJSONArray(i)?.optString(0) ?: "")
                }
                sb.toString().trim().ifBlank { text }
            } catch (e: Exception) {
                text
            }
        }
    }
}
