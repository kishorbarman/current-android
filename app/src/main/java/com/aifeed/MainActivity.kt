package com.aifeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.aifeed.core.util.DataStoreManager
import com.aifeed.navigation.AiFeedNavHost
import com.aifeed.navigation.Screen
import com.aifeed.ui.theme.AiFeedTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Determine start destination before showing UI
        val startDestination = runBlocking {
            determineStartDestination()
        }

        enableEdgeToEdge()

        setContent {
            val isDarkMode by dataStoreManager.isDarkMode.collectAsState(initial = false)

            AiFeedTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    AiFeedNavHost(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }

    private suspend fun determineStartDestination(): String {
        val isLoggedIn = dataStoreManager.isLoggedIn.first()
        val onboardingCompleted = dataStoreManager.onboardingCompleted.first()

        return when {
            !isLoggedIn -> Screen.Auth.route
            !onboardingCompleted -> Screen.Onboarding.route
            else -> Screen.Feed.route
        }
    }
}
