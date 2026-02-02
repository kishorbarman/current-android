package com.aifeed.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aifeed.core.database.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Query("SELECT * FROM topics ORDER BY name ASC")
    fun getAllTopics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY name ASC")
    suspend fun getAllTopicsOnce(): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getTopicById(id: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE slug = :slug")
    suspend fun getTopicBySlug(slug: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE parentTopicId IS NULL ORDER BY name ASC")
    fun getParentTopics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE parentTopicId = :parentId ORDER BY name ASC")
    fun getChildTopics(parentId: String): Flow<List<TopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: TopicEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun deleteTopic(id: String)

    @Query("DELETE FROM topics")
    suspend fun deleteAllTopics()

    @Query("SELECT COUNT(*) FROM topics")
    suspend fun getTopicCount(): Int
}
