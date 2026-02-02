package com.aifeed.core.network.model

import com.aifeed.core.database.entity.InteractionEntity
import com.google.gson.annotations.SerializedName

data class InteractionDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("article_id")
    val articleId: String,
    @SerializedName("interaction_type")
    val interactionType: String,
    @SerializedName("metadata")
    val metadata: Map<String, Any>?,
    @SerializedName("created_at")
    val createdAt: String?
) {
    companion object {
        fun fromEntity(entity: InteractionEntity): InteractionDto {
            return InteractionDto(
                id = entity.id,
                userId = entity.userId,
                articleId = entity.articleId,
                interactionType = entity.type.name.lowercase(),
                metadata = entity.metadata?.let { parseMetadata(it) },
                createdAt = entity.timestamp.toString()
            )
        }

        private fun parseMetadata(json: String): Map<String, Any>? {
            return try {
                com.google.gson.Gson().fromJson(
                    json,
                    object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class InteractionBatchRequest(
    @SerializedName("interactions")
    val interactions: List<InteractionDto>
)
