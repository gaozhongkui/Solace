package com.getsolace.ai.chat.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface PolicyStrategyService {

    /**
     * 获取私有仓库原始文件内容
     * 自建 Gitee: https://your-gitee.com/api/v5/repos/{owner}/{repo}/raw/{filepath}
     *
     * @param owner     仓库所有者
     * @param repo      仓库名称
     * @param filepath  文件路径，如 config/strategy.json
     * @param ref       分支/tag，默认 main
     * @param token     私有 token（Bearer）
     */
    @GET("api/v5/repos/{owner}/{repo}/raw/{filepath}")
    suspend fun fetchRawFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("filepath", encoded = true) filepath: String,
        @Query("ref") ref: String = "main",
        @Header("Authorization") token: String
    ): okhttp3.ResponseBody
}