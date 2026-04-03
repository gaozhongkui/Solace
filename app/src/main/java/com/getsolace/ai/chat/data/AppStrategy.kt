package com.getsolace.ai.chat.data

data class AppStrategy(
    val version: Int = 0,
    val forceUpdate: Boolean = false,
    val minVersionCode: Int = 0,
    val updateUrl: String = "",
    val maintenance: Boolean = false,
    val maintenanceMsg: String = "",
    val featureFlags: Map<String, Boolean> = emptyMap(),
    val fetchTimeMs: Long = 0L  // 记录拉取时间，用于缓存过期判断
)