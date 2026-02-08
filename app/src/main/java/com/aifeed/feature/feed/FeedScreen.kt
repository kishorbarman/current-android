package com.aifeed.feature.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.aifeed.R
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.feature.trending.TrendingContent
import com.aifeed.feature.trending.TrendingViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onArticleClick: (ArticleEntity) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTrendingTopicClick: (String) -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val trendingViewModel: TrendingViewModel = hiltViewModel()
    val articles = viewModel.articles.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    // Track scroll for collapsing header with accumulated offset and threshold
    var accumulatedScroll by remember { mutableIntStateOf(0) }
    var isHeaderVisible by remember { mutableStateOf(true) }
    val scrollThreshold = 150 // pixels to scroll before toggling

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y.toInt()

                if (delta > 0) {
                    // Scrolling up - show header quickly
                    accumulatedScroll = (accumulatedScroll + delta).coerceAtMost(0)
                    if (accumulatedScroll > -scrollThreshold / 2) {
                        isHeaderVisible = true
                    }
                } else if (delta < 0) {
                    // Scrolling down - hide header after threshold
                    accumulatedScroll = (accumulatedScroll + delta).coerceAtLeast(-scrollThreshold * 2)
                    if (accumulatedScroll < -scrollThreshold) {
                        isHeaderVisible = false
                    }
                }

                return Offset.Zero
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(durationMillis = 200)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(durationMillis = 200)
                )
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.feed_title),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }
                            IconButton(onClick = onProfileClick) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    // Tab row for News and Trending
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text(stringResource(R.string.tab_news)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text(stringResource(R.string.tab_trending)) }
                        )
                    }
                    // Topic filter chips (only for News tab)
                    if (selectedTabIndex == 0 && uiState.userTopics.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = uiState.selectedTopicId == null,
                                    onClick = { viewModel.selectTopic(null) },
                                    label = { Text("All") }
                                )
                            }
                            items(uiState.userTopics) { topic ->
                                FilterChip(
                                    selected = uiState.selectedTopicId == topic.id,
                                    onClick = { viewModel.selectTopic(topic.id) },
                                    label = { Text(topic.name) }
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (selectedTabIndex) {
            0 -> {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing = uiState.isRefreshing),
                    onRefresh = { viewModel.refreshFeed() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .nestedScroll(nestedScrollConnection)
                ) {
                    ArticleList(
                        articles = articles,
                        onArticleClick = { article ->
                            viewModel.onArticleClicked(article)
                            onArticleClick(article)
                        },
                        onBookmarkClick = { viewModel.toggleBookmark(it) },
                        onLikeClick = { viewModel.likeArticle(it) },
                        onDislikeClick = { viewModel.dislikeArticle(it) },
                        onLoadMore = { viewModel.loadMoreArticles() },
                        isLoadingMore = uiState.isLoadingMore
                    )
                }
            }
            1 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    TrendingContent(
                        viewModel = trendingViewModel,
                        onTopicClick = onTrendingTopicClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleList(
    articles: LazyPagingItems<ArticleEntity>,
    onArticleClick: (ArticleEntity) -> Unit,
    onBookmarkClick: (ArticleEntity) -> Unit,
    onLikeClick: (ArticleEntity) -> Unit,
    onDislikeClick: (ArticleEntity) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean
) {
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
    LazyColumn(
        contentPadding = PaddingValues(
            bottom = navigationBarPadding.calculateBottomPadding()
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = articles.itemCount,
            key = { index -> articles[index]?.id ?: index }
        ) { index ->
            articles[index]?.let { article ->
                ArticleItem(
                    article = article,
                    onClick = { onArticleClick(article) },
                    onBookmarkClick = { onBookmarkClick(article) },
                    onLikeClick = { onLikeClick(article) },
                    onDislikeClick = { onDislikeClick(article) }
                )
                if (index < articles.itemCount - 1) {
                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 3.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Loading state
        when (val appendState = articles.loadState.append) {
            is LoadState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is LoadState.Error -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.error_loading),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            is LoadState.NotLoading -> {
                // Show "Load More" button when we've reached the end of cached articles
                if (appendState.endOfPaginationReached && articles.itemCount > 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                OutlinedButton(onClick = onLoadMore) {
                                    Text("Load More")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Initial loading state
        if (articles.loadState.refresh is LoadState.Loading && articles.itemCount == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Empty state
        if (articles.loadState.refresh is LoadState.NotLoading && articles.itemCount == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_articles),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleItem(
    article: ArticleEntity,
    onClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Full-bleed image
        article.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Preview
            Text(
                text = article.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Footer: Source, time, bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = article.sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " \u2022 ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRelativeTime(article.publishedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onLikeClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (article.isLiked) {
                                Icons.Filled.ThumbUp
                            } else {
                                Icons.Outlined.ThumbUp
                            },
                            contentDescription = "Like",
                            modifier = Modifier.size(18.dp),
                            tint = if (article.isLiked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = onDislikeClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (article.isDisliked) {
                                Icons.Filled.ThumbDown
                            } else {
                                Icons.Outlined.ThumbDown
                            },
                            contentDescription = "Dislike",
                            modifier = Modifier.size(18.dp),
                            tint = if (article.isDisliked) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = onBookmarkClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (article.isBookmarked) {
                                Icons.Default.Bookmark
                            } else {
                                Icons.Default.BookmarkBorder
                            },
                            contentDescription = "Bookmark",
                            modifier = Modifier.size(18.dp),
                            tint = if (article.isBookmarked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(instant: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(instant, now)

    return when {
        duration.toMinutes() < 1 -> "Just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "${duration.toHours()}h ago"
        duration.toDays() < 7 -> "${duration.toDays()}d ago"
        else -> "${duration.toDays() / 7}w ago"
    }
}
