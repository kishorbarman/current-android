package com.aifeed.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "trending_topics",
    indices = [
        Index(value = ["trendName"], unique = true)
    ]
)
data class TrendingTopicEntity(
    @PrimaryKey
    val id: String,
    val trendName: String,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val sentiment: String,
    val tweetCount: Int,
    val category: String,
    val cachedAt: Instant = Instant.now()
)
