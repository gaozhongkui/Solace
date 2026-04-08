package com.getsolace.ai.chat

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.getsolace.ai.chat.api.GiteeStrategyConfig
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import com.getsolace.ai.chat.api.PolicyRetrofitFactory
import com.getsolace.ai.chat.api.StrategyCacheManager
import com.getsolace.ai.chat.api.StrategyRepository
import com.getsolace.ai.chat.data.AppStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SolaceApplication : Application(), ImageLoaderFactory {

    companion object {
        /** App 策略状态，全局可观察 */
        private val _strategyFlow = MutableStateFlow<AppStrategy?>(null)
        val strategyFlow: StateFlow<AppStrategy?> = _strategyFlow

        lateinit var strategyRepository: StrategyRepository
            private set
    }

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // ── 初始化策略仓库 ─────────────────────────────────────────────────────
        val config = GiteeStrategyConfig(
            baseUrl      = BuildConfig.STRATEGY_BASE_URL,
            owner        = BuildConfig.STRATEGY_OWNER,
            repo         = BuildConfig.STRATEGY_REPO,
            filePath     = BuildConfig.STRATEGY_FILE_PATH,
            branch       = BuildConfig.STRATEGY_BRANCH,
            privateToken = BuildConfig.STRATEGY_TOKEN
        )
        strategyRepository = StrategyRepository(
            service = PolicyRetrofitFactory.create(config.baseUrl),
            cache   = StrategyCacheManager(this),
            config  = config
        )

        // ── 启动策略拉取（优先缓存，后台更新） ──────────────────────────────
        appScope.launch {
            strategyRepository.getStrategy(forceRefresh = true)
                .onSuccess { _strategyFlow.value = it }
        }
    }

    override fun newImageLoader(): ImageLoader {
        // 代理：取 proxy_list 第一个可用条目供 Coil 使用
        val strategy  = strategyFlow.value
        val rawList   = strategy?.flagString("proxy_list") ?: ""
        val firstProxy: Proxy = runCatching {
            if (rawList.isBlank()) return@runCatching Proxy.NO_PROXY
            val obj  = org.json.JSONArray(rawList).getJSONObject(0)
            val host = obj.optString("host", "")
            val port = obj.optInt("port", 0)
            val type = obj.optString("type", "HTTP")
            if (host.isBlank() || port <= 0) return@runCatching Proxy.NO_PROXY
            val pType = if (type.equals("SOCKS", ignoreCase = true))
                Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(pType, InetSocketAddress(host, port))
        }.getOrDefault(Proxy.NO_PROXY)

        val okHttp = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .proxy(firstProxy)
            .build()

        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .okHttpClient(okHttp)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)  // 30% 内存用于图片缓存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)  // 200 MB 磁盘缓存
                    .build()
            }
            .crossfade(250)
            .respectCacheHeaders(false)  // 忽略 CDN 的 Cache-Control，强制本地缓存
            .build()
    }
}
