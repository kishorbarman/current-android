package com.aifeed.feature.feed.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.aifeed.core.database.dao.ArticleDao
import com.aifeed.core.database.dao.InteractionDao
import com.aifeed.core.database.dao.TopicDao
import com.aifeed.core.database.dao.UserTopicDao
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.InteractionEntity
import com.aifeed.core.database.entity.InteractionType
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
    private val topicDao: TopicDao,
    private val supabaseApi: SupabaseApiService,
    private val newsApiService: NewsApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FeedRepository {

    private val gson = Gson()

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
                // Get user's topics
                val userTopicIds = userTopicDao.getUserTopicIdsOnce(userId)

                // Always fetch from NewsAPI for fresh content
                if (userTopicIds.isNotEmpty()) {
                    fetchFromNewsApi(userTopicIds)
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
                        val articles = supabaseResult.body()?.map { dto ->
                            dto.toEntity(calculateRelevanceScore(userId, dto.primaryTopicId ?: ""))
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

    private suspend fun fetchFromNewsApi(topicIds: List<String>): NetworkResult<Unit> {
        return try {
            val articles = mutableListOf<ArticleEntity>()

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
                val response = newsApiService.getTopHeadlines(category = category)
                if (response.isSuccessful) {
                    response.body()?.articles?.filter {
                        !it.title.contains("[Removed]") && it.title.isNotBlank()
                    }?.forEach { newsArticle ->
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
                                relevanceScore = 1.0f,
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

    private suspend fun calculateRelevanceScore(userId: String, topicId: String): Float {
        val userTopic = userTopicDao.getUserTopicsOnce(userId).find { it.topicId == topicId }
        return userTopic?.weight ?: 0.5f
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

        val weightDelta = when (type) {
            InteractionType.CLICK -> 0.1f
            InteractionType.READ -> 0.2f
            InteractionType.BOOKMARK -> 0.5f
            InteractionType.UNBOOKMARK -> -0.3f
            InteractionType.SHARE -> 0.6f
            InteractionType.LIKE -> 0.4f
            InteractionType.DISLIKE -> -1.0f
        }

        val currentUserTopic = userTopicDao.getUserTopic(userId, topicId) ?: return
        val newWeight = (currentUserTopic.weight + weightDelta).coerceIn(0.1f, 3.0f)

        userTopicDao.updateWeight(userId, topicId, newWeight)
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
}
