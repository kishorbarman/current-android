package com.aifeed.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aifeed.core.database.entity.TrendingTopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrendingTopicDao {

    @Query("SELECT * FROM trending_topics ORDER BY tweetCount DESC")
    fun getAllTrendingTopics(): Flow<List<TrendingTopicEntity>>

    @Query("SELECT * FROM trending_topics WHERE id = :topicId LIMIT 1")
    suspend fun getTrendingTopicById(topicId: String): TrendingTopicEntity?

    @Query("SELECT MAX(cachedAt) FROM trending_topics")
    suspend fun getLatestCacheTime(): Long?

    @Transaction
    suspend fun replaceAllTrendingTopics(topics: List<TrendingTopicEntity>) {
        deleteAllTrendingTopics()
        insertTrendingTopics(topics)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrendingTopics(topics: List<TrendingTopicEntity>)

    @Query("DELETE FROM trending_topics WHERE cachedAt < :minCachedAtEpochMillis")
    suspend fun deleteTopicsOlderThan(minCachedAtEpochMillis: Long)

    @Query("DELETE FROM trending_topics")
    suspend fun deleteAllTrendingTopics()
}
