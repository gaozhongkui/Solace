package com.getsolace.ai.chat.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.getsolace.ai.aidl.ISingBoxService
import com.getsolace.ai.aidl.ISingBoxServiceCallback
import com.getsolace.ai.chat.SolaceApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.Date

object SingBoxManager {

    private const val TAG = "SingBoxManager"
    const val MIXED_PORT = SingBoxService.MIXED_PORT

    @Volatile private var service: ISingBoxService? = null
    @Volatile private var bound = false

    private val _isRunningFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRunningFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunningFlow

    private val stateCallback = object : ISingBoxServiceCallback.Stub() {
        override fun onStateChanged(state: Int, activeLabel: String?, lastError: String?, manuallyStopped: Boolean) {
            _isRunningFlow.value = state == SingBoxService.STATE_RUNNING
            if (state == SingBoxService.STATE_RUNNING) {
                Log.i(TAG, "sing-box 状态已置为 RUNNING，正在启动深度连通性诊断...")
                testProxyConnectivity()
            }
        }
        override fun onUrlTestNodeDelayResult(requestId: Long, delay: Int) {}
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ISingBoxService.Stub.asInterface(binder)
            bound = true
            runCatching { service?.registerCallback(stateCallback) }
            loadConfigFromStrategy()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    fun bind(context: Context) {
        if (bound) return
        val intent = Intent(context.applicationContext, SingBoxService::class.java)
        context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 动态修正配置并注入诊断规则
     */
    fun loadConfigFromStrategy() {
        val rawJson = SolaceApplication.strategyFlow.value?.flagString("sing_box_config") ?: return
        if (rawJson.isBlank()) return

        val finalJson = try {
            val json = JSONObject(rawJson)
            
            // 1. 修正 Outbounds: 强制 anytls 禁用 h2，提高隧道稳定性
            val outbounds = json.optJSONArray("outbounds")
            if (outbounds != null) {
                for (i in 0 until outbounds.length()) {
                    val out = outbounds.optJSONObject(i) ?: continue
                    val tls = out.optJSONObject("tls") ?: continue
                    tls.put("alpn", JSONArray(listOf("http/1.1")))
                }
            }

            // 2. 注入 DNS: 若配置里没有 dns 字段，强制使用可靠的公共 DNS
            if (!json.has("dns")) {
                val dns = JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("tag", "dns-remote")
                            put("address", "8.8.8.8")
                            put("detour", "direct")
                        })
                    })
                    put("final", "dns-remote")
                }
                json.put("dns", dns)
            }

            // 3. 注入 Route: 强制百度走 direct (直连)，用于排除 TLS/时间干扰
            val route = json.optJSONObject("route") ?: JSONObject()
            val rules = route.optJSONArray("rules") ?: JSONArray()
            val directRule = JSONObject().apply {
                put("domain", JSONArray(listOf("baidu.com", "www.baidu.com", "connectivitycheck.gstatic.com")))
                put("outbound", "direct")
            }
            val newRules = JSONArray().apply {
                put(directRule)
                for (i in 0 until rules.length()) put(rules.get(i))
            }
            route.put("rules", newRules)
            json.put("route", route)
            
            json.toString()
        } catch (e: Exception) {
            Log.e(TAG, "配置修正失败: ${e.message}")
            rawJson
        }

        runCatching { service?.hotReloadConfig(finalJson) }
    }

    fun getLocalProxy(): Proxy? {
        val running = runCatching { service?.getState() == SingBoxService.STATE_RUNNING }.getOrDefault(false)
        return if (running) Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", MIXED_PORT)) else null
    }

    fun isRunning(): Boolean = getLocalProxy() != null

    private fun testProxyConnectivity() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(5000) 
            Log.i(TAG, "诊断开始 - 当前系统时间: ${Date()}")

            // 检查端口是否真的开启了
            if (!isPortOpen(MIXED_PORT)) {
                Log.e(TAG, "致命错误: 本地端口 $MIXED_PORT 未开启！请检查 libbox 是否支持 anytls 协议。")
                return@launch
            }

            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", MIXED_PORT))
            val client = OkHttpClient.Builder()
                .proxy(proxy)
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectTimeout(15, TimeUnit.SECONDS)
                .build()

            // 诊断 A: 百度（已注入 Direct 规则）
            // 如果这个也失败，那 100% 是手机系统时间（2026年）的问题！
            testUrl(client, "https://www.baidu.com", "诊断-HTTPS直连")

            // 诊断 B: 谷歌 (走 anytls 节点)
            testUrl(client, "https://www.google.com/generate_204", "诊断-anytls节点")
        }
    }

    private fun isPortOpen(port: Int): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1000) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun testUrl(client: OkHttpClient, url: String, label: String) {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        try {
            client.newCall(request).execute().use { resp ->
                Log.i(TAG, "[$label] 成功: HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$label] 失败: ${e.javaClass.simpleName} - ${e.message}")
            if (e.message?.contains("closed") == true || e.message?.contains("reset") == true) {
                Log.w(TAG, "警告: 连接异常断开。当前设备时间为 ${Date()}，若不准确请先修正时间！")
            }
        }
    }
}
