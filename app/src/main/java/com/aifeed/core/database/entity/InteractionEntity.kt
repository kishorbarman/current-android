package com.aifeed.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class InteractionType {
    CLICK,
    READ,
    BOOKMARK,
    UNBOOKMARK,
    SHARE,
    LIKE,
    DISLIKE
}

@Entity(
    tableName = "interactions",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["articleId"]),
        Index(value = ["timestamp"]),
        Index(value = ["synced"])
    ]
)
data class InteractionEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val articleId: String,
    val type: InteractionType,
    val metadata: String? = null,
    val timestamp: Instant = Instant.now(),
    val synced: Boolean = false
)
