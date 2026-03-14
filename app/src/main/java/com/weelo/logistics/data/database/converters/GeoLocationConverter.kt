package com.weelo.logistics.data.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weelo.logistics.data.model.Location

/**
 * GeoLocationConverter - Converts Location objects to/from JSON strings
 *
 * Complex objects like Location cannot be stored directly in Room.
 * We serialize to JSON and deserialize back when needed.
 */
class GeoLocationConverter {

    private val gson = Gson()

    /**
     * Convert JSON string to Location object
     */
    @TypeConverter
    fun fromJson(json: String?): Location? {
        return if (json.isNullOrBlank()) {
            null
        } else {
            try {
                gson.fromJson(json, Location::class.java)
            } catch (e: Exception) {
                // Return default location if parsing fails
                null
            }
        }
    }

    /**
     * Convert Location object to JSON string
     */
    @TypeConverter
    fun toJson(location: Location?): String? {
        return gson.toJson(location)
    }
}

/**
 * LocationListConverter - Converts list of locations to/from JSON strings
 *
 * Used for storing multiple location points (pickup, stops, drop).
 */
class LocationListConverter {

    private val gson = Gson()

    /**
     * Convert JSON string to list of Location objects
     */
    @TypeConverter
    fun fromJson(json: String?): List<Location>? {
        return if (json.isNullOrBlank()) {
            null
        } else {
            try {
                val type = object : TypeToken<List<Location>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Convert list of Location objects to JSON string
     */
    @TypeConverter
    fun toJson(locations: List<Location>?): String? {
        return gson.toJson(locations)
    }
}
