package com.aifeed.core.database.converter

import androidx.room.TypeConverter
import com.aifeed.core.database.entity.InteractionType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

class Converters {

    private val gson = Gson()

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

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}
