package com.getsolace.ai.chat.data

data class AppStrategy(
    val version: Int = 0,
    val forceUpdate: Boolean = false,
    val minVersionCode: Int = 0,
    val updateUrl: String = "",
    val maintenance: Boolean = false,
    val maintenanceMsg: String = "",
    val featureFlags: Map<String, Any> = emptyMap(),
    val fetchTimeMs: Long = 0L
) {
    /** 读取 Boolean，默认 false */
    fun flag(key: String, default: Boolean = false): Boolean =
        when (val v = featureFlags[key]) {
            is Boolean -> v
            is String  -> v.equals("true", ignoreCase = true)
            else       -> default
        }

    /** 读取 String，默认 "" */
    fun flagString(key: String, default: String = ""): String =
        featureFlags[key]?.toString() ?: default

    /** 读取 Int，默认 0 */
    fun flagInt(key: String, default: Int = 0): Int =
        when (val v = featureFlags[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else      -> default
        }

    /** 读取 Double，默认 0.0 */
    fun flagDouble(key: String, default: Double = 0.0): Double =
        when (val v = featureFlags[key]) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else      -> default
        }
}