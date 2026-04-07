package com.getsolace.ai.chat.api
import android.util.Log
import com.getsolace.ai.chat.data.AppStrategy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 保留 JSON 原始类型：Boolean/Int/Double/String/null */
private object NativeTypeAdapter : TypeAdapter<Any>() {
    override fun write(out: JsonWriter, value: Any?) = Gson().toJson(value, Any::class.java, out)
    override fun read(reader: JsonReader): Any? = when (reader.peek()) {
        JsonToken.BOOLEAN      -> reader.nextBoolean()
        JsonToken.NUMBER       -> {
            val raw = reader.nextString()
            raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw
        }
        JsonToken.STRING       -> reader.nextString()
        JsonToken.NULL         -> reader.nextNull().let { null }
        else                   -> Gson().fromJson(reader, Any::class.java)
    }
}

class StrategyRepository(
    private val service: PolicyStrategyService,
    private val cache: StrategyCacheManager,
    private val config: GiteeStrategyConfig
) {
    private val gson = GsonBuilder()
        .registerTypeAdapter(Any::class.java, NativeTypeAdapter)
        .create()

    /**
     * 获取策略：优先缓存，缓存失效则拉取远端
     * @param forceRefresh 为 true 时强制忽略缓存
     */
    suspend fun getStrategy(forceRefresh: Boolean = false): Result<AppStrategy> =
        withContext(Dispatchers.IO) {

            // 1. 尝试读取有效缓存
            if (!forceRefresh) {
                cache.load()?.let { return@withContext Result.success(it) }
            }

            // 2. 请求远端 Gitee
            fetchFromGitee()
        }

    private suspend fun fetchFromGitee(): Result<AppStrategy> {
        return runCatching {
            val body = service.fetchRawFile(
                owner    = config.owner,
                repo     = config.repo,
                branch   = config.branch,
                filepath = config.filePath
            )
            val json     = body.string()
            val strategy = gson.fromJson(json, AppStrategy::class.java)

            require(strategy.version > 0) { "策略 version 字段无效" }

            cache.save(strategy)
            strategy
        }
    }
}

/** 配置项，建议通过 BuildConfig 或加密存储注入 */
data class GiteeStrategyConfig(
    val baseUrl: String,        // https://your-gitee.example.com/
    val owner: String,          // 仓库拥有者
    val repo: String,           // 仓库名
    val filePath: String,       // config/strategy.json
    val branch: String = "main",
    val privateToken: String    // 访问令牌（建议加密存储）
)