package com.aifeed.core.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aifeed_preferences")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val USER_ID = stringPreferencesKey("user_id")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_AVATAR_URL = stringPreferencesKey("user_avatar_url")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val userId: Flow<String?> = dataStore.data.map { it[USER_ID] }
    val userEmail: Flow<String?> = dataStore.data.map { it[USER_EMAIL] }
    val userName: Flow<String?> = dataStore.data.map { it[USER_NAME] }
    val userAvatarUrl: Flow<String?> = dataStore.data.map { it[USER_AVATAR_URL] }
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }
    val authToken: Flow<String?> = dataStore.data.map { it[AUTH_TOKEN] }
    val isDarkMode: Flow<Boolean> = dataStore.data.map { it[DARK_MODE] ?: false }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { it[USER_ID] != null }

    suspend fun saveUserInfo(
        userId: String,
        email: String,
        name: String?,
        avatarUrl: String?
    ) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = userId
            preferences[USER_EMAIL] = email
            name?.let { preferences[USER_NAME] = it }
            avatarUrl?.let { preferences[USER_AVATAR_URL] = it }
        }
    }

    suspend fun saveAuthToken(token: String) {
        dataStore.edit { preferences ->
            preferences[AUTH_TOKEN] = token
        }
    }

    suspend fun saveRefreshToken(token: String) {
        dataStore.edit { preferences ->
            preferences[REFRESH_TOKEN] = token
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun clearUserData() {
        dataStore.edit { preferences ->
            preferences.remove(USER_ID)
            preferences.remove(USER_EMAIL)
            preferences.remove(USER_NAME)
            preferences.remove(USER_AVATAR_URL)
            preferences.remove(AUTH_TOKEN)
            preferences.remove(REFRESH_TOKEN)
            preferences.remove(ONBOARDING_COMPLETED)
        }
    }
}
