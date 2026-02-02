package com.aifeed.core.network.model

import com.aifeed.core.database.entity.TopicEntity
import com.google.gson.annotations.SerializedName

data class TopicDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("slug")
    val slug: String,
    @SerializedName("icon")
    val icon: String?,
    @SerializedName("parent_topic_id")
    val parentTopicId: String?
) {
    fun toEntity(): TopicEntity {
        return TopicEntity(
            id = id,
            name = name,
            slug = slug,
            icon = icon ?: "category",
            parentTopicId = parentTopicId
        )
    }
}
