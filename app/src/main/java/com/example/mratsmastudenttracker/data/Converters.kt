package com.example.mratsmastudenttracker.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val listLongType = Types.newParameterizedType(List::class.java, Long::class.javaObjectType)
    private val adapter = moshi.adapter<List<Long>>(listLongType)

    @TypeConverter
    fun fromListLong(value: List<Long>?): String? {
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toListLong(value: String?): List<Long>? {
        return value?.let { adapter.fromJson(it) }
    }
}
