package com.aifeed.core.network.api

import com.aifeed.core.network.model.TweetSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface XApiService {

    @GET("2/tweets/search/recent")
    suspend fun searchRecentTweets(
        @Query("query") query: String,
        @Query("max_results") maxResults: Int = 10,
        @Query("tweet.fields") tweetFields: String = "created_at,public_metrics,author_id,attachments",
        @Query("expansions") expansions: String = "author_id,attachments.media_keys",
        @Query("user.fields") userFields: String = "name,username,profile_image_url,verified,public_metrics",
        @Query("media.fields") mediaFields: String = "preview_image_url,url,type"
    ): Response<TweetSearchResponse>
}
