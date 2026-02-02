package com.aifeed.core.network.api

import com.aifeed.core.network.model.ArticleDto
import com.aifeed.core.network.model.InteractionBatchRequest
import com.aifeed.core.network.model.TopicDto
import com.aifeed.core.network.model.UserDto
import com.aifeed.core.network.model.UserTopicDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApiService {

    // Articles
    @GET("articles")
    suspend fun getArticles(
        @Query("select") select: String = "*",
        @Query("order") order: String = "published_at.desc",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<ArticleDto>>

    @GET("articles")
    suspend fun getArticlesByTopics(
        @Query("select") select: String = "*",
        @Query("primary_topic_id") topicFilter: String, // "in.(id1,id2,id3)"
        @Query("order") order: String = "published_at.desc",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<ArticleDto>>

    @GET("articles")
    suspend fun getArticleById(
        @Query("id") id: String, // "eq.{id}"
        @Query("select") select: String = "*"
    ): Response<List<ArticleDto>>

    @GET("articles")
    suspend fun searchArticles(
        @Query("or") query: String, // "(title.ilike.*query*,preview.ilike.*query*)"
        @Query("order") order: String = "published_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<ArticleDto>>

    // Topics
    @GET("topics")
    suspend fun getTopics(
        @Query("select") select: String = "*",
        @Query("order") order: String = "name.asc"
    ): Response<List<TopicDto>>

    @GET("topics")
    suspend fun getTopicById(
        @Query("id") id: String // "eq.{id}"
    ): Response<List<TopicDto>>

    // Users
    @GET("users")
    suspend fun getUser(
        @Query("id") id: String, // "eq.{id}"
        @Query("select") select: String = "*"
    ): Response<List<UserDto>>

    @POST("users")
    suspend fun createUser(
        @Body user: UserDto
    ): Response<List<UserDto>>

    @PATCH("users")
    suspend fun updateUser(
        @Query("id") id: String, // "eq.{id}"
        @Body updates: Map<String, Any>
    ): Response<List<UserDto>>

    // User Topics
    @GET("user_topics")
    suspend fun getUserTopics(
        @Query("user_id") userId: String, // "eq.{userId}"
        @Query("select") select: String = "*,topics(*)"
    ): Response<List<UserTopicDto>>

    @POST("user_topics")
    suspend fun insertUserTopics(
        @Body userTopics: List<UserTopicDto>
    ): Response<List<UserTopicDto>>

    @DELETE("user_topics")
    suspend fun deleteUserTopics(
        @Query("user_id") userId: String // "eq.{userId}"
    ): Response<Unit>

    // Interactions
    @POST("interactions")
    suspend fun syncInteractions(
        @Body request: InteractionBatchRequest
    ): Response<Unit>

    @GET("interactions")
    suspend fun getUserInteractions(
        @Query("user_id") userId: String, // "eq.{userId}"
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100
    ): Response<List<Map<String, Any>>>

    // RPC functions for feed
    @POST("rpc/get_personalized_feed")
    suspend fun getPersonalizedFeed(
        @Body params: Map<String, Any>,
        @Header("Authorization") authToken: String
    ): Response<List<ArticleDto>>
}
