package com.getsolace.ai.chat.network

import android.util.Log
import com.getsolace.ai.chat.SolaceApplication
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 全局统一网络客户端，支持代理池轮询 + 故障自动切换。
 *
 * 代理列表通过远程策略 featureFlags["proxy_list"] 下发，格式：
 * [
 *   {"host":"1.2.3.4","port":7890,"type":"HTTP"},
 *   {"host":"5.6.7.8","port":1080,"type":"SOCKS"},
 *   ...
 * ]
 *
 * 执行策略：
 *   · 按顺序尝试代理，单个代理连续失败 [MAX_FAILS] 次后切换下一个
 *   · 全部代理失败后降级直连（Proxy.NO_PROXY）
 *   · 策略配置变化时自动重置代理池
 *   · [buildCoilProxy] 供 Coil ImageLoader 获取当前最优代理
 */
object AppNetworkClient {

    private const val TAG       = "AppNetworkClient"
    private const val MAX_FAILS = 2

    // ── 代理条目 ───────────────────────────────────────────────────────────────

    data class ProxyEntry(val host: String, val port: Int, val type: String) {
        fun toProxy(): Proxy {
            val proxyType = if (type.equals("SOCKS", ignoreCase = true))
                Proxy.Type.SOCKS else Proxy.Type.HTTP
            return Proxy(proxyType, InetSocketAddress(host, port))
        }
        override fun toString() = "[${type}] $host:$port"
    }

    // ── 代理池状态 ─────────────────────────────────────────────────────────────

    @Volatile private var cachedListKey  : String           = ""
    @Volatile private var proxyList      : List<ProxyEntry> = emptyList()
    @Volatile private var currentIndex   : Int              = 0
    @Volatile private var failCount      : Int              = 0

    // ── OkHttp 基础构建器（超时配置） ─────────────────────────────────────────

    private fun baseBuilder(
        connectSec: Long = 20,
        readSec   : Long = 90,
        writeSec  : Long = 30
    ) = OkHttpClient.Builder()
        .connectTimeout(connectSec, TimeUnit.SECONDS)
        .readTimeout(readSec,       TimeUnit.SECONDS)
        .writeTimeout(writeSec,     TimeUnit.SECONDS)

    // ── 解析代理列表 ───────────────────────────────────────────────────────────

    private fun parseProxyList(raw: String): List<ProxyEntry> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj  = arr.getJSONObject(i)
                val host = obj.optString("host", "")
                val port = obj.optInt("port", 0)
                val type = obj.optString("type", "HTTP")
                if (host.isNotBlank() && port > 0) ProxyEntry(host, port, type) else null
            }
        }.getOrElse {
            Log.w(TAG, "proxy_list parse error: ${it.message}")
            emptyList()
        }
    }

    // ── 同步代理池（策略变化时调用） ───────────────────────────────────────────

    private fun syncProxyPool() {
        val raw = SolaceApplication.strategyFlow.value?.flagString("proxy_list") ?: ""
        if (raw == cachedListKey) return
        proxyList    = parseProxyList(raw)
        currentIndex = 0
        failCount    = 0
        cachedListKey = raw
        if (proxyList.isEmpty()) {
            Log.i(TAG, "Proxy pool: direct connection (no proxies configured)")
        } else {
            Log.i(TAG, "Proxy pool updated: ${proxyList.size} proxies → ${proxyList.joinToString()}")
        }
    }

    // ── 构建携带指定代理的 OkHttpClient ────────────────────────────────────────

    private fun buildClient(
        entry      : ProxyEntry?,
        connectSec : Long = 20,
        readSec    : Long = 90,
        writeSec   : Long = 30
    ): OkHttpClient = baseBuilder(connectSec, readSec, writeSec)
        .proxy(entry?.toProxy() ?: Proxy.NO_PROXY)
        .build()

    // ── 核心执行方法（代理池故障切换） ────────────────────────────────────────
    //
    // 供 AIViewModel（AI生成）和 UnifiedFeedManager（Feed API）统一调用

    @Synchronized
    fun execute(
        request    : Request,
        connectSec : Long = 20,
        readSec    : Long = 90,
        writeSec   : Long = 30
    ): Response {
        syncProxyPool()

        // 无代理配置 → 直连
        if (proxyList.isEmpty()) {
            return buildClient(null, connectSec, readSec, writeSec)
                .newCall(request).execute()
        }

        // 轮询整个列表，最后一次用直连兜底
        val total = proxyList.size + 1
        var lastException: Exception? = null

        repeat(total) { attempt ->
            val entry = proxyList.getOrNull(currentIndex)
            val label = entry?.toString() ?: "direct"
            try {
                val response = buildClient(entry, connectSec, readSec, writeSec)
                    .newCall(request).execute()
                failCount = 0
                if (attempt > 0) Log.i(TAG, "Succeeded via $label after $attempt attempt(s)")
                return response
            } catch (e: Exception) {
                lastException = e
                failCount++
                Log.w(TAG, "[$label] failed ($failCount/$MAX_FAILS): ${e.message}")

                if (failCount >= MAX_FAILS) {
                    currentIndex = (currentIndex + 1) % total
                    failCount    = 0
                    val next = proxyList.getOrNull(currentIndex)?.toString() ?: "direct"
                    Log.i(TAG, "Switching proxy → $next")
                }
            }
        }
        throw lastException ?: Exception("All proxies and direct connection failed")
    }

    // ── 为 Coil ImageLoader 提供当前最优代理 ──────────────────────────────────
    //
    // Coil 的 OkHttpClient 在构建时固定，无法动态切换；
    // 取当前 currentIndex 对应的代理（已通过故障切换选出最优）。

    /**
     * 为 Coil ImageLoader 构建 OkHttpClient。
     *
     * 使用动态 [ProxySelector] 而非固定 proxy()：每次 Coil 建立连接时都会
     * 调用 select()，从而始终读取最新的代理池状态（策略下发后自动生效）。
     */
    fun buildCoilClient(): OkHttpClient {
        val dynamicSelector = object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                syncProxyPool()
                val entry = proxyList.getOrNull(currentIndex)
                if (entry != null) Log.d(TAG, "Coil proxy → $entry")
                return listOf(entry?.toProxy() ?: Proxy.NO_PROXY)
            }
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Log.w(TAG, "Coil proxy connect failed for $uri: ${ioe?.message}")
            }
        }
        return OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45,  TimeUnit.SECONDS)
            .proxySelector(dynamicSelector)
            .build()
    }
}
