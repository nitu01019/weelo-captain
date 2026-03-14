package com.weelo.logistics.data.database.repositories

import com.weelo.logistics.data.database.dao.WarningDao
import com.weelo.logistics.data.database.entities.WarningEntity
import com.weelo.logistics.data.database.dao.WarningSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * WarningRepository - Repository for driver warnings
 *
 * Provides persistent storage for driver warnings and earnings score calculation.
 * Enables offline viewing of warning history.
 */
class WarningRepository(
    private val warningDao: WarningDao
) {

    /**
     * Get warning by ID
     */
    suspend fun getWarning(warningId: String): WarningEntity? = withContext(Dispatchers.IO) {
        warningDao.getWarningById(warningId)
    }

    /**
     * Get all warnings for a driver
     */
    suspend fun getWarnings(driverId: String): List<WarningEntity> = withContext(Dispatchers.IO) {
        warningDao.getAllWarnings(driverId)
    }

    /**
     * Observe driver's warnings (for reactive UI)
     */
    fun observeWarnings(driverId: String): Flow<List<WarningEntity>> {
        return warningDao.observeWarnings(driverId)
    }

    /**
     * Get unacknowledged warnings
     */
    suspend fun getUnacknowledgedWarnings(driverId: String): List<WarningEntity> = withContext(Dispatchers.IO) {
        warningDao.getUnacknowledgedWarnings(driverId)
    }

    /**
     * Get warnings by severity
     */
    suspend fun getWarningsBySeverity(
        driverId: String,
        severity: String
    ): List<WarningEntity> = withContext(Dispatchers.IO) {
        warningDao.getWarningsBySeverity(driverId, severity)
    }

    /**
     * Get critical warnings (unacknowledged)
     */
    suspend fun getCriticalWarnings(driverId: String): List<WarningEntity> = withContext(Dispatchers.IO) {
        warningDao.getCriticalWarnings(driverId)
    }

    /**
     * Save warning (upsert pattern)
     */
    suspend fun saveWarning(warning: WarningEntity) = withContext(Dispatchers.IO) {
        warningDao.saveWarning(warning)
    }

    /**
     * Save multiple warnings atomically
     */
    suspend fun saveAllWarnings(warnings: List<WarningEntity>) = withContext(Dispatchers.IO) {
        warningDao.saveAllWarnings(warnings)
    }

    /**
     * Acknowledge warning
     */
    suspend fun acknowledgeWarning(warningId: String) = withContext(Dispatchers.IO) {
        warningDao.acknowledgeWarning(warningId, System.currentTimeMillis())
    }

    /**
     * Delete warning
     */
    suspend fun deleteWarning(warningId: String) = withContext(Dispatchers.IO) {
        warningDao.deleteWarning(warningId)
    }

    /**
     * Clear all warnings for a driver
     */
    suspend fun clearWarnings(driverId: String) = withContext(Dispatchers.IO) {
        warningDao.clearWarningsForDriver(driverId)
    }

    /**
     * Clear all warnings
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        warningDao.clearAllWarnings()
    }

    /**
     * Get warnings count
     */
    suspend fun getWarningsCount(driverId: String): Int = withContext(Dispatchers.IO) {
        warningDao.getWarningsCount(driverId)
    }

    /**
     * Get unacknowledged count
     */
    suspend fun getUnacknowledgedCount(driverId: String): Int = withContext(Dispatchers.IO) {
        warningDao.getUnacknowledgedCount(driverId)
    }

    /**
     * Get total points deducted
     */
    suspend fun getTotalPointsDeducted(driverId: String): Int = withContext(Dispatchers.IO) {
        warningDao.getTotalPointsDeducted(driverId)
    }

    /**
     * Calculate earnings score
     *
     * Score formula: 100 - weightedPoints
     * - Critical warnings: weight 3x
     * Warning: weight 2x
     * Info: weight 1x
     * Score ranges from 0 (worst) to 100 (best)
     */
    suspend fun calculateEarningsScore(driverId: String): EarningsScore = withContext(Dispatchers.IO) {
        val weightedPoints = warningDao.calculateWeightedScore(driverId)
        val score = maxOf(0, 100 - weightedPoints)

        val totalWarnings = getWarningsCount(driverId)
        val criticalCount = getWarningsBySeverity(driverId, "CRITICAL").size
        val warningCount = getWarningsBySeverity(driverId, "WARNING").size

        val warningLevel = when {
            totalWarnings == 0 -> WarningLevel.NONE
            criticalCount > 0 -> WarningLevel.CRITICAL
            warningCount > 3 -> WarningLevel.HIGH
            warningCount > 0 -> WarningLevel.MEDIUM
            else -> WarningLevel.NONE
        }

        EarningsScore(
            score = score,
            totalWarnings = totalWarnings,
            criticalWarnings = criticalCount,
            warningLevel = warningLevel
        )
    }

    /**
     * Get warning summary (for dashboard display)
     */
    suspend fun getWarningSummary(driverId: String): List<WarningSummary> = withContext(Dispatchers.IO) {
        warningDao.getWarningSummary(driverId)
    }
}

/**
 * Earnings score data class
 */
data class EarningsScore(
    val score: Int,           // 0-100
    val totalWarnings: Int,
    val criticalWarnings: Int,
    val warningLevel: WarningLevel
)

/**
 * Warning level enum for UI display
 */
enum class WarningLevel {
    NONE,       // No warnings
    MEDIUM,     // Few non-critical warnings
    HIGH,       // Multiple warnings
    CRITICAL    // Critical warnings present
}
