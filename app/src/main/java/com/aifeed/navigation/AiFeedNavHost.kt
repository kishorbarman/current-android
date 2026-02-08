package com.aifeed.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aifeed.feature.article.ArticleDetailScreen
import com.aifeed.feature.auth.AuthScreen
import com.aifeed.feature.feed.FeedScreen
import com.aifeed.feature.onboarding.OnboardingScreen
import com.aifeed.feature.profile.ProfileScreen
import com.aifeed.feature.search.SearchScreen
import com.aifeed.feature.trending.TrendingTopicDetailScreen

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Onboarding : Screen("onboarding")
    data object Feed : Screen("feed")
    data object ArticleDetail : Screen("article/{articleId}") {
        fun createRoute(articleId: String) = "article/$articleId"
    }
    data object Search : Screen("search")
    data object Profile : Screen("profile")
    data object TrendingTopicDetail : Screen("trending/topic/{topicId}") {
        fun createRoute(topicId: String) = "trending/topic/$topicId"
    }
}

@Composable
fun AiFeedNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = { needsOnboarding ->
                    val destination = if (needsOnboarding) {
                        Screen.Onboarding.route
                    } else {
                        Screen.Feed.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Feed.route) {
            FeedScreen(
                onArticleClick = { article ->
                    navController.navigate(Screen.ArticleDetail.createRoute(article.id))
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                },
                onTrendingTopicClick = { topicId ->
                    navController.navigate(Screen.TrendingTopicDetail.createRoute(topicId))
                }
            )
        }

        composable(
            route = Screen.ArticleDetail.route,
            arguments = listOf(
                navArgument("articleId") { type = NavType.StringType }
            )
        ) {
            ArticleDetailScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onSimilarArticleClick = { article ->
                    navController.navigate(Screen.ArticleDetail.createRoute(article.id))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onArticleClick = { article ->
                    navController.navigate(Screen.ArticleDetail.createRoute(article.id))
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onArticleClick = { article ->
                    navController.navigate(Screen.ArticleDetail.createRoute(article.id))
                },
                onManageTopicsClick = {
                    navController.navigate(Screen.Onboarding.route)
                },
                onSignOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.TrendingTopicDetail.route,
            arguments = listOf(
                navArgument("topicId") { type = NavType.StringType }
            )
        ) {
            TrendingTopicDetailScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
