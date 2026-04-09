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
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy

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
            
            // 1. 注入 DNS: 若配置里没有 dns 字段，强制使用可靠的公共 DNS
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

}
