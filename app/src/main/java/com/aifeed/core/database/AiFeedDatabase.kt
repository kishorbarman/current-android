package com.aifeed.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aifeed.core.database.converter.Converters
import com.aifeed.core.database.dao.ArticleDao
import com.aifeed.core.database.dao.InteractionDao
import com.aifeed.core.database.dao.TopicDao
import com.aifeed.core.database.dao.UserTopicDao
import com.aifeed.core.database.entity.ArticleEntity
import com.aifeed.core.database.entity.InteractionEntity
import com.aifeed.core.database.entity.TopicEntity
import com.aifeed.core.database.entity.UserTopicEntity

@Database(
    entities = [
        ArticleEntity::class,
        TopicEntity::class,
        UserTopicEntity::class,
        InteractionEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AiFeedDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun topicDao(): TopicDao
    abstract fun userTopicDao(): UserTopicDao
    abstract fun interactionDao(): InteractionDao

    companion object {
        const val DATABASE_NAME = "aifeed_database"
    }
}
