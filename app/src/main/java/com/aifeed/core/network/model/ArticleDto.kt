package com.aifeed.core.network.model

import com.aifeed.core.database.entity.ArticleEntity
import com.google.gson.annotations.SerializedName
import java.time.Instant

data class ArticleDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("preview")
    val preview: String?,
    @SerializedName("source_url")
    val sourceUrl: String,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("source_name")
    val sourceName: String?,
    @SerializedName("published_at")
    val publishedAt: String?,
    @SerializedName("primary_topic_id")
    val primaryTopicId: String?,
    @SerializedName("created_at")
    val createdAt: String?
) {
    fun toEntity(relevanceScore: Float = 0f): ArticleEntity {
        return ArticleEntity(
            id = id,
            title = title,
            preview = preview ?: "",
            sourceUrl = sourceUrl,
            imageUrl = imageUrl,
            sourceName = sourceName ?: "Unknown",
            publishedAt = publishedAt?.let {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    Instant.now()
                }
            } ?: Instant.now(),
            primaryTopicId = primaryTopicId ?: "",
            relevanceScore = relevanceScore,
            cachedAt = Instant.now()
        )
    }
}

data class ArticlesResponse(
    @SerializedName("articles")
    val articles: List<ArticleDto>?,
    @SerializedName("total")
    val total: Int?,
    @SerializedName("page")
    val page: Int?
)
