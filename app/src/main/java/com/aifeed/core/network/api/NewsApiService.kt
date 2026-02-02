package com.aifeed.core.network.api

import com.aifeed.core.network.model.NewsApiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApiService {

    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("country") country: String = "us",
        @Query("category") category: String? = null,
        @Query("pageSize") pageSize: Int = 20,
        @Query("page") page: Int = 1
    ): Response<NewsApiResponse>

    @GET("everything")
    suspend fun searchNews(
        @Query("q") query: String,
        @Query("sortBy") sortBy: String = "publishedAt",
        @Query("language") language: String = "en",
        @Query("pageSize") pageSize: Int = 20,
        @Query("page") page: Int = 1
    ): Response<NewsApiResponse>

    @GET("everything")
    suspend fun getNewsByTopic(
        @Query("q") topic: String,
        @Query("sortBy") sortBy: String = "publishedAt",
        @Query("language") language: String = "en",
        @Query("from") from: String? = null,
        @Query("pageSize") pageSize: Int = 20,
        @Query("page") page: Int = 1
    ): Response<NewsApiResponse>
}
