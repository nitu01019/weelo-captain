package com.weelo.logistics.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.weelo.logistics.data.database.entities.WarningEntity
import kotlinx.coroutines.flow.Flow

/**
 * WarningDao - Data Access Object for driver warnings
 */
@Dao
interface WarningDao {

    /**
     * Get warning by ID
     */
    @Query("SELECT * FROM warnings WHERE warningId = :warningId")
    suspend fun getWarningById(warningId: String): WarningEntity?

    /**
     * Get all warnings for a driver
     */
    @Query("SELECT * FROM warnings WHERE driverId = :driverId ORDER BY issuedAt DESC")
    suspend fun getAllWarnings(driverId: String): List<WarningEntity>

    /**
     * Observe driver's warnings (for Compose)
     */
    @Query("SELECT * FROM warnings WHERE driverId = :driverId ORDER BY issuedAt DESC")
    fun observeWarnings(driverId: String): Flow<List<WarningEntity>>

    /**
     * Get unacknowledged warnings for a driver
     */
    @Query("SELECT * FROM warnings WHERE driverId = :driverId AND isAcknowledged = 0 ORDER BY issuedAt DESC")
    suspend fun getUnacknowledgedWarnings(driverId: String): List<WarningEntity>

    /**
     * Get warnings by severity
     */
    @Query("SELECT * FROM warnings WHERE driverId = :driverId AND severity = :severity ORDER BY issuedAt DESC")
    suspend fun getWarningsBySeverity(driverId: String, severity: String): List<WarningEntity>

    /**
     * Get critical warnings (for prioritized display)
     */
    @Query("SELECT * FROM warnings WHERE driverId = :driverId AND severity = 'CRITICAL' AND isAcknowledged = 0 ORDER BY issuedAt DESC")
    suspend fun getCriticalWarnings(driverId: String): List<WarningEntity>

    /**
     * Insert or replace warning
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWarning(warning: WarningEntity)

    /**
     * Insert or replace multiple warnings
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllWarnings(warnings: List<WarningEntity>)

    /**
     * Acknowledge warning
     */
    @Query("UPDATE warnings SET isAcknowledged = 1, acknowledgedAt = :timestamp WHERE warningId = :warningId")
    suspend fun acknowledgeWarning(warningId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Delete warning
     */
    @Query("DELETE FROM warnings WHERE warningId = :warningId")
    suspend fun deleteWarning(warningId: String)

    /**
     * Clear all warnings for a driver (for testing or data reset)
     */
    @Query("DELETE FROM warnings WHERE driverId = :driverId")
    suspend fun clearWarningsForDriver(driverId: String)

    /**
     * Clear all warnings (for logout)
     */
    @Query("DELETE FROM warnings")
    suspend fun clearAllWarnings()

    /**
     * Get warnings count for a driver
     */
    @Query("SELECT COUNT(*) FROM warnings WHERE driverId = :driverId")
    suspend fun getWarningsCount(driverId: String): Int

    /**
     * Get unacknowledged count
     */
    @Query("SELECT COUNT(*) FROM warnings WHERE driverId = :driverId AND isAcknowledged = 0")
    suspend fun getUnacknowledgedCount(driverId: String): Int

    /**
     * Get total points deducted for a driver
     */
    @Query("SELECT COALESCE(SUM(pointsDeducted), 0) FROM warnings WHERE driverId = :driverId")
    suspend fun getTotalPointsDeducted(driverId: String): Int

    /**
     * Calculate earnings score based on warnings
     * Higher score = better driver (fewer/less severe warnings)
     * Score ranges from 0 (worst) to 100 (best)
     */
    @Query("SELECT COALESCE(SUM(CASE severity WHEN 'CRITICAL' THEN pointsDeducted * 3 WHEN 'WARNING' THEN pointsDeducted * 2 ELSE pointsDeducted END), 0) FROM warnings WHERE driverId = :driverId")
    suspend fun calculateWeightedScore(driverId: String): Int

    /**
     * Get warning summary for dashboard display
     */
    @Query("""
        SELECT
            severity,
            COUNT(*) as count,
            SUM(pointsDeducted) as totalPoints
        FROM warnings
        WHERE driverId = :driverId
        GROUP BY severity
        ORDER BY
            CASE severity
                WHEN 'CRITICAL' THEN 1
                WHEN 'WARNING' THEN 2
                WHEN 'INFO' THEN 3
                ELSE 4
            END
    """)
    suspend fun getWarningSummary(driverId: String): List<WarningSummary>
}

/**
 * Warning summary grouped by severity
 */
data class WarningSummary(
    val severity: String,
    val count: Int,
    val totalPoints: Int
)
