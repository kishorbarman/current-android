package com.aifeed.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "user_topics",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["topicId"]),
        Index(value = ["userId"])
    ]
)
data class UserTopicEntity(
    @PrimaryKey
    val topicId: String,
    val userId: String,
    val weight: Float = 1.0f,
    val selectedAt: Instant = Instant.now()
)
