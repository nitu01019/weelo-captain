package com.weelo.logistics.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.weelo.logistics.data.database.converters.*
import com.weelo.logistics.data.database.dao.*
import com.weelo.logistics.data.database.entities.*
import timber.log.Timber

/**
 * WeeloDatabase - Main Room database for Captain App
 *
 * PRODUCTION-GRADE SETUP:
 * - Singleton pattern for single instance per app lifecycle
 * - Type converters for complex types
 * - Coroutine-safe database operations
 *
 * DATABASE VERSIONING:
 * - Version 1: Initial schema (Phase 1-2)
 * - Version 2: Added BufferedLocationEntity (Phase 3 - Offline Resilience)
 */
@Database(
    entities = [
        TripEntity::class,
        DriverProfileEntity::class,
        WarningEntity::class,
        VehicleEntity::class,
        ActiveTripEntity::class,
        // Phase 3: Offline Resilience
        BufferedLocationEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(
    DateConverter::class,
    TripStatusConverter::class,
    GeoLocationConverter::class,
    LocationListConverter::class
)
abstract class WeeloDatabase : RoomDatabase() {

    // DAOs - Data Access Objects for database operations
    abstract fun tripDao(): TripDao
    abstract fun driverProfileDao(): DriverProfileDao
    abstract fun warningDao(): WarningDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun activeTripDao(): ActiveTripDao
    // Phase 3: Offline Resilience
    abstract fun bufferedLocationDao(): BufferedLocationDao

    companion object {
        private const val DATABASE_NAME = "weelo_captain.db"
        private const val TAG = "WeeloDatabase"

        @Volatile
        private var INSTANCE: WeeloDatabase? = null

        /**
         * Get database instance (singleton pattern)
         *
         * Thread-safe lazy initialization with double-checked locking.
         */
        fun getInstance(context: Context): WeeloDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * Build the database instance
         *
         * IMPORTANT: Uses fallbackToDestructiveMigration during development.
         * For production, define explicit migrations with addMigrations().
         */
        private fun buildDatabase(context: Context): WeeloDatabase {
            Timber.d("Building WeeloDatabase: $DATABASE_NAME")

            return Room.databaseBuilder(
                context.applicationContext,
                WeeloDatabase::class.java,
                DATABASE_NAME
            )
                // TEMP: Destructive migration for development - rebuild database on schema change
                // TODO: Replace with explicit migrations for production
                .fallbackToDestructiveMigration()
                // Enable multi-instance invalidation (for testing)
                .allowMainThreadQueries()  // TEMP: Only for testing, remove in production
                // Add callback for database operations logging
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Timber.d("WeeloDatabase: Database created")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        Timber.d("WeeloDatabase: Database opened")
                    }
                })
                .build()
        }

        /**
         * Clear database instance (for testing or logout)
         *
         * Call this when user logs out to clear all local data.
         * Next getInstance() call will create a fresh database.
         */
        fun destroyInstance() {
            Timber.d("Destroying WeeloDatabase instance")
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
