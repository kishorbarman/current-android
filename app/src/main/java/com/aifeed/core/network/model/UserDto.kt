package com.aifeed.core.network.model

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("display_name")
    val displayName: String?,
    @SerializedName("avatar_url")
    val avatarUrl: String?,
    @SerializedName("onboarding_completed")
    val onboardingCompleted: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String?
)

data class UserTopicDto(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("topic_id")
    val topicId: String,
    @SerializedName("weight")
    val weight: Float = 1.0f
)

data class UserTopicsUpdateRequest(
    @SerializedName("topic_ids")
    val topicIds: List<String>
)
