package com.aifeed.feature.search.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.aifeed.core.database.dao.ArticleDao
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.di.IoDispatcher
import com.aifeed.core.network.NetworkResult
import com.aifeed.core.network.api.SupabaseApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "search_preferences"
)

@Singleton
class SearchRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val supabaseApi: SupabaseApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SearchRepository {

    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val maxRecentSearches = 10

    override fun searchArticles(query: String): Flow<PagingData<ArticleEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            )
        ) {
            articleDao.searchArticlesPaged(query)
        }.flow
    }

    override suspend fun searchArticlesOnce(query: String): NetworkResult<List<ArticleEntity>> =
        withContext(ioDispatcher) {
            try {
                // First try remote search
                val remoteQuery = "(title.ilike.*$query*,preview.ilike.*$query*)"
                val response = supabaseApi.searchArticles(query = remoteQuery)

                if (response.isSuccessful) {
                    val articles = response.body()?.map { it.toEntity() } ?: emptyList()
                    if (articles.isNotEmpty()) {
                        // Cache the results
                        articleDao.insertArticles(articles)
                        return@withContext NetworkResult.Success(articles)
                    }
                }

                // Fall back to local search
                val localResults = articleDao.searchArticles(query).first()
                NetworkResult.Success(localResults)
            } catch (_: Exception) {
                // Try local search on error
                try {
                    val localResults = articleDao.searchArticles(query).first()
                    NetworkResult.Success(localResults)
                } catch (e2: Exception) {
                    NetworkResult.Error("Search failed: ${e2.message}")
                }
            }
        }

    override fun getRecentSearches(): Flow<List<String>> {
        return context.searchDataStore.data.map { preferences ->
            preferences[recentSearchesKey]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
        }
    }

    override suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return

        context.searchDataStore.edit { preferences ->
            val currentSearches = preferences[recentSearchesKey]
                ?.split("|")
                ?.filter { it.isNotEmpty() }
                ?.toMutableList()
                ?: mutableListOf()

            // Remove if already exists to move to front
            currentSearches.remove(query)

            // Add to front
            currentSearches.add(0, query)

            // Limit to max size
            val limitedSearches = currentSearches.take(maxRecentSearches)

            preferences[recentSearchesKey] = limitedSearches.joinToString("|")
        }
    }

    override suspend fun clearRecentSearches() {
        context.searchDataStore.edit { preferences ->
            preferences.remove(recentSearchesKey)
        }
    }
}
