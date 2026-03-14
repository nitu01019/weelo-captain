package com.weelo.logistics.data.database.converters

import androidx.room.TypeConverter

/**
 * DateConverter - Converts between Long (milliseconds) and Date objects
 *
 * Room doesn't support Date objects directly, so we use Long timestamps instead.
 * This converter handles the conversion transparently.
 */
class DateConverter {

    /**
     * Convert Long timestamp to Date object
     * Returns null if input is null
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): java.util.Date? {
        return value?.let { java.util.Date(it) }
    }

    /**
     * Convert Date object to Long timestamp
     * Returns null if input is null
     */
    @TypeConverter
    fun toTimestamp(date: java.util.Date?): Long? = date?.time
}
