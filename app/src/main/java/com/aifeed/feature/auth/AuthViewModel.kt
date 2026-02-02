package com.aifeed.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aifeed.core.network.NetworkResult
import com.aifeed.feature.auth.data.AuthRepository
import com.aifeed.feature.auth.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    data object Initial : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val user: User, val onboardingCompleted: Boolean) : AuthState()
    data object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class AuthEvent {
    data object SignInClicked : AuthEvent()
    data object SignOutClicked : AuthEvent()
    data object ErrorDismissed : AuthEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val authState: StateFlow<AuthState> = combine(
        authRepository.currentUser,
        authRepository.onboardingCompleted,
        _isLoading,
        _error
    ) { user, onboardingCompleted, isLoading, error ->
        when {
            isLoading -> AuthState.Loading
            error != null -> AuthState.Error(error)
            user != null -> AuthState.Authenticated(user, onboardingCompleted)
            else -> AuthState.Unauthenticated
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AuthState.Initial
    )

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            when (val result = authRepository.signInWithGoogle(context)) {
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

    fun signOut() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            when (val result = authRepository.signOut()) {
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

    fun dismissError() {
        _error.value = null
    }
}
