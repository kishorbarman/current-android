package com.aifeed.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.network.NetworkResult
import com.aifeed.feature.auth.data.AuthRepository
import com.aifeed.feature.onboarding.data.TopicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val topics: List<TopicEntity> = emptyList(),
    val selectedTopicIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val canContinue: Boolean = false
) {
    companion object {
        const val MIN_TOPICS_REQUIRED = 3
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val topicRepository: TopicRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _selectedTopicIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isLoading = MutableStateFlow(true)
    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OnboardingUiState> = combine(
        topicRepository.getAllTopics(),
        _selectedTopicIds,
        _isLoading,
        _isSaving,
        _error
    ) { topics, selectedIds, isLoading, isSaving, error ->
        OnboardingUiState(
            topics = topics,
            selectedTopicIds = selectedIds,
            isLoading = isLoading,
            isSaving = isSaving,
            error = error,
            canContinue = selectedIds.size >= OnboardingUiState.MIN_TOPICS_REQUIRED
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        OnboardingUiState()
    )

    init {
        loadTopics()
    }

    private fun loadTopics() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            when (val result = topicRepository.fetchAndCacheTopics()) {
                is NetworkResult.Success -> {
                    _isLoading.value = false
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    _error.value = result.message
                }
                is NetworkResult.Loading -> {
                    // Already loading
                }
            }
        }
    }

    fun toggleTopic(topicId: String) {
        val currentSelection = _selectedTopicIds.value.toMutableSet()
        if (currentSelection.contains(topicId)) {
            currentSelection.remove(topicId)
        } else {
            currentSelection.add(topicId)
        }
        _selectedTopicIds.value = currentSelection
    }

    fun saveSelectedTopics(onComplete: () -> Unit) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            _isSaving.value = true
            _error.value = null

            when (val result = topicRepository.saveUserTopics(userId, _selectedTopicIds.value.toList())) {
                is NetworkResult.Success -> {
                    authRepository.setOnboardingCompleted(true)
                    _isSaving.value = false
                    onComplete()
                }
                is NetworkResult.Error -> {
                    _isSaving.value = false
                    _error.value = result.message
                }
                is NetworkResult.Loading -> {
                    // Already saving
                }
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
