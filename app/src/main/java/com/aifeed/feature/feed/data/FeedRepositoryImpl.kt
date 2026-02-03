package com.aifeed.feature.feed.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.aifeed.core.database.dao.ArticleDao
import com.aifeed.core.database.dao.InteractionDao
import com.aifeed.core.database.dao.TopicDao
import com.aifeed.core.database.dao.UserSourceDao
import com.aifeed.core.database.dao.UserTopicDao
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.InteractionEntity
import com.aifeed.core.database.entity.InteractionType
import com.aifeed.core.database.entity.UserSourceEntity
import com.aifeed.core.di.IoDispatcher
import com.aifeed.core.network.NetworkResult
import com.aifeed.core.network.api.NewsApiService
import com.aifeed.core.network.api.SupabaseApiService
import com.aifeed.core.network.model.InteractionBatchRequest
import com.aifeed.core.network.model.InteractionDto
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val interactionDao: InteractionDao,
    private val userTopicDao: UserTopicDao,
    private val userSourceDao: UserSourceDao,
    private val topicDao: TopicDao,
    private val supabaseApi: SupabaseApiService,
    private val newsApiService: NewsApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FeedRepository {

    private val gson = Gson()
    private var currentPage = 1

    override fun getArticlesFeed(userId: String): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            )
        ) {
            articleDao.getArticlesPaged()
        }.flow
    }

    override fun getArticlesByTopic(topicId: String): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            )
        ) {
            articleDao.getArticlesByTopicPaged(topicId)
        }.flow
    }

    override fun getArticleById(articleId: String): Flow<ArticleEntity?> {
        return articleDao.getArticleById(articleId)
    }

    override suspend fun refreshFeed(userId: String): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            try {
                // Reset page counter for fresh refresh
                currentPage = 1

                // Get user's topics
                val userTopicIds = userTopicDao.getUserTopicIdsOnce(userId)

                // Always fetch from NewsAPI for fresh content
                if (userTopicIds.isNotEmpty()) {
                    fetchFromNewsApi(userId, userTopicIds)
                }

                // Also try Supabase for any additional articles
                try {
                    val supabaseResult = if (userTopicIds.isNotEmpty()) {
                        val topicFilter = "in.(${userTopicIds.joinToString(",")})"
                        supabaseApi.getArticlesByTopics(topicFilter = topicFilter)
                    } else {
                        supabaseApi.getArticles()
                    }

                    if (supabaseResult.isSuccessful) {
                        // Pre-fetch user preferences for relevance calculation
                        val userTopicWeights = userTopicDao.getUserTopicsOnce(userId).associate { it.topicId to it.weight }
                        val userSourceWeights = userSourceDao.getUserSourcesOnce(userId).associate { it.sourceName to it.weight }

                        val articles = supabaseResult.body()?.map { dto ->
                            val topicWeight = userTopicWeights[dto.primaryTopicId] ?: 1.0f
                            val sourceWeight = userSourceWeights[dto.sourceName] ?: 1.0f
                            val relevance = (topicWeight * 0.6f + sourceWeight * 0.4f).coerceIn(0.1f, 3.0f)
                            dto.toEntity(relevance)
                        } ?: emptyList()

                        if (articles.isNotEmpty()) {
                            articleDao.insertArticles(articles)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore Supabase errors, we have NewsAPI
                }

                // If we have cached articles, that's fine
                val cachedCount = articleDao.getArticleCount()
                if (cachedCount > 0) {
                    NetworkResult.Success(Unit)
                } else {
                    NetworkResult.Error("Failed to load articles")
                }
            } catch (e: Exception) {
                NetworkResult.Error("Failed to refresh feed: ${e.message}")
            }
        }

    override suspend fun loadMoreArticles(userId: String): NetworkResult<Int> =
        withContext(ioDispatcher) {
            try {
                // Increment page for next batch
                currentPage++

                // Get user's topics
                val userTopicIds = userTopicDao.getUserTopicIdsOnce(userId)

                // Fetch next page from NewsAPI
                val result = if (userTopicIds.isNotEmpty()) {
                    fetchFromNewsApi(userId, userTopicIds)
                } else {
                    NetworkResult.Error("No topics selected")
                }

                when (result) {
                    is NetworkResult.Success -> {
                        val newCount = articleDao.getArticleCount()
                        NetworkResult.Success(newCount)
                    }
                    is NetworkResult.Error -> {
                        // Revert page increment on error
                        currentPage--
                        NetworkResult.Error(result.message)
                    }
                    is NetworkResult.Loading -> NetworkResult.Loading
                }
            } catch (e: Exception) {
                currentPage--
                NetworkResult.Error("Failed to load more articles: ${e.message}")
            }
        }

    private suspend fun fetchFromNewsApi(userId: String, topicIds: List<String>): NetworkResult<Unit> {
        return try {
            val articles = mutableListOf<ArticleEntity>()

            // Pre-fetch user preferences for relevance calculation
            val userTopicWeights = userTopicDao.getUserTopicsOnce(userId).associate { it.topicId to it.weight }
            val userSourceWeights = userSourceDao.getUserSourcesOnce(userId).associate { it.sourceName to it.weight }

            // Map topic slugs to NewsAPI categories
            // NewsAPI categories: business, entertainment, general, health, science, sports, technology
            val slugToCategory = mapOf(
                "technology" to "technology",
                "science" to "science",
                "business" to "business",
                "sports" to "sports",
                "entertainment" to "entertainment",
                "health" to "health",
                "politics" to "general",
                "world" to "general",
                "gaming" to "technology",
                "food" to "general",
                "travel" to "general",
                "finance" to "business"
            )

            // Get all topics from database and create ID -> topic map
            val allTopics = topicDao.getAllTopicsOnce()
            val topicMap = allTopics.associateBy { it.id }

            // Get categories for user's selected topics
            val categoriesToFetch = mutableListOf<Pair<String, String>>() // category to topicId

            for (topicId in topicIds) {
                val topic = topicMap[topicId]
                if (topic != null) {
                    val category = slugToCategory[topic.slug]
                    if (category != null && categoriesToFetch.none { it.first == category }) {
                        categoriesToFetch.add(category to topicId)
                    }
                }
            }

            // If no topics matched, fetch all available categories
            if (categoriesToFetch.isEmpty()) {
                val defaultTopicId = topicIds.firstOrNull() ?: "1"
                categoriesToFetch.add("technology" to defaultTopicId)
                categoriesToFetch.add("business" to defaultTopicId)
                categoriesToFetch.add("science" to defaultTopicId)
                categoriesToFetch.add("sports" to defaultTopicId)
                categoriesToFetch.add("entertainment" to defaultTopicId)
                categoriesToFetch.add("health" to defaultTopicId)
                categoriesToFetch.add("general" to defaultTopicId)
            }

            // Fetch articles for each category (limit to 6 API calls to stay within limits)
            for ((category, topicId) in categoriesToFetch.take(6)) {
                val response = newsApiService.getTopHeadlines(category = category, page = currentPage)
                if (response.isSuccessful) {
                    response.body()?.articles?.filter {
                        !it.title.contains("[Removed]") && it.title.isNotBlank()
                    }?.forEach { newsArticle ->
                        // Calculate relevance using pre-fetched weights
                        val topicWeight = userTopicWeights[topicId] ?: 1.0f
                        val sourceWeight = userSourceWeights[newsArticle.source.name] ?: 1.0f
                        val relevance = (topicWeight * 0.6f + sourceWeight * 0.4f).coerceIn(0.1f, 3.0f)
                        articles.add(
                            ArticleEntity(
                                id = UUID.randomUUID().toString(),
                                title = newsArticle.title,
                                preview = newsArticle.description ?: "",
                                sourceUrl = newsArticle.url,
                                imageUrl = newsArticle.urlToImage,
                                sourceName = newsArticle.source.name,
                                publishedAt = try {
                                    Instant.parse(newsArticle.publishedAt)
                                } catch (e: Exception) {
                                    Instant.now()
                                },
                                primaryTopicId = topicId,
                                relevanceScore = relevance,
                                cachedAt = Instant.now()
                            )
                        )
                    }
                }
            }

            if (articles.isNotEmpty()) {
                articleDao.insertArticles(articles)
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("No articles found from NewsAPI")
            }
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch from NewsAPI: ${e.message}")
        }
    }

    private suspend fun calculateRelevanceScore(userId: String, topicId: String, sourceName: String): Float {
        val topicWeight = userTopicDao.getUserTopicsOnce(userId).find { it.topicId == topicId }?.weight ?: 1.0f
        val sourceWeight = userSourceDao.getUserSource(userId, sourceName)?.weight ?: 1.0f

        // Combined score: topic (60%) + source (40%)
        return (topicWeight * 0.6f + sourceWeight * 0.4f).coerceIn(0.1f, 3.0f)
    }

    override suspend fun recordInteraction(
        userId: String,
        articleId: String,
        type: InteractionType,
        metadata: Map<String, Any>?
    ): NetworkResult<Unit> = withContext(ioDispatcher) {
        try {
            val interaction = InteractionEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                articleId = articleId,
                type = type,
                metadata = metadata?.let { gson.toJson(it) },
                timestamp = Instant.now(),
                synced = false
            )

            interactionDao.insertInteraction(interaction)

            // Update topic weights based on interaction
            // Note: Like/Dislike status is handled separately by toggleLike/toggleDislike
            updateTopicWeights(userId, articleId, type)

            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to record interaction: ${e.message}")
        }
    }

    private suspend fun updateTopicWeights(
        userId: String,
        articleId: String,
        type: InteractionType
    ) {
        val article = articleDao.getArticleByIdOnce(articleId) ?: return
        val topicId = article.primaryTopicId
        val sourceName = article.sourceName

        val weightDelta = when (type) {
            InteractionType.CLICK -> 0.1f
            InteractionType.READ -> 0.2f
            InteractionType.BOOKMARK -> 0.5f
            InteractionType.UNBOOKMARK -> -0.3f
            InteractionType.SHARE -> 0.6f
            InteractionType.LIKE -> 0.4f
            InteractionType.DISLIKE -> -1.0f
        }

        // Update topic weight
        val currentUserTopic = userTopicDao.getUserTopic(userId, topicId)
        if (currentUserTopic != null) {
            val newTopicWeight = (currentUserTopic.weight + weightDelta).coerceIn(0.1f, 3.0f)
            userTopicDao.updateWeight(userId, topicId, newTopicWeight)
        }

        // Update source weight
        val currentUserSource = userSourceDao.getUserSource(userId, sourceName)
        if (currentUserSource != null) {
            val newSourceWeight = (currentUserSource.weight + weightDelta).coerceIn(0.1f, 3.0f)
            userSourceDao.updateWeight(userId, sourceName, newSourceWeight)
        } else {
            // Create new source preference
            val initialWeight = (1.0f + weightDelta).coerceIn(0.1f, 3.0f)
            userSourceDao.insertUserSource(
                UserSourceEntity(
                    userId = userId,
                    sourceName = sourceName,
                    weight = initialWeight
                )
            )
        }

        // Note: Relevance scores are NOT updated immediately to avoid UI jitter.
        // The updated weights will be applied during the next feed refresh.
    }

    override suspend fun toggleBookmark(articleId: String): NetworkResult<Boolean> =
        withContext(ioDispatcher) {
            try {
                val article = articleDao.getArticleByIdOnce(articleId)
                    ?: return@withContext NetworkResult.Error("Article not found")

                val newBookmarkState = !article.isBookmarked
                articleDao.updateBookmarkStatus(articleId, newBookmarkState)

                NetworkResult.Success(newBookmarkState)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to toggle bookmark: ${e.message}")
            }
        }

    override suspend fun toggleLike(userId: String, articleId: String): NetworkResult<Boolean> =
        withContext(ioDispatcher) {
            try {
                val article = articleDao.getArticleByIdOnce(articleId)
                    ?: return@withContext NetworkResult.Error("Article not found")

                val newLikeState = !article.isLiked
                articleDao.updateLikeStatus(articleId, newLikeState)

                // Record interaction and update weights only when liking (not unliking)
                if (newLikeState) {
                    val interaction = InteractionEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        articleId = articleId,
                        type = InteractionType.LIKE,
                        metadata = null,
                        timestamp = Instant.now(),
                        synced = false
                    )
                    interactionDao.insertInteraction(interaction)
                    updateTopicWeights(userId, articleId, InteractionType.LIKE)
                }

                NetworkResult.Success(newLikeState)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to toggle like: ${e.message}")
            }
        }

    override suspend fun toggleDislike(userId: String, articleId: String): NetworkResult<Boolean> =
        withContext(ioDispatcher) {
            try {
                val article = articleDao.getArticleByIdOnce(articleId)
                    ?: return@withContext NetworkResult.Error("Article not found")

                val newDislikeState = !article.isDisliked
                articleDao.updateDislikeStatus(articleId, newDislikeState)

                // Record interaction and update weights only when disliking (not undisliking)
                if (newDislikeState) {
                    val interaction = InteractionEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        articleId = articleId,
                        type = InteractionType.DISLIKE,
                        metadata = null,
                        timestamp = Instant.now(),
                        synced = false
                    )
                    interactionDao.insertInteraction(interaction)
                    updateTopicWeights(userId, articleId, InteractionType.DISLIKE)
                }

                NetworkResult.Success(newDislikeState)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to toggle dislike: ${e.message}")
            }
        }

    override suspend fun markAsRead(articleId: String): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            try {
                articleDao.updateReadStatus(articleId, true)
                NetworkResult.Success(Unit)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to mark as read: ${e.message}")
            }
        }

    override suspend fun syncInteractions(): NetworkResult<Unit> = withContext(ioDispatcher) {
        try {
            val unsynced = interactionDao.getUnsyncedInteractions()
            if (unsynced.isEmpty()) {
                return@withContext NetworkResult.Success(Unit)
            }

            val dtos = unsynced.map { InteractionDto.fromEntity(it) }
            val response = supabaseApi.syncInteractions(InteractionBatchRequest(dtos))

            if (response.isSuccessful) {
                interactionDao.markAsSynced(unsynced.map { it.id })
            }

            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            // Don't fail on sync errors
            NetworkResult.Success(Unit)
        }
    }

    override suspend fun getSimilarArticles(articleId: String, limit: Int): List<ArticleEntity> =
        withContext(ioDispatcher) {
            try {
                val article = articleDao.getArticleByIdOnce(articleId)
                    ?: return@withContext emptyList()

                articleDao.getSimilarArticlesByTopicOrSource(
                    topicId = article.primaryTopicId,
                    sourceName = article.sourceName,
                    excludeArticleId = articleId,
                    limit = limit
                )
            } catch (e: Exception) {
                emptyList()
            }
        }
}
