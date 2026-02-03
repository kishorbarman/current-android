package com.aifeed.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "user_sources",
    primaryKeys = ["userId", "sourceName"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["sourceName"])
    ]
)
data class UserSourceEntity(
    val userId: String,
    val sourceName: String,
    val weight: Float = 1.0f,
    val updatedAt: Instant = Instant.now()
)
