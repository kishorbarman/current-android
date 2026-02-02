package com.aifeed.feature.auth.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.aifeed.BuildConfig
import com.aifeed.core.di.IoDispatcher
import com.aifeed.core.network.NetworkResult
import com.aifeed.core.network.api.SupabaseApiService
import com.aifeed.core.network.model.UserDto
import com.aifeed.core.util.DataStoreManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val supabaseApi: SupabaseApiService,
    private val dataStoreManager: DataStoreManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    override val currentUser: Flow<User?> = dataStoreManager.userId.map { userId ->
        if (userId != null) {
            User(
                id = userId,
                email = dataStoreManager.userEmail.first() ?: "",
                displayName = dataStoreManager.userName.first(),
                avatarUrl = dataStoreManager.userAvatarUrl.first(),
                onboardingCompleted = dataStoreManager.onboardingCompleted.first()
            )
        } else {
            null
        }
    }

    override val isLoggedIn: Flow<Boolean> = dataStoreManager.isLoggedIn

    override val onboardingCompleted: Flow<Boolean> = dataStoreManager.onboardingCompleted

    override suspend fun signInWithGoogle(activityContext: Context): NetworkResult<User> =
        withContext(ioDispatcher) {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                // Sign in to Firebase
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()

                val firebaseUser = authResult.user
                    ?: return@withContext NetworkResult.Error("Firebase authentication failed")

                val user = User(
                    id = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName,
                    avatarUrl = firebaseUser.photoUrl?.toString()
                )

                // Save user info locally
                dataStoreManager.saveUserInfo(
                    userId = user.id,
                    email = user.email,
                    name = user.displayName,
                    avatarUrl = user.avatarUrl
                )

                // Get Firebase ID token for API calls
                val firebaseIdToken = firebaseUser.getIdToken(false).await()
                firebaseIdToken.token?.let { token ->
                    dataStoreManager.saveAuthToken(token)
                }

                // Sync user to Supabase
                syncUserToSupabase(user)

                NetworkResult.Success(user)
            } catch (e: GetCredentialException) {
                NetworkResult.Error("Sign in failed: ${e.message}")
            } catch (e: Exception) {
                NetworkResult.Error("Sign in failed: ${e.message}")
            }
        }

    override suspend fun signOut(): NetworkResult<Unit> = withContext(ioDispatcher) {
        try {
            firebaseAuth.signOut()
            dataStoreManager.clearUserData()
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error("Sign out failed: ${e.message}")
        }
    }

    override suspend fun syncUserToSupabase(user: User): NetworkResult<Unit> =
        withContext(ioDispatcher) {
            try {
                // Check if user exists
                val existingUser = supabaseApi.getUser("eq.${user.id}")

                if (existingUser.isSuccessful && existingUser.body()?.isEmpty() == true) {
                    // Create new user
                    val userDto = UserDto(
                        id = user.id,
                        email = user.email,
                        displayName = user.displayName,
                        avatarUrl = user.avatarUrl,
                        onboardingCompleted = user.onboardingCompleted,
                        createdAt = null
                    )
                    supabaseApi.createUser(userDto)
                } else {
                    // Update existing user
                    val updates = mapOf(
                        "display_name" to (user.displayName ?: ""),
                        "avatar_url" to (user.avatarUrl ?: "")
                    )
                    supabaseApi.updateUser("eq.${user.id}", updates)
                }

                NetworkResult.Success(Unit)
            } catch (e: Exception) {
                // Don't fail auth if Supabase sync fails
                NetworkResult.Success(Unit)
            }
        }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStoreManager.setOnboardingCompleted(completed)

        // Also update in Supabase
        try {
            getCurrentUserId()?.let { userId ->
                supabaseApi.updateUser(
                    "eq.$userId",
                    mapOf("onboarding_completed" to completed)
                )
            }
        } catch (e: Exception) {
            // Ignore remote update failures
        }
    }

    override suspend fun getCurrentUserId(): String? {
        return dataStoreManager.userId.first()
    }
}
