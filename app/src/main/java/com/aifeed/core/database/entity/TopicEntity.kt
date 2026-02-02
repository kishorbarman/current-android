package com.aifeed.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "topics",
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["parentTopicId"])
    ]
)
data class TopicEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val slug: String,
    val icon: String,
    val parentTopicId: String? = null
)
