package com.aifeed.core.di

import android.content.Context
import androidx.room.Room
import com.aifeed.core.database.AiFeedDatabase
import com.aifeed.core.database.dao.ArticleDao
import com.aifeed.core.database.dao.InteractionDao
import com.aifeed.core.database.dao.TopicDao
import com.aifeed.core.database.dao.UserSourceDao
import com.aifeed.core.database.dao.UserTopicDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AiFeedDatabase {
        return Room.databaseBuilder(
            context,
            AiFeedDatabase::class.java,
            AiFeedDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideArticleDao(database: AiFeedDatabase): ArticleDao {
        return database.articleDao()
    }

    @Provides
    @Singleton
    fun provideTopicDao(database: AiFeedDatabase): TopicDao {
        return database.topicDao()
    }

    @Provides
    @Singleton
    fun provideUserTopicDao(database: AiFeedDatabase): UserTopicDao {
        return database.userTopicDao()
    }

    @Provides
    @Singleton
    fun provideInteractionDao(database: AiFeedDatabase): InteractionDao {
        return database.interactionDao()
    }

    @Provides
    @Singleton
    fun provideUserSourceDao(database: AiFeedDatabase): UserSourceDao {
        return database.userSourceDao()
    }
}
