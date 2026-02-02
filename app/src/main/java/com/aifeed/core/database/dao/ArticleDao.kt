package com.aifeed.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aifeed.core.database.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles ORDER BY relevanceScore DESC, publishedAt DESC")
    fun getArticlesPaged(): PagingSource<Int, ArticleEntity>

    @Query("""
        SELECT * FROM articles
        WHERE primaryTopicId = :topicId
        ORDER BY relevanceScore DESC, publishedAt DESC
    """)
    fun getArticlesByTopicPaged(topicId: String): PagingSource<Int, ArticleEntity>

    @Query("""
        SELECT * FROM articles
        WHERE primaryTopicId IN (:topicIds)
        ORDER BY relevanceScore DESC, publishedAt DESC
    """)
    fun getArticlesByTopicsPaged(topicIds: List<String>): PagingSource<Int, ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :id")
    fun getArticleById(id: String): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleByIdOnce(id: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY cachedAt DESC")
    fun getBookmarkedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY cachedAt DESC")
    fun getBookmarkedArticlesPaged(): PagingSource<Int, ArticleEntity>

    @Query("SELECT * FROM articles WHERE isRead = 1 ORDER BY cachedAt DESC")
    fun getReadArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isRead = 1 ORDER BY cachedAt DESC")
    fun getReadArticlesPaged(): PagingSource<Int, ArticleEntity>

    @Query("""
        SELECT * FROM articles
        WHERE title LIKE '%' || :query || '%'
           OR preview LIKE '%' || :query || '%'
        ORDER BY publishedAt DESC
    """)
    fun searchArticles(query: String): Flow<List<ArticleEntity>>

    @Query("""
        SELECT * FROM articles
        WHERE title LIKE '%' || :query || '%'
           OR preview LIKE '%' || :query || '%'
        ORDER BY publishedAt DESC
    """)
    fun searchArticlesPaged(query: String): PagingSource<Int, ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Update
    suspend fun updateArticle(article: ArticleEntity)

    @Query("UPDATE articles SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: String, isBookmarked: Boolean)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :id")
    suspend fun updateReadStatus(id: String, isRead: Boolean)

    @Query("UPDATE articles SET relevanceScore = :score WHERE id = :id")
    suspend fun updateRelevanceScore(id: String, score: Float)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteArticle(id: String)

    @Query("DELETE FROM articles WHERE cachedAt < :before AND isBookmarked = 0")
    suspend fun deleteOldArticles(before: Instant)

    @Query("DELETE FROM articles")
    suspend fun deleteAllArticles()

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun getArticleCount(): Int

    @Query("SELECT COUNT(*) FROM articles WHERE primaryTopicId = :topicId")
    suspend fun getArticleCountByTopic(topicId: String): Int
}
