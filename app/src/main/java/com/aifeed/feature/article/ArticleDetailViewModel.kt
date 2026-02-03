package com.aifeed.feature.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.InteractionType
import com.aifeed.feature.auth.data.AuthRepository
import com.aifeed.feature.feed.data.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ArticleDetailUiState(
    val article: ArticleEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isBookmarked: Boolean = false,
    val similarArticles: List<ArticleEntity> = emptyList()
)

@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val articleId: String = checkNotNull(savedStateHandle["articleId"])

    private val _error = MutableStateFlow<String?>(null)
    private val _readStartTime = MutableStateFlow<Instant?>(null)
    private val _similarArticles = MutableStateFlow<List<ArticleEntity>>(emptyList())

    val uiState: StateFlow<ArticleDetailUiState> = combine(
        feedRepository.getArticleById(articleId),
        _error,
        _similarArticles
    ) { article, error, similarArticles ->
        ArticleDetailUiState(
            article = article,
            isLoading = article == null && error == null,
            error = error,
            isBookmarked = article?.isBookmarked ?: false,
            similarArticles = similarArticles
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ArticleDetailUiState()
    )

    init {
        markArticleAsClicked()
        loadSimilarArticles()
    }

    private fun loadSimilarArticles() {
        viewModelScope.launch {
            _similarArticles.value = feedRepository.getSimilarArticles(articleId, limit = 10)
        }
    }

    private fun markArticleAsClicked() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            feedRepository.recordInteraction(
                userId = userId,
                articleId = articleId,
                type = InteractionType.CLICK
            )
        }
    }

    fun onReadStarted() {
        _readStartTime.value = Instant.now()
    }

    fun onReadEnded(scrollPercentage: Float) {
        viewModelScope.launch {
            val startTime = _readStartTime.value ?: return@launch
            val readDurationMs = java.time.Duration.between(startTime, Instant.now()).toMillis()

            val userId = authRepository.getCurrentUserId() ?: return@launch

            feedRepository.markAsRead(articleId)

            if (readDurationMs > 5000) {  // Only record if read for more than 5 seconds
                feedRepository.recordInteraction(
                    userId = userId,
                    articleId = articleId,
                    type = InteractionType.READ,
                    metadata = mapOf(
                        "readDurationMs" to readDurationMs,
                        "scrollPercentage" to scrollPercentage
                    )
                )
            }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            when (val result = feedRepository.toggleBookmark(articleId)) {
                is com.aifeed.core.network.NetworkResult.Success -> {
                    val interactionType = if (result.data) {
                        InteractionType.BOOKMARK
                    } else {
                        InteractionType.UNBOOKMARK
                    }

                    feedRepository.recordInteraction(
                        userId = userId,
                        articleId = articleId,
                        type = interactionType
                    )
                }
                is com.aifeed.core.network.NetworkResult.Error -> {
                    _error.value = result.message
                }
                else -> {}
            }
        }
    }

    fun likeArticle() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            feedRepository.recordInteraction(
                userId = userId,
                articleId = articleId,
                type = InteractionType.LIKE
            )
        }
    }

    fun dislikeArticle() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            feedRepository.recordInteraction(
                userId = userId,
                articleId = articleId,
                type = InteractionType.DISLIKE
            )
        }
    }

    fun shareArticle() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            feedRepository.recordInteraction(
                userId = userId,
                articleId = articleId,
                type = InteractionType.SHARE
            )
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
