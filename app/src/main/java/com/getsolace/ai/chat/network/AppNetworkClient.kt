package com.getsolace.ai.chat.network

import android.util.Log
import okhttp3.*
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 全局统一 HTTP 客户端。
 */
object AppNetworkClient {

    private const val TAG = "AppNetworkClient"

    private val sharedClient: OkHttpClient by lazy {
        val dynamicSelector = object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val proxy = SingBoxManager.getLocalProxy()
                return listOf(proxy ?: Proxy.NO_PROXY)
            }
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Log.w(TAG, "Proxy connect failed for $uri: ${ioe?.message}")
            }
        }

        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60,  TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .proxySelector(dynamicSelector)
            // 关键：强制使用 HTTP/1.1 解决部分代理节点处理 H2 导致的 Connection reset 问题
            .protocols(listOf(Protocol.HTTP_1_1)) 
            .build()
    }

    fun execute(
        request    : Request,
        connectSec : Long = 20,
        readSec    : Long = 90,
        writeSec   : Long = 30
    ): Response {
        val client = if (connectSec == 20L && readSec == 90L && writeSec == 30L) {
            sharedClient
        } else {
            sharedClient.newBuilder()
                .connectTimeout(connectSec, TimeUnit.SECONDS)
                .readTimeout(readSec,       TimeUnit.SECONDS)
                .writeTimeout(writeSec,     TimeUnit.SECONDS)
                .build()
        }
        return client.newCall(request).execute()
    }

    fun buildCoilClient(): OkHttpClient = sharedClient
}
