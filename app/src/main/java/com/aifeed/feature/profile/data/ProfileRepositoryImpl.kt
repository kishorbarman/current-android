package com.aifeed.feature.profile.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.aifeed.core.database.dao.ArticleDao
import com.aifeed.core.database.dao.UserTopicDao
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.di.IoDispatcher
import com.aifeed.core.network.NetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val userTopicDao: UserTopicDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ProfileRepository {

    override fun getBookmarkedArticles(): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            )
        ) {
            articleDao.getBookmarkedArticlesPaged()
        }.flow
    }

    override fun getReadingHistory(): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            )
        ) {
            articleDao.getReadArticlesPaged()
        }.flow
    }

    override fun getLikedArticles(): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            )
        ) {
            articleDao.getLikedArticlesPaged()
        }.flow
    }

    override fun getDislikedArticles(): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            )
        ) {
            articleDao.getDislikedArticlesPaged()
        }.flow
    }

    override fun getUserTopics(userId: String): Flow<List<TopicEntity>> {
        return userTopicDao.getUserTopicsWithDetails(userId)
    }

    override suspend fun removeBookmark(articleId: String): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            try {
                articleDao.updateBookmarkStatus(articleId, false)
                NetworkResult.Success(Unit)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to remove bookmark: ${e.message}")
            }
        }

    override suspend fun removeLike(articleId: String): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            try {
                articleDao.updateLikeStatus(articleId, false)
                NetworkResult.Success(Unit)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to remove like: ${e.message}")
            }
        }

    override suspend fun removeDislike(articleId: String): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            try {
                articleDao.updateDislikeStatus(articleId, false)
                NetworkResult.Success(Unit)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to remove dislike: ${e.message}")
            }
        }

    override suspend fun clearReadingHistory(): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            try {
                // We don't actually delete articles, just mark them as unread
                // In a real app, you might want to track this differently
                val readArticles = articleDao.getReadArticles().first()
                readArticles.forEach { article ->
                    articleDao.updateReadStatus(article.id, false)
                }
                NetworkResult.Success(Unit)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to clear history: ${e.message}")
            }
        }

    override suspend fun getBookmarksCount(): Int = withContext(ioDispatcher) {
        articleDao.getBookmarkedArticles().first().size
    }

    override suspend fun getReadCount(): Int = withContext(ioDispatcher) {
        articleDao.getReadArticles().first().size
    }
}
