package com.getsolace.ai.chat.network

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import com.getsolace.ai.aidl.ISingBoxService
import com.getsolace.ai.aidl.ISingBoxServiceCallback
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

class SingBoxService : Service() {

    companion object {
        private const val TAG = "SingBoxService"
        const val MIXED_PORT = 2080

        const val STATE_STOPPED  = 0
        const val STATE_STARTING = 1
        const val STATE_RUNNING  = 2
        const val STATE_ERROR    = 3

        @Volatile private var libboxSetupDone = false
    }

    private var boxService: BoxService? = null
    private val callbacks = RemoteCallbackList<ISingBoxServiceCallback>()

    @Volatile private var _state           = STATE_STOPPED
    @Volatile private var _lastError       = ""
    @Volatile private var _activeLabel     = ""
    @Volatile private var _manuallyStopped = false

    private val binder = object : ISingBoxService.Stub() {
        override fun getState(): Int          = _state
        override fun getActiveLabel(): String  = _activeLabel
        override fun getLastError(): String    = _lastError
        override fun isManuallyStopped(): Boolean = _manuallyStopped

        override fun registerCallback(callback: ISingBoxServiceCallback?) {
            callback?.let { callbacks.register(it) }
        }

        override fun unregisterCallback(callback: ISingBoxServiceCallback?) {
            callback?.let { callbacks.unregister(it) }
        }

        override fun notifyAppLifecycle(isForeground: Boolean) {
            if (isForeground) boxService?.wake() else boxService?.pause()
        }

        override fun hotReloadConfig(configContent: String?): Int {
            if (configContent.isNullOrBlank()) {
                _lastError = "Empty config"
                return -1
            }
            return try {
                _state = STATE_STARTING
                broadcastState()

                runCatching { boxService?.close() }
                val newBoxService = Libbox.newService(configContent, platform)
                newBoxService.start()
                boxService = newBoxService

                _state = STATE_RUNNING
                _manuallyStopped = false
                _lastError = ""
                broadcastState()
                Log.i(TAG, "sing-box 内核成功启动/重载 (端口: $MIXED_PORT)")
                0
            } catch (e: Exception) {
                val fullError = Log.getStackTraceString(e)
                _lastError = e.message ?: "Unknown kernel error"
                _state = STATE_ERROR
                broadcastState()
                Log.e(TAG, "sing-box 内核启动失败！异常详情: $fullError")
                -1
            }
        }

        override fun requestUrlTestNodeDelay(requestId: Long, groupTag: String?, nodeTag: String?, timeoutMs: Int) {}
    }

    private val platform = object : PlatformInterface {
        override fun autoDetectInterfaceControl(fd: Int) {}
        override fun clearDNSCache() {}
        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {}
        override fun findConnectionOwner(p: Int, sA: String?, sP: Int, dA: String?, dP: Int): Int = -1
        override fun getInterfaces(): NetworkInterfaceIterator? = null
        override fun includeAllNetworks(): Boolean = false
        override fun localDNSTransport(): LocalDNSTransport? = null
        override fun openTun(options: TunOptions?): Int = -1
        override fun packageNameByUid(uid: Int): String = ""
        override fun readWIFIState(): WIFIState? = null
        override fun sendNotification(n: io.nekohasekai.libbox.Notification?) {}
        override fun startDefaultInterfaceMonitor(l: InterfaceUpdateListener?) {}
        override fun systemCertificates(): StringIterator? = null
        override fun uidByPackageName(packageName: String): Int = -1
        override fun underNetworkExtension(): Boolean = false
        override fun usePlatformAutoDetectInterfaceControl(): Boolean = false
        override fun useProcFS(): Boolean = false
        override fun writeLog(message: String) { Log.d("SingBoxKernel", message) }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (!libboxSetupDone) {
                Libbox.setup(SetupOptions().apply {
                    basePath = filesDir.absolutePath
                    workingPath = filesDir.absolutePath
                    tempPath = cacheDir.absolutePath
                    fixAndroidStack = true
                })
                libboxSetupDone = true
            }
        } catch (e: Exception) {
            _lastError = e.message ?: "init failed"
            _state = STATE_ERROR
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        runCatching { boxService?.close() }
        _state = STATE_STOPPED
        super.onDestroy()
    }

    private fun broadcastState() {
        val n = callbacks.beginBroadcast()
        repeat(n) { i ->
            runCatching {
                callbacks.getBroadcastItem(i).onStateChanged(_state, _activeLabel, _lastError, _manuallyStopped)
            }
        }
        callbacks.finishBroadcast()
    }
}
