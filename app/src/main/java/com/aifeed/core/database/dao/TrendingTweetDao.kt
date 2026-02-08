package com.aifeed.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aifeed.core.database.entity.TrendingTweetEntity

@Dao
interface TrendingTweetDao {

    @Query("SELECT * FROM trending_tweets WHERE topicId = :topicId ORDER BY likeCount DESC")
    suspend fun getTweetsForTopicOnce(topicId: String): List<TrendingTweetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTweets(tweets: List<TrendingTweetEntity>)

    @Query("DELETE FROM trending_tweets WHERE cachedAt < :minCachedAtEpochMillis")
    suspend fun deleteTweetsOlderThan(minCachedAtEpochMillis: Long)

    @Query("DELETE FROM trending_tweets")
    suspend fun deleteAllTweets()
}
