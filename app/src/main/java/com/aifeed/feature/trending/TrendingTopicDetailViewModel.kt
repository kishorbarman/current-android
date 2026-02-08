package com.aifeed.feature.trending

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aifeed.core.database.entity.TrendingTopicEntity
import com.aifeed.core.database.entity.TrendingTweetEntity
import com.aifeed.feature.trending.data.TrendingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrendingTopicDetailUiState(
    val topic: TrendingTopicEntity? = null,
    val tweets: List<TrendingTweetEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TrendingTopicDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trendingRepository: TrendingRepository
) : ViewModel() {

    private val topicId: String = checkNotNull(savedStateHandle["topicId"])

    private val _uiState = MutableStateFlow(TrendingTopicDetailUiState())
    val uiState: StateFlow<TrendingTopicDetailUiState> = _uiState.asStateFlow()

    init {
        loadTopicDetail()
    }

    fun loadTopicDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val topic = trendingRepository.getTopicById(topicId)
                if (topic == null) {
                    _uiState.value = TrendingTopicDetailUiState(
                        isLoading = false,
                        error = "Topic not found"
                    )
                    return@launch
                }

                val tweets = trendingRepository.getTweetsForTopic(topicId)
                _uiState.value = TrendingTopicDetailUiState(
                    topic = topic,
                    tweets = tweets,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = TrendingTopicDetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load topic"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
