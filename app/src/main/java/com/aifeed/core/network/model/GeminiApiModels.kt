package com.aifeed.core.network.model

import com.google.gson.annotations.SerializedName

// Request models
data class GeminiRequest(
    @SerializedName("contents")
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    @SerializedName("parts")
    val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text")
    val text: String
)

data class GeminiGenerationConfig(
    @SerializedName("temperature")
    val temperature: Float? = null,
    @SerializedName("responseMimeType")
    val responseMimeType: String? = null
)

// Response models
data class GeminiResponse(
    @SerializedName("candidates")
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    @SerializedName("content")
    val content: GeminiCandidateContent?
)

data class GeminiCandidateContent(
    @SerializedName("parts")
    val parts: List<GeminiPart>?
)

// Parsed summary model (deserialized from Gemini's JSON response)
data class TrendingSummaryBatch(
    @SerializedName("summaries")
    val summaries: List<TrendingSummary>
)

data class TrendingSummary(
    @SerializedName("trend_name")
    val trendName: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("summary")
    val summary: String,
    @SerializedName("key_points")
    val keyPoints: List<String>,
    @SerializedName("sentiment")
    val sentiment: String
)
