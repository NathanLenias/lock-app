package com.nathanb.lock.data.database

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return JSONArray(value).toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val array = JSONArray(value)
        return (0 until array.length()).map { array.getString(it) }
    }
}
