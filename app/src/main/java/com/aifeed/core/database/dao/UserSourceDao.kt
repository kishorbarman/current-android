package com.aifeed.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aifeed.core.database.entity.UserSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSourceDao {

    @Query("SELECT * FROM user_sources WHERE userId = :userId")
    fun getUserSources(userId: String): Flow<List<UserSourceEntity>>

    @Query("SELECT * FROM user_sources WHERE userId = :userId")
    suspend fun getUserSourcesOnce(userId: String): List<UserSourceEntity>

    @Query("SELECT * FROM user_sources WHERE userId = :userId AND sourceName = :sourceName")
    suspend fun getUserSource(userId: String, sourceName: String): UserSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserSource(userSource: UserSourceEntity)

    @Query("UPDATE user_sources SET weight = :weight, updatedAt = :updatedAt WHERE userId = :userId AND sourceName = :sourceName")
    suspend fun updateWeight(userId: String, sourceName: String, weight: Float, updatedAt: java.time.Instant = java.time.Instant.now())

    @Query("DELETE FROM user_sources WHERE userId = :userId")
    suspend fun deleteUserSources(userId: String)
}
