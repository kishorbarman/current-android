package com.aifeed.core.network.model

import com.google.gson.annotations.SerializedName

data class TweetSearchResponse(
    @SerializedName("data")
    val data: List<XTweet>?,
    @SerializedName("includes")
    val includes: TweetIncludes?,
    @SerializedName("meta")
    val meta: TweetSearchMeta?
)

data class XTweet(
    @SerializedName("id")
    val id: String,
    @SerializedName("text")
    val text: String,
    @SerializedName("author_id")
    val authorId: String,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("public_metrics")
    val publicMetrics: TweetPublicMetrics?,
    @SerializedName("attachments")
    val attachments: TweetAttachments?
)

data class TweetIncludes(
    @SerializedName("users")
    val users: List<XUser>?,
    @SerializedName("media")
    val media: List<XMedia>?
)

data class XUser(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("profile_image_url")
    val profileImageUrl: String?,
    @SerializedName("verified")
    val verified: Boolean?,
    @SerializedName("public_metrics")
    val publicMetrics: XUserPublicMetrics?
)

data class XUserPublicMetrics(
    @SerializedName("followers_count")
    val followersCount: Int?
)

data class XMedia(
    @SerializedName("media_key")
    val mediaKey: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("url")
    val url: String?,
    @SerializedName("preview_image_url")
    val previewImageUrl: String?
)

data class TweetPublicMetrics(
    @SerializedName("like_count")
    val likeCount: Int,
    @SerializedName("retweet_count")
    val retweetCount: Int,
    @SerializedName("reply_count")
    val replyCount: Int
)

data class TweetAttachments(
    @SerializedName("media_keys")
    val mediaKeys: List<String>?
)

data class TweetSearchMeta(
    @SerializedName("result_count")
    val resultCount: Int,
    @SerializedName("newest_id")
    val newestId: String?,
    @SerializedName("oldest_id")
    val oldestId: String?
)
