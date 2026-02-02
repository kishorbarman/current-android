package com.aifeed.feature.onboarding.data

import com.aifeed.core.database.dao.TopicDao
import com.aifeed.core.database.dao.UserTopicDao
import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.database.entity.UserTopicEntity
import com.aifeed.core.di.IoDispatcher
import com.aifeed.core.network.NetworkResult
import com.aifeed.core.network.api.SupabaseApiService
import com.aifeed.core.network.model.UserTopicDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val topicDao: TopicDao,
    private val userTopicDao: UserTopicDao,
    private val supabaseApi: SupabaseApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TopicRepository {

    override fun getAllTopics(): Flow<List<TopicEntity>> {
        return topicDao.getAllTopics()
    }

    override fun getUserTopics(userId: String): Flow<List<TopicEntity>> {
        return userTopicDao.getUserTopicsWithDetails(userId)
    }

    override fun getUserTopicIds(userId: String): Flow<List<String>> {
        return userTopicDao.getUserTopicIds(userId)
    }

    override suspend fun fetchAndCacheTopics(): NetworkResult<List<TopicEntity>> =
        withContext(ioDispatcher) {
            try {
                val response = supabaseApi.getTopics()
                if (response.isSuccessful) {
                    val topics = response.body()?.map { it.toEntity() } ?: emptyList()
                    topicDao.insertTopics(topics)
                    NetworkResult.Success(topics)
                } else {
                    // Fall back to local cache
                    val cachedTopics = topicDao.getAllTopicsOnce()
                    if (cachedTopics.isNotEmpty()) {
                        NetworkResult.Success(cachedTopics)
                    } else {
                        // Insert default topics if no cache
                        val defaultTopics = getDefaultTopics()
                        topicDao.insertTopics(defaultTopics)
                        NetworkResult.Success(defaultTopics)
                    }
                }
            } catch (e: Exception) {
                // Fall back to local cache on error
                val cachedTopics = topicDao.getAllTopicsOnce()
                if (cachedTopics.isNotEmpty()) {
                    NetworkResult.Success(cachedTopics)
                } else {
                    // Insert default topics if no cache
                    val defaultTopics = getDefaultTopics()
                    topicDao.insertTopics(defaultTopics)
                    NetworkResult.Success(defaultTopics)
                }
            }
        }

    override suspend fun saveUserTopics(
        userId: String,
        topicIds: List<String>
    ): NetworkResult<Unit> = withContext(ioDispatcher) {
        try {
            val userTopics = topicIds.map { topicId ->
                UserTopicEntity(
                    topicId = topicId,
                    userId = userId,
                    weight = 1.0f,
                    selectedAt = Instant.now()
                )
            }

            // Save locally
            userTopicDao.replaceUserTopics(userId, userTopics)

            // Sync to remote
            try {
                supabaseApi.deleteUserTopics("eq.$userId")
                val dtos = topicIds.map { topicId ->
                    UserTopicDto(
                        userId = userId,
                        topicId = topicId,
                        weight = 1.0f
                    )
                }
                supabaseApi.insertUserTopics(dtos)
            } catch (e: Exception) {
                // Ignore remote sync errors for now
            }

            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to save topics: ${e.message}")
        }
    }

    override suspend fun updateTopicWeight(
        userId: String,
        topicId: String,
        weight: Float
    ): NetworkResult<Unit> = withContext(ioDispatcher) {
        try {
            userTopicDao.updateWeight(userId, topicId, weight)
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to update topic weight: ${e.message}")
        }
    }

    override suspend fun removeUserTopic(
        userId: String,
        topicId: String
    ): NetworkResult<Unit> = withContext(ioDispatcher) {
        try {
            userTopicDao.deleteUserTopic(userId, topicId)
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to remove topic: ${e.message}")
        }
    }

    private fun getDefaultTopics(): List<TopicEntity> {
        return listOf(
            TopicEntity("1", "Technology", "technology", "computer"),
            TopicEntity("2", "Science", "science", "science"),
            TopicEntity("3", "Business", "business", "business"),
            TopicEntity("4", "Sports", "sports", "sports"),
            TopicEntity("5", "Entertainment", "entertainment", "movie"),
            TopicEntity("6", "Health", "health", "health"),
            TopicEntity("7", "Politics", "politics", "gavel"),
            TopicEntity("8", "World News", "world", "public"),
            TopicEntity("9", "Gaming", "gaming", "gamepad"),
            TopicEntity("10", "Food", "food", "restaurant"),
            TopicEntity("11", "Travel", "travel", "flight"),
            TopicEntity("12", "Finance", "finance", "trending_up")
        )
    }
}
