package com.aifeed.core.network.api

import com.aifeed.core.network.model.GeminiRequest
import com.aifeed.core.network.model.GeminiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface GeminiApiService {

    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
