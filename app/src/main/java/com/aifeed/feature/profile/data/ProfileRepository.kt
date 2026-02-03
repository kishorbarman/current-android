package com.aifeed.feature.profile.data

import androidx.paging.PagingData
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.network.NetworkResult
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getBookmarkedArticles(): Flow<PagingData<ArticleEntity>>
    fun getReadingHistory(): Flow<PagingData<ArticleEntity>>
    fun getLikedArticles(): Flow<PagingData<ArticleEntity>>
    fun getDislikedArticles(): Flow<PagingData<ArticleEntity>>
    fun getUserTopics(userId: String): Flow<List<TopicEntity>>

    suspend fun removeBookmark(articleId: String): NetworkResult<Unit>
    suspend fun removeLike(articleId: String): NetworkResult<Unit>
    suspend fun removeDislike(articleId: String): NetworkResult<Unit>
    suspend fun clearReadingHistory(): NetworkResult<Unit>
    suspend fun getBookmarksCount(): Int
    suspend fun getReadCount(): Int
}
