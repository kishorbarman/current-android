package com.aifeed.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.InteractionType
import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.network.NetworkResult
import com.aifeed.feature.auth.data.AuthRepository
import com.aifeed.feature.feed.data.FeedRepository
import com.aifeed.feature.onboarding.data.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedTopicId: String? = null,
    val userTopics: List<TopicEntity> = emptyList()
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val topicRepository: TopicRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _selectedTopicId = MutableStateFlow<String?>(null)

    val articles: Flow<PagingData<ArticleEntity>> = authRepository.currentUser
        .flatMapLatest { user ->
            user?.let {
                _selectedTopicId.flatMapLatest { topicId ->
                    if (topicId != null) {
                        feedRepository.getArticlesByTopic(topicId)
                    } else {
                        feedRepository.getArticlesFeed(user.id)
                    }
                }
            } ?: flowOf(PagingData.empty())
        }
        .cachedIn(viewModelScope)

    init {
        loadUserTopics()
        refreshFeed()
    }

    private fun loadUserTopics() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            topicRepository.getUserTopics(userId).collect { topics ->
                _uiState.value = _uiState.value.copy(userTopics = topics)
            }
        }
    }

    fun refreshFeed() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

            when (val result = feedRepository.refreshFeed(userId)) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = result.message
                    )
                }
                is NetworkResult.Loading -> {
                    // Already loading
                }
            }
        }
    }

    fun selectTopic(topicId: String?) {
        _selectedTopicId.value = topicId
        _uiState.value = _uiState.value.copy(selectedTopicId = topicId)
    }

    fun onArticleClicked(article: ArticleEntity) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            feedRepository.recordInteraction(
                userId = userId,
                articleId = article.id,
                type = InteractionType.CLICK
            )
        }
    }

    fun onArticleRead(article: ArticleEntity, readDurationMs: Long, scrollPercentage: Float) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            feedRepository.markAsRead(article.id)

            val metadata = mapOf(
                "readDurationMs" to readDurationMs,
                "scrollPercentage" to scrollPercentage
            )

            feedRepository.recordInteraction(
                userId = userId,
                articleId = article.id,
                type = InteractionType.READ,
                metadata = metadata
            )
        }
    }

    fun toggleBookmark(article: ArticleEntity) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            when (val result = feedRepository.toggleBookmark(article.id)) {
                is NetworkResult.Success -> {
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
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is NetworkResult.Loading -> {}
            }
        }
    }

    fun likeArticle(article: ArticleEntity) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            feedRepository.recordInteraction(
                userId = userId,
                articleId = article.id,
                type = InteractionType.LIKE
            )
        }
    }

    fun dislikeArticle(article: ArticleEntity) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            feedRepository.recordInteraction(
                userId = userId,
                articleId = article.id,
                type = InteractionType.DISLIKE
            )
        }
    }

    fun shareArticle(article: ArticleEntity) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            feedRepository.recordInteraction(
                userId = userId,
                articleId = article.id,
                type = InteractionType.SHARE
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun syncInteractions() {
        viewModelScope.launch {
            feedRepository.syncInteractions()
        }
    }
}
