package com.weelo.logistics.data.database.converters

import androidx.room.TypeConverter

/**
 * AssignmentStatusConverter - Converts assignment status enum to/from String
 *
 * Used in broadcast and assignment tracking.
 */
class AssignmentStatusConverter {

    /**
     * Convert String to status enum
     */
    @TypeConverter
    fun fromString(value: String?): String? = value?.uppercase()

    /**
     * Status enum to String
     */
    @TypeConverter
    fun toString(status: String?): String? = status?.uppercase()
}
