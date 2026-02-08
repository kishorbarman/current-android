package com.aifeed.feature.trending.data

import com.aifeed.core.database.dao.TrendingTopicDao
import com.aifeed.core.database.dao.TrendingTweetDao
import com.aifeed.core.database.entity.TrendingTopicEntity
import com.aifeed.core.database.entity.TrendingTweetEntity
import com.aifeed.core.di.IoDispatcher
import com.aifeed.core.network.NetworkResult
import com.aifeed.core.network.api.GeminiApiService
import com.aifeed.core.network.api.XApiService
import com.aifeed.core.network.model.GeminiContent
import com.aifeed.core.network.model.GeminiGenerationConfig
import com.aifeed.core.network.model.GeminiPart
import com.aifeed.core.network.model.GeminiRequest
import com.aifeed.core.network.model.TweetSearchResponse
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.max

@Singleton
class TrendingRepositoryImpl @Inject constructor(
    private val trendingTopicDao: TrendingTopicDao,
    private val trendingTweetDao: TrendingTweetDao,
    private val xApiService: XApiService,
    private val geminiApiService: GeminiApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TrendingRepository {

    private val gson = Gson()

    companion object {
        private const val CACHE_TTL_MINUTES = 120L
        private const val MAX_BATCH_RESULTS = 100
        private const val MAX_TWEETS_FOR_CLUSTERING = 220
        private const val MAX_TWEETS_FOR_GEMINI = 90
        private const val MAX_TOPICS = 40
        private const val MIN_TWEETS_PER_TOPIC = 1
        private const val MIN_TOPIC_TARGET = 20
        private const val FETCH_BATCH_CONCURRENCY = 3
    }

    // Curated mix of top global news wires, institutions, and major tech/company accounts.
    private val authoritativeAccounts = listOf(
        "Reuters", "AP", "BBCWorld", "cnnbrk", "nytimes", "FT", "WSJ",
        "NPR", "AlJazeera", "TheEconomist", "Bloomberg", "TechCrunch",
        "verge", "WIRED", "Nature", "sciencemagazine", "NASA", "WHO",
        "UN", "OpenAI", "google", "Microsoft", "Meta", "YouTube"
    )

    // Higher weight means the account is treated as more authoritative in ranking.
    private val trustedSourceWeights = mapOf(
        "reuters" to 1.0,
        "ap" to 1.0,
        "bbcworld" to 0.95,
        "cnnbrk" to 0.9,
        "nytimes" to 0.9,
        "ft" to 0.9,
        "wsj" to 0.9,
        "npr" to 0.85,
        "aljazeera" to 0.85,
        "theeconomist" to 0.85,
        "bloomberg" to 0.9,
        "nature" to 0.95,
        "sciencemagazine" to 0.95,
        "nasa" to 0.95,
        "who" to 0.95,
        "un" to 0.95,
        "openai" to 0.85,
        "google" to 0.8,
        "microsoft" to 0.8,
        "meta" to 0.8,
        "youtube" to 0.75,
        "techcrunch" to 0.8,
        "verge" to 0.75,
        "wired" to 0.75
    )

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var refineJob: Job? = null

    override fun getTrendingTopics(): Flow<List<TrendingTopicEntity>> {
        return trendingTopicDao.getAllTrendingTopics()
    }

    override suspend fun getTopicById(topicId: String): TrendingTopicEntity? =
        withContext(ioDispatcher) {
            trendingTopicDao.getTrendingTopicById(topicId)
        }

    override suspend fun getTweetsForTopic(topicId: String): List<TrendingTweetEntity> =
        withContext(ioDispatcher) {
            trendingTweetDao.getTweetsForTopicOnce(topicId)
        }

    override suspend fun isCacheStale(): Boolean = withContext(ioDispatcher) {
        val latestCacheTime = trendingTopicDao.getLatestCacheTime() ?: return@withContext true
        val cachedAt = Instant.ofEpochMilli(latestCacheTime)
        val staleThreshold = Instant.now().minusSeconds(CACHE_TTL_MINUTES * 60)
        cachedAt.isBefore(staleThreshold)
    }

    override suspend fun refreshTrending(): NetworkResult<Unit> = withContext(ioDispatcher) {
        try {
            val collectedTweets = fetchAuthoritativeTweetsParallel()
            if (collectedTweets.isEmpty()) {
                return@withContext NetworkResult.Error("Failed to fetch authoritative posts from X")
            }

            val clusteringCandidates = collectedTweets
                .groupBy { it.tweetId }
                .map { (_, tweets) -> tweets.maxByOrNull { it.rankingScore } ?: tweets.first() }
                .sortedByDescending { it.rankingScore }
                .take(MAX_TWEETS_FOR_CLUSTERING)

            if (clusteringCandidates.isEmpty()) {
                return@withContext NetworkResult.Error("No usable posts found from X")
            }

            // Phase 1: quick heuristic snapshot so UI updates fast.
            val quickSnapshot = buildTopicSnapshot(
                candidates = clusteringCandidates,
                clusters = fallbackClusters(clusteringCandidates)
            )
            if (quickSnapshot == null) {
                return@withContext NetworkResult.Error("No trend clusters could be generated")
            }
            persistSnapshot(quickSnapshot)

            // Phase 2: refine in background with Gemini and overwrite incrementally when ready.
            launchGeminiRefinement(clusteringCandidates)

            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to refresh trending: ${e.message}")
        }
    }

    private suspend fun fetchAuthoritativeTweetsParallel(): List<TweetData> = coroutineScope {
        val semaphore = Semaphore(FETCH_BATCH_CONCURRENCY)
        val batches = authoritativeAccounts.chunked(8)

        batches.map { batch ->
            async {
                semaphore.withPermit {
                    try {
                        val fromClause = batch.joinToString(" OR ") { "from:$it" }
                        val response = xApiService.searchRecentTweets(
                            query = "($fromClause) -is:retweet -is:reply lang:en",
                            maxResults = MAX_BATCH_RESULTS
                        )
                        if (response.isSuccessful) {
                            parseTweets(response.body())
                        } else {
                            emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
        }.awaitAll().flatten()
    }

    private fun launchGeminiRefinement(candidates: List<TweetData>) {
        refineJob?.cancel()
        refineJob = repositoryScope.launch {
            try {
                val geminiCandidates = candidates.take(MAX_TWEETS_FOR_GEMINI)
                if (geminiCandidates.isEmpty()) return@launch

                val geminiClusters = summarizeWithGemini(geminiCandidates)
                if (geminiClusters.isEmpty()) return@launch

                val refinedSnapshot = buildTopicSnapshot(
                    candidates = candidates,
                    clusters = geminiClusters
                ) ?: return@launch

                persistSnapshot(refinedSnapshot)
            } catch (_: Exception) {
                // Keep quick snapshot if refinement fails.
            }
        }
    }

    private suspend fun persistSnapshot(snapshot: TopicSnapshot) {
        if (snapshot.topics.isEmpty()) return
        trendingTopicDao.insertTrendingTopics(snapshot.topics)
        if (snapshot.tweets.isNotEmpty()) {
            trendingTweetDao.insertTweets(snapshot.tweets)
        }
        trendingTweetDao.deleteTweetsOlderThan(snapshot.snapshotEpochMillis)
        trendingTopicDao.deleteTopicsOlderThan(snapshot.snapshotEpochMillis)
    }

    private fun buildTopicSnapshot(
        candidates: List<TweetData>,
        clusters: List<TopicCluster>
    ): TopicSnapshot? {
        val tweetById = candidates.associateBy { it.tweetId }
        val selectedClusters = clusters
            .mapIndexed { index, cluster ->
                cluster.copy(
                    topicId = normalizeTopicId(cluster.topicId, cluster.title, index)
                )
            }
            .distinctBy { it.topicId }
            .take(MAX_TOPICS)

        val snapshotEpochMillis = System.currentTimeMillis()
        val snapshotInstant = Instant.ofEpochMilli(snapshotEpochMillis)
        val topicEntities = mutableListOf<TrendingTopicEntity>()
        val tweetEntities = mutableListOf<TrendingTweetEntity>()
        val assignedTweetIds = mutableSetOf<String>()
        val tweetsByTopicKey = mutableMapOf<String, List<TweetData>>()

        selectedClusters.forEach { cluster ->
            val linkedTweets = cluster.tweetIds
                .mapNotNull { tweetById[it] }
                .distinctBy { it.tweetId }
                .filter { it.tweetId !in assignedTweetIds }
                .sortedByDescending { it.rankingScore }

            if (linkedTweets.size < MIN_TWEETS_PER_TOPIC) return@forEach
            assignedTweetIds += linkedTweets.map { it.tweetId }
            tweetsByTopicKey[cluster.topicId] = linkedTweets

            topicEntities += TrendingTopicEntity(
                id = UUID.randomUUID().toString(),
                trendName = cluster.topicId,
                title = cluster.title.ifBlank { "Trending Topic" },
                summary = cluster.summary.ifBlank { "Top posts from authoritative X sources." },
                keyPoints = cluster.keyPoints.take(4),
                sentiment = sanitizeSentiment(cluster.sentiment),
                tweetCount = linkedTweets.size,
                category = cluster.category.ifBlank { inferCategoryFromText(cluster.title + " " + cluster.summary) },
                cachedAt = snapshotInstant
            )
        }

        if (topicEntities.size < MIN_TOPIC_TARGET) {
            val remainingTweets = candidates
                .filter { it.tweetId !in assignedTweetIds }
                .sortedByDescending { it.rankingScore }

            val needed = MIN_TOPIC_TARGET - topicEntities.size
            remainingTweets.take(needed).forEachIndexed { idx, tweet ->
                val topicKey = "single-${tweet.tweetId}-${idx + 1}"
                topicEntities += TrendingTopicEntity(
                    id = UUID.randomUUID().toString(),
                    trendName = topicKey,
                    title = buildSingleTweetTopicTitle(tweet),
                    summary = buildSingleTweetSummary(tweet),
                    keyPoints = listOf(
                        "Source: @${tweet.authorHandle}",
                        "High-signal update from authoritative account"
                    ),
                    sentiment = "neutral",
                    tweetCount = 1,
                    category = inferCategoryFromText("${tweet.text} ${tweet.authorName}"),
                    cachedAt = snapshotInstant
                )
                tweetsByTopicKey[topicKey] = listOf(tweet)
            }
        }

        if (topicEntities.size < MIN_TOPIC_TARGET && candidates.isNotEmpty()) {
            var idx = 0
            while (topicEntities.size < MIN_TOPIC_TARGET) {
                val tweet = candidates[idx % candidates.size]
                val topicKey = "extra-${tweet.tweetId}-${idx + 1}"
                if (tweetsByTopicKey.containsKey(topicKey)) {
                    idx++
                    continue
                }
                topicEntities += TrendingTopicEntity(
                    id = UUID.randomUUID().toString(),
                    trendName = topicKey,
                    title = buildSingleTweetTopicTitle(tweet),
                    summary = "Additional high-signal topic extracted from authoritative X sources.",
                    keyPoints = listOf("Source: @${tweet.authorHandle}"),
                    sentiment = "neutral",
                    tweetCount = 1,
                    category = inferCategoryFromText(tweet.text),
                    cachedAt = snapshotInstant
                )
                tweetsByTopicKey[topicKey] = listOf(tweet)
                idx++
            }
        }

        if (topicEntities.isEmpty()) return null

        val topicIdByTrendName = topicEntities.associateBy { it.trendName }.mapValues { it.value.id }
        for ((topicKey, topicTweets) in tweetsByTopicKey) {
            val roomTopicId = topicIdByTrendName[topicKey] ?: continue
            topicTweets.forEach { tweet ->
                tweetEntities += TrendingTweetEntity(
                    id = UUID.randomUUID().toString(),
                    tweetId = tweet.tweetId,
                    topicId = roomTopicId,
                    authorName = tweet.authorName,
                    authorHandle = tweet.authorHandle,
                    authorAvatarUrl = tweet.authorAvatarUrl,
                    text = tweet.text,
                    mediaUrl = tweet.mediaUrl,
                    likeCount = tweet.likeCount,
                    retweetCount = tweet.retweetCount,
                    replyCount = tweet.replyCount,
                    tweetUrl = "https://twitter.com/${tweet.authorHandle}/status/${tweet.tweetId}",
                    publishedAt = tweet.publishedAt,
                    cachedAt = snapshotInstant
                )
            }
        }

        return TopicSnapshot(
            topics = topicEntities,
            tweets = tweetEntities,
            snapshotEpochMillis = snapshotEpochMillis
        )
    }

    private fun parseTweets(response: TweetSearchResponse?): List<TweetData> {
        if (response?.data == null) return emptyList()

        val userMap = response.includes?.users?.associateBy { it.id } ?: emptyMap()
        val mediaMap = response.includes?.media?.associateBy { it.mediaKey } ?: emptyMap()

        return response.data.map { tweet ->
            val author = userMap[tweet.authorId]
            val mediaUrl = tweet.attachments?.mediaKeys?.firstOrNull()?.let { key ->
                val media = mediaMap[key]
                media?.url ?: media?.previewImageUrl
            }

            val likeCount = max(tweet.publicMetrics?.likeCount ?: 0, 0)
            val retweetCount = max(tweet.publicMetrics?.retweetCount ?: 0, 0)
            val replyCount = max(tweet.publicMetrics?.replyCount ?: 0, 0)
            val authorHandle = author?.username ?: "unknown"
            val handleWeight = trustedSourceWeights[authorHandle.lowercase(Locale.US)] ?: 0.5
            val followerCount = max(author?.publicMetrics?.followersCount ?: 0, 0)
            val followerSignal = ln(1.0 + followerCount.toDouble()).coerceAtMost(15.0) / 15.0
            val verifiedBonus = if (author?.verified == true) 0.15 else 0.0
            val sourceQualityScore = (handleWeight + followerSignal + verifiedBonus).coerceIn(0.2, 2.0)

            TweetData(
                tweetId = tweet.id,
                text = tweet.text,
                authorName = author?.name ?: authorHandle,
                authorHandle = authorHandle,
                authorAvatarUrl = author?.profileImageUrl,
                mediaUrl = mediaUrl,
                likeCount = likeCount,
                retweetCount = retweetCount,
                replyCount = replyCount,
                sourceQualityScore = sourceQualityScore,
                publishedAt = tweet.createdAt?.let {
                    try {
                        Instant.parse(it)
                    } catch (_: Exception) {
                        Instant.now()
                    }
                } ?: Instant.now()
            )
        }
    }

    private suspend fun summarizeWithGemini(tweets: List<TweetData>): List<TopicCluster> {
        val prompt = buildGeminiPrompt(tweets)

        try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.2f,
                    responseMimeType = "application/json"
                )
            )

            val response = geminiApiService.generateContent(request)
            if (response.isSuccessful) {
                val responseText = response.body()
                    ?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()
                    ?.text

                if (!responseText.isNullOrBlank()) {
                    val parsed = gson.fromJson(responseText, GeminiTopicBatch::class.java)
                    val topics = parsed?.topics.orEmpty()
                        .mapNotNull { it.toTopicClusterOrNull(tweets.map { t -> t.tweetId }.toSet()) }
                        .filterNot { isGenericClusterTitle(it.title) }
                        .filter { it.tweetIds.size >= MIN_TWEETS_PER_TOPIC }

                    if (topics.isNotEmpty()) {
                        return topics
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to deterministic clustering below.
        }

        return emptyList()
    }

    private fun fallbackClusters(tweets: List<TweetData>): List<TopicCluster> {
        val grouped = tweets
            .groupBy { inferCategoryFromText(it.text) }
            .entries
            .sortedByDescending { (_, groupedTweets) -> groupedTweets.sumOf { it.rankingScore } }

        val clusters = mutableListOf<TopicCluster>()
        for ((category, groupedTweets) in grouped) {
            val topTweets = groupedTweets
                .sortedByDescending { it.rankingScore }
                .take(8)

            if (topTweets.size < MIN_TWEETS_PER_TOPIC) continue

            val title = buildSingleTweetTopicTitle(topTweets.first())

            val accounts = topTweets.map { it.authorHandle }.distinct().take(3)
            val leadSnippet = topTweets.first().text
                .replace(Regex("https?://\\S+"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(180)
            val summary = "Posts from ${accounts.joinToString(", ") { "@$it" }} are converging on this event: \"$leadSnippet\". " +
                "Open the topic to review all related source posts."

            clusters += TopicCluster(
                topicId = "fallback-${slugify(category)}",
                title = title,
                summary = summary,
                keyPoints = listOf(
                    "Cluster built from high-engagement posts",
                    "Sources are curated authoritative accounts",
                    "Topic should be reviewed as updates continue"
                ),
                sentiment = "neutral",
                category = category,
                tweetIds = topTweets.map { it.tweetId }
            )
        }

        if (clusters.isNotEmpty()) {
            return clusters.take(MAX_TOPICS)
        }

        val topTweets = tweets.sortedByDescending { it.rankingScore }.take(10)
        return listOf(
            TopicCluster(
                topicId = "fallback-general",
                title = "Top Updates from Authoritative X Accounts",
                summary = "This is a broad snapshot of the most engaged recent posts from trusted sources.",
                keyPoints = listOf("High-engagement authoritative posts", "Refresh to update topic grouping"),
                sentiment = "neutral",
                category = "General",
                tweetIds = topTweets.map { it.tweetId }
            )
        )
    }

    private fun buildGeminiPrompt(tweets: List<TweetData>): String {
        val tweetJsonLines = tweets.joinToString("\n") { tweet ->
            gson.toJson(
                mapOf(
                    "tweet_id" to tweet.tweetId,
                    "author_handle" to tweet.authorHandle,
                    "author_name" to tweet.authorName,
                    "published_at" to tweet.publishedAt.toString(),
                    "like_count" to tweet.likeCount,
                    "retweet_count" to tweet.retweetCount,
                    "reply_count" to tweet.replyCount,
                    "source_quality_score" to tweet.sourceQualityScore,
                    "text" to tweet.text
                )
            )
        }

        return """
You are a breaking-news analyst. You are given recent posts from authoritative X accounts.
Your task is to identify the top real-world trending topics/events, group related posts, and summarize each topic.
Focus on concrete events with specific actors, places, actions, or announcements.

Return JSON only with this schema:
{
  "topics": [
    {
      "topic_id": "short-stable-slug",
      "title": "clear headline",
      "summary": "2-3 sentence summary of what happened and why it matters",
      "key_points": ["point 1", "point 2", "point 3"],
      "sentiment": "positive|negative|neutral|mixed",
      "category": "World|Technology|Business|Science|Health|Politics|Sports|General",
      "tweet_ids": ["id1", "id2", "id3"]
    }
  ]
}

Rules:
- Use ONLY tweet_ids present in the input.
- Group by shared event/topic, not by account.
- Prefer concrete events (e.g., earthquake, model launch, policy decision).
- Title MUST read like a specific headline and include at least one concrete identifier:
  person/org name, location, product/model name, treaty/deal name, or event name.
- Do NOT output generic umbrella titles such as:
  "Technology updates", "World news", "Business highlights", "Product updates", "Market updates".
- Good title examples:
  "OpenAI launches Codex 5.3 for developers"
  "US and UK officials discuss new peace framework"
  "San Jose builds excitement ahead of Super Bowl week"
- Create 20-30 topics when possible.
- Each topic should include 1-8 tweet_ids.
- Avoid duplicate topics that describe the same event.
- Keep summaries factual and concise.

Input posts (JSON lines):
$tweetJsonLines
""".trimIndent()
    }

    private fun inferCategoryFromText(text: String): String {
        val lower = text.lowercase(Locale.US)
        return when {
            listOf("earthquake", "war", "election", "summit", "government", "united nations").any { it in lower } -> "World"
            listOf("openai", "model", "ai", "google", "microsoft", "apple", "launch", "chip").any { it in lower } -> "Technology"
            listOf("market", "stocks", "earnings", "inflation", "fed", "economy").any { it in lower } -> "Business"
            listOf("study", "research", "nasa", "space", "climate", "science").any { it in lower } -> "Science"
            listOf("health", "who", "disease", "outbreak", "vaccine").any { it in lower } -> "Health"
            else -> "General"
        }
    }

    private fun sanitizeSentiment(sentiment: String): String {
        return when (sentiment.lowercase(Locale.US)) {
            "positive", "negative", "neutral", "mixed" -> sentiment.lowercase(Locale.US)
            else -> "neutral"
        }
    }

    private fun slugify(value: String): String {
        return value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "topic" }
    }

    private fun normalizeTopicId(rawTopicId: String, title: String, index: Int): String {
        val candidate = rawTopicId.trim()
        if (candidate.isNotBlank()) return slugify(candidate)
        return "topic-${index + 1}-${slugify(title)}"
    }

    private fun buildSingleTweetTopicTitle(tweet: TweetData): String {
        val cleaned = tweet.text
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(90).ifBlank { "Update from @${tweet.authorHandle}" }
    }

    private fun buildSingleTweetSummary(tweet: TweetData): String {
        val snippet = tweet.text
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
        return "This high-signal post from @${tweet.authorHandle} is trending now. " +
            "Key update: \"$snippet\". Open to review the original source and monitor follow-up developments."
    }

    private fun isGenericClusterTitle(title: String): Boolean {
        val normalized = title.lowercase(Locale.US).trim()
        if (normalized.isBlank()) return true

        val blockedPhrases = listOf(
            "technology update", "technology updates", "product updates", "tech updates",
            "world update", "world updates", "world news", "global updates",
            "business update", "business updates", "market updates",
            "science updates", "health updates", "general updates", "top updates"
        )
        return blockedPhrases.any { normalized == it || normalized.contains(it) }
    }

    private data class TweetData(
        val tweetId: String,
        val text: String,
        val authorName: String,
        val authorHandle: String,
        val authorAvatarUrl: String?,
        val mediaUrl: String?,
        val likeCount: Int,
        val retweetCount: Int,
        val replyCount: Int,
        val sourceQualityScore: Double,
        val publishedAt: Instant
    ) {
        val engagementScore: Int
            get() = likeCount + (retweetCount * 2) + replyCount
        val rankingScore: Double
            get() = engagementScore * sourceQualityScore
    }

    private data class TopicCluster(
        val topicId: String,
        val title: String,
        val summary: String,
        val keyPoints: List<String>,
        val sentiment: String,
        val category: String,
        val tweetIds: List<String>
    )

    private data class TopicSnapshot(
        val topics: List<TrendingTopicEntity>,
        val tweets: List<TrendingTweetEntity>,
        val snapshotEpochMillis: Long
    )

    private data class GeminiTopicBatch(
        @SerializedName("topics")
        val topics: List<GeminiTopic>?
    )

    private data class GeminiTopic(
        @SerializedName("topic_id")
        val topicId: String?,
        @SerializedName("title")
        val title: String?,
        @SerializedName("summary")
        val summary: String?,
        @SerializedName("key_points")
        val keyPoints: List<String>?,
        @SerializedName("sentiment")
        val sentiment: String?,
        @SerializedName("category")
        val category: String?,
        @SerializedName("tweet_ids")
        val tweetIds: List<String>?
    ) {
        fun toTopicClusterOrNull(validTweetIds: Set<String>): TopicCluster? {
            val normalizedTweetIds = tweetIds.orEmpty()
                .map { it.trim() }
                .filter { it in validTweetIds }
                .distinct()

            if (normalizedTweetIds.size < MIN_TWEETS_PER_TOPIC) return null

            val normalizedTitle = title?.trim().orEmpty()
            val normalizedSummary = summary?.trim().orEmpty()
            if (normalizedTitle.isBlank() || normalizedSummary.isBlank()) return null

            return TopicCluster(
                topicId = topicId?.trim().orEmpty().ifBlank { "topic-${slugFallback(normalizedTitle)}" },
                title = normalizedTitle,
                summary = normalizedSummary,
                keyPoints = keyPoints.orEmpty().map { it.trim() }.filter { it.isNotBlank() },
                sentiment = sentiment?.trim().orEmpty(),
                category = category?.trim().orEmpty(),
                tweetIds = normalizedTweetIds
            )
        }

        private fun slugFallback(value: String): String {
            return value
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "cluster" }
        }
    }
}
