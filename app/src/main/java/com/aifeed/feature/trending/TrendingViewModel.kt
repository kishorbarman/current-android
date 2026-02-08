package com.aifeed.feature.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aifeed.core.database.entity.TrendingTopicEntity
import com.aifeed.core.network.NetworkResult
import com.aifeed.feature.trending.data.TrendingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrendingUiState(
    val topics: List<TrendingTopicEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val isInitialLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val trendingRepository: TrendingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendingUiState())
    val uiState: StateFlow<TrendingUiState> = _uiState.asStateFlow()

    init {
        observeTrendingTopics()
        refreshIfStale()
    }

    private fun refreshIfStale() {
        viewModelScope.launch {
            if (trendingRepository.isCacheStale()) {
                refreshTrending()
            }
        }
    }

    private fun observeTrendingTopics() {
        viewModelScope.launch {
            trendingRepository.getTrendingTopics().collect { topics ->
                _uiState.value = _uiState.value.copy(
                    topics = topics,
                    isInitialLoading = false
                )
            }
        }
    }

    fun refreshTrending() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

            when (val result = trendingRepository.refreshTrending()) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        isInitialLoading = false,
                        error = result.message
                    )
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
