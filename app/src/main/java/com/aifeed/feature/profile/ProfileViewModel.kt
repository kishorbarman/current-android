package com.aifeed.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.util.DataStoreManager
import com.aifeed.feature.auth.data.AuthRepository
import com.aifeed.feature.auth.data.User
import com.aifeed.feature.profile.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val userTopics: List<TopicEntity> = emptyList(),
    val bookmarksCount: Int = 0,
    val readCount: Int = 0,
    val isDarkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed class ProfileTab {
    data object Bookmarks : ProfileTab()
    data object History : ProfileTab()
    data object Topics : ProfileTab()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _selectedTab = MutableStateFlow<ProfileTab>(ProfileTab.Bookmarks)
    val selectedTab: StateFlow<ProfileTab> = _selectedTab.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        authRepository.currentUser,
        dataStoreManager.isDarkMode,
        dataStoreManager.notificationsEnabled,
        _error
    ) { user, isDarkMode, notificationsEnabled, error ->
        ProfileUiState(
            user = user,
            isDarkMode = isDarkMode,
            notificationsEnabled = notificationsEnabled,
            isLoading = false,
            error = error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProfileUiState()
    )

    val bookmarkedArticles: Flow<PagingData<ArticleEntity>> =
        profileRepository.getBookmarkedArticles().cachedIn(viewModelScope)

    val readingHistory: Flow<PagingData<ArticleEntity>> =
        profileRepository.getReadingHistory().cachedIn(viewModelScope)

    val userTopics: StateFlow<List<TopicEntity>> = authRepository.currentUser
        .flatMapLatest { user ->
            user?.let { profileRepository.getUserTopics(it.id) } ?: flowOf(emptyList())
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    init {
        loadCounts()
    }

    private fun loadCounts() {
        viewModelScope.launch {
            // Load bookmark and read counts if needed
        }
    }

    fun selectTab(tab: ProfileTab) {
        _selectedTab.value = tab
    }

    fun removeBookmark(articleId: String) {
        viewModelScope.launch {
            profileRepository.removeBookmark(articleId)
        }
    }

    fun clearReadingHistory() {
        viewModelScope.launch {
            profileRepository.clearReadingHistory()
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setDarkMode(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setNotificationsEnabled(enabled)
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onComplete()
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
