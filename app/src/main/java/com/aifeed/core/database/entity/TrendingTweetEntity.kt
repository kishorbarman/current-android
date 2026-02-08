package com.aifeed.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "trending_tweets",
    foreignKeys = [
        ForeignKey(
            entity = TrendingTopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tweetId"], unique = true),
        Index(value = ["topicId"])
    ]
)
data class TrendingTweetEntity(
    @PrimaryKey
    val id: String,
    val tweetId: String,
    val topicId: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String?,
    val text: String,
    val mediaUrl: String?,
    val likeCount: Int,
    val retweetCount: Int,
    val replyCount: Int,
    val tweetUrl: String,
    val publishedAt: Instant,
    val cachedAt: Instant = Instant.now()
)
