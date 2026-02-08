package com.aifeed.core.di

import com.aifeed.feature.auth.data.AuthRepository
import com.aifeed.feature.auth.data.AuthRepositoryImpl
import com.aifeed.feature.feed.data.FeedRepository
import com.aifeed.feature.feed.data.FeedRepositoryImpl
import com.aifeed.feature.onboarding.data.TopicRepository
import com.aifeed.feature.onboarding.data.TopicRepositoryImpl
import com.aifeed.feature.profile.data.ProfileRepository
import com.aifeed.feature.profile.data.ProfileRepositoryImpl
import com.aifeed.feature.search.data.SearchRepository
import com.aifeed.feature.search.data.SearchRepositoryImpl
import com.aifeed.feature.trending.data.TrendingRepository
import com.aifeed.feature.trending.data.TrendingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindFeedRepository(
        feedRepositoryImpl: FeedRepositoryImpl
    ): FeedRepository

    @Binds
    @Singleton
    abstract fun bindTopicRepository(
        topicRepositoryImpl: TopicRepositoryImpl
    ): TopicRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        searchRepositoryImpl: SearchRepositoryImpl
    ): SearchRepository

    @Binds
    @Singleton
    abstract fun bindTrendingRepository(
        trendingRepositoryImpl: TrendingRepositoryImpl
    ): TrendingRepository
}
