package com.aifeed.feature.trending.data

import com.aifeed.core.database.entity.TrendingTopicEntity
import com.aifeed.core.database.entity.TrendingTweetEntity
import com.aifeed.core.network.NetworkResult
import kotlinx.coroutines.flow.Flow

interface TrendingRepository {
    fun getTrendingTopics(): Flow<List<TrendingTopicEntity>>
    suspend fun getTopicById(topicId: String): TrendingTopicEntity?
    suspend fun getTweetsForTopic(topicId: String): List<TrendingTweetEntity>
    suspend fun refreshTrending(): NetworkResult<Unit>
    suspend fun isCacheStale(): Boolean
}
