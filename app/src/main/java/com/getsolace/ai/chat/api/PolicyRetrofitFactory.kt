package com.getsolace.ai.chat.api

import com.getsolace.ai.chat.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object PolicyRetrofitFactory {

    fun create(baseUrl: String): PolicyStrategyService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // 若 Authorization 值为 "token "（token 为空），则去掉该头，适配公开仓库
            .addInterceptor { chain ->
                val original = chain.request()
                val auth = original.header("Authorization")
                val request = if (auth.isNullOrBlank() || auth == "token ") {
                    original.newBuilder().removeHeader("Authorization").build()
                } else {
                    original
                }
                chain.proceed(request)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)           // 例: https://your-gitee.example.com/
            .client(client)
            .build()
            .create(PolicyStrategyService::class.java)
    }
}