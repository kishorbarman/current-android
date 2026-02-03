package com.aifeed.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["primaryTopicId"]),
        Index(value = ["publishedAt"]),
        Index(value = ["sourceUrl"], unique = true)
    ]
)
data class ArticleEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val preview: String,
    val sourceUrl: String,
    val imageUrl: String?,
    val sourceName: String,
    val publishedAt: Instant,
    val primaryTopicId: String,
    val relevanceScore: Float = 0f,
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val cachedAt: Instant = Instant.now()
)
