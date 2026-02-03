package com.aifeed.feature.feed.data

import androidx.paging.PagingData
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.InteractionType
import com.aifeed.core.network.NetworkResult
import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    fun getArticlesFeed(userId: String): Flow<PagingData<ArticleEntity>>
    fun getArticlesByTopic(topicId: String): Flow<PagingData<ArticleEntity>>
    fun getArticleById(articleId: String): Flow<ArticleEntity?>

    suspend fun refreshFeed(userId: String): NetworkResult<Unit>
    suspend fun loadMoreArticles(userId: String): NetworkResult<Int>
    suspend fun recordInteraction(
        userId: String,
        articleId: String,
        type: InteractionType,
        metadata: Map<String, Any>? = null
    ): NetworkResult<Unit>
    suspend fun toggleBookmark(articleId: String): NetworkResult<Boolean>
    suspend fun toggleLike(userId: String, articleId: String): NetworkResult<Boolean>
    suspend fun toggleDislike(userId: String, articleId: String): NetworkResult<Boolean>
    suspend fun markAsRead(articleId: String): NetworkResult<Unit>
    suspend fun syncInteractions(): NetworkResult<Unit>
    suspend fun getSimilarArticles(articleId: String, limit: Int = 10): List<ArticleEntity>
}
