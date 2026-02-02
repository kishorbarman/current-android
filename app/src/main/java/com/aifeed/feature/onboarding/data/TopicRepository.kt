package com.aifeed.feature.onboarding.data

import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.database.entity.UserTopicEntity
import com.aifeed.core.network.NetworkResult
import kotlinx.coroutines.flow.Flow

interface TopicRepository {
    fun getAllTopics(): Flow<List<TopicEntity>>
    fun getUserTopics(userId: String): Flow<List<TopicEntity>>
    fun getUserTopicIds(userId: String): Flow<List<String>>

    suspend fun fetchAndCacheTopics(): NetworkResult<List<TopicEntity>>
    suspend fun saveUserTopics(userId: String, topicIds: List<String>): NetworkResult<Unit>
    suspend fun updateTopicWeight(userId: String, topicId: String, weight: Float): NetworkResult<Unit>
    suspend fun removeUserTopic(userId: String, topicId: String): NetworkResult<Unit>
}
