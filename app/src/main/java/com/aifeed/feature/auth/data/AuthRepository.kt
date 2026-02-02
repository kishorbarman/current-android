package com.aifeed.feature.auth.data

import com.aifeed.core.network.NetworkResult
import kotlinx.coroutines.flow.Flow

data class User(
    val id: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val onboardingCompleted: Boolean = false
)

interface AuthRepository {
    val currentUser: Flow<User?>
    val isLoggedIn: Flow<Boolean>
    val onboardingCompleted: Flow<Boolean>

    suspend fun signInWithGoogle(activityContext: android.content.Context): NetworkResult<User>
    suspend fun signOut(): NetworkResult<Unit>
    suspend fun syncUserToSupabase(user: User): NetworkResult<Unit>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun getCurrentUserId(): String?
}
