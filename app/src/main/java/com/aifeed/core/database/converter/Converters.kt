package com.aifeed.core.database.converter

import androidx.room.TypeConverter
import com.aifeed.core.database.entity.InteractionType
import java.time.Instant

class Converters {

    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun fromInteractionType(value: InteractionType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toInteractionType(value: String?): InteractionType? {
        return value?.let { InteractionType.valueOf(it) }
    }
}
