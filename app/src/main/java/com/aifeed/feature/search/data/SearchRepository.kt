package com.aifeed.feature.search.data

import androidx.paging.PagingData
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.network.NetworkResult
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    fun searchArticles(query: String): Flow<PagingData<ArticleEntity>>
    suspend fun searchArticlesOnce(query: String): NetworkResult<List<ArticleEntity>>
    fun getRecentSearches(): Flow<List<String>>
    suspend fun addRecentSearch(query: String)
    suspend fun clearRecentSearches()
}
