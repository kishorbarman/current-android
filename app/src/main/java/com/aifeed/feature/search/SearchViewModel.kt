package com.aifeed.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.InteractionType
import com.aifeed.feature.auth.data.AuthRepository
import com.aifeed.feature.feed.data.FeedRepository
import com.aifeed.feature.search.data.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val error: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val feedRepository: FeedRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val searchResults: Flow<PagingData<ArticleEntity>> = _searchQuery
        .debounce(300)
        .filter { it.length >= 2 }
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                searchRepository.searchArticles(query)
            }
        }
        .cachedIn(viewModelScope)

    val recentSearches: StateFlow<List<String>> = searchRepository.getRecentSearches()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(
            query = query,
            isSearching = query.length >= 2
        )
    }

    fun onSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            searchRepository.addRecentSearch(query)
        }

        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(
            query = query,
            isSearching = true
        )
    }

    fun onRecentSearchClick(query: String) {
        onSearch(query)
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            searchRepository.clearRecentSearches()
        }
    }

    fun clearQuery() {
        _searchQuery.value = ""
        _uiState.value = _uiState.value.copy(
            query = "",
            isSearching = false
        )
    }

    fun onArticleClick(article: ArticleEntity) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            feedRepository.recordInteraction(
                userId = userId,
                articleId = article.id,
                type = InteractionType.CLICK
            )
        }
    }

    fun toggleBookmark(article: ArticleEntity) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            when (val result = feedRepository.toggleBookmark(article.id)) {
                is com.aifeed.core.network.NetworkResult.Success -> {
                    val interactionType = if (result.data) {
                        InteractionType.BOOKMARK
                    } else {
                        InteractionType.UNBOOKMARK
                    }

                    feedRepository.recordInteraction(
                        userId = userId,
                        articleId = article.id,
                        type = interactionType
                    )
                }
                else -> {}
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
