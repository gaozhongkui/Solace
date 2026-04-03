package com.getsolace.ai.chat.api
import com.getsolace.ai.chat.data.AppStrategy
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StrategyRepository(
    private val service: PolicyStrategyService,
    private val cache: StrategyCacheManager,
    private val config: GiteeStrategyConfig
) {
    private val gson = Gson()

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
                filepath = config.filePath,
                ref      = config.branch,
                token    = "token ${config.privateToken}"
            )
            val json     = body.string()
            val strategy = gson.fromJson(json, AppStrategy::class.java)

            // 3. 校验版本字段合法性
            require(strategy.version > 0) { "策略 version 字段无效" }

            // 4. 写入缓存
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