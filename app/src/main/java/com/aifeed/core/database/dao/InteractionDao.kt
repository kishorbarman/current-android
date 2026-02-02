package com.aifeed.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aifeed.core.database.entity.InteractionEntity
import com.aifeed.core.database.entity.InteractionType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface InteractionDao {

    @Query("SELECT * FROM interactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getUserInteractions(userId: String): Flow<List<InteractionEntity>>

    @Query("SELECT * FROM interactions WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentInteractions(userId: String, limit: Int): List<InteractionEntity>

    @Query("SELECT * FROM interactions WHERE userId = :userId AND articleId = :articleId")
    suspend fun getInteractionsForArticle(userId: String, articleId: String): List<InteractionEntity>

    @Query("SELECT * FROM interactions WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedInteractions(): List<InteractionEntity>

    @Query("SELECT COUNT(*) FROM interactions WHERE synced = 0")
    fun getUnsyncedInteractionCount(): Flow<Int>

    @Query("""
        SELECT * FROM interactions
        WHERE userId = :userId AND type = :type
        ORDER BY timestamp DESC
    """)
    fun getInteractionsByType(userId: String, type: InteractionType): Flow<List<InteractionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: InteractionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteractions(interactions: List<InteractionEntity>)

    @Query("UPDATE interactions SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    @Query("UPDATE interactions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Query("DELETE FROM interactions WHERE id = :id")
    suspend fun deleteInteraction(id: String)

    @Query("DELETE FROM interactions WHERE timestamp < :before AND synced = 1")
    suspend fun deleteOldSyncedInteractions(before: Instant)

    @Query("DELETE FROM interactions WHERE userId = :userId")
    suspend fun deleteAllUserInteractions(userId: String)

    @Query("SELECT COUNT(*) FROM interactions WHERE userId = :userId")
    suspend fun getInteractionCount(userId: String): Int

    @Query("""
        SELECT COUNT(*) FROM interactions
        WHERE userId = :userId AND type = :type
    """)
    suspend fun getInteractionCountByType(userId: String, type: InteractionType): Int
}
