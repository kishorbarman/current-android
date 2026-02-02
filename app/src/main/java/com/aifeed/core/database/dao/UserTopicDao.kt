package com.aifeed.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.database.entity.UserTopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserTopicDao {

    @Query("SELECT * FROM user_topics WHERE userId = :userId ORDER BY weight DESC")
    fun getUserTopics(userId: String): Flow<List<UserTopicEntity>>

    @Query("SELECT * FROM user_topics WHERE userId = :userId ORDER BY weight DESC")
    suspend fun getUserTopicsOnce(userId: String): List<UserTopicEntity>

    @Query("SELECT topicId FROM user_topics WHERE userId = :userId")
    fun getUserTopicIds(userId: String): Flow<List<String>>

    @Query("SELECT topicId FROM user_topics WHERE userId = :userId")
    suspend fun getUserTopicIdsOnce(userId: String): List<String>

    @Query("""
        SELECT t.* FROM topics t
        INNER JOIN user_topics ut ON t.id = ut.topicId
        WHERE ut.userId = :userId
        ORDER BY ut.weight DESC
    """)
    fun getUserTopicsWithDetails(userId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM user_topics WHERE userId = :userId AND topicId = :topicId")
    suspend fun getUserTopic(userId: String, topicId: String): UserTopicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserTopic(userTopic: UserTopicEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserTopics(userTopics: List<UserTopicEntity>)

    @Query("UPDATE user_topics SET weight = :weight WHERE userId = :userId AND topicId = :topicId")
    suspend fun updateWeight(userId: String, topicId: String, weight: Float)

    @Query("DELETE FROM user_topics WHERE userId = :userId AND topicId = :topicId")
    suspend fun deleteUserTopic(userId: String, topicId: String)

    @Query("DELETE FROM user_topics WHERE userId = :userId")
    suspend fun deleteAllUserTopics(userId: String)

    @Transaction
    suspend fun replaceUserTopics(userId: String, userTopics: List<UserTopicEntity>) {
        deleteAllUserTopics(userId)
        insertUserTopics(userTopics)
    }

    @Query("SELECT COUNT(*) FROM user_topics WHERE userId = :userId")
    suspend fun getUserTopicCount(userId: String): Int
}
