package com.getsolace.ai.chat.api

import retrofit2.http.GET
import retrofit2.http.Path

interface PolicyStrategyService {

    /**
     * 读取 Gitee 公开仓库的 raw 文件内容
     * 实际 URL: https://gitee.com/{owner}/{repo}/raw/{branch}/{filepath}
     */
    @GET("{owner}/{repo}/raw/{branch}/{filepath}")
    suspend fun fetchRawFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Path("filepath", encoded = true) filepath: String
    ): okhttp3.ResponseBody
}