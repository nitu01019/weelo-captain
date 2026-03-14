package com.weelo.logistics.data.database.converters

import androidx.room.TypeConverter
import com.weelo.logistics.data.model.TripStatus

/**
 * TripStatusConverter - Converts between TripStatus enum and String
 *
 * Room stores enums as Strings by default, but we use this converter
 * for explicit control and to ensure compatibility with offline storage.
 */
class TripStatusConverter {

    /**
     * Convert String to TripStatus enum
     */
    @TypeConverter
    fun fromString(value: String?): TripStatus? = value?.let {
        try {
            TripStatus.valueOf(it.uppercase())
        } catch (e: IllegalArgumentException) {
            // If we encounter an unknown status, default to PENDING
            TripStatus.PENDING
        }
    }

    /**
     * Convert TripStatus enum to String
     */
    @TypeConverter
    fun toString(status: TripStatus?): String? = status?.name
}
