package com.weelo.logistics.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.weelo.logistics.data.model.TruckSubtype
import com.weelo.logistics.utils.SecureLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Vehicle Draft Repository - Manages vehicle registration drafts
 * 
 * Features:
 * - Save incomplete vehicle registration as draft
 * - Load saved drafts
 * - Clear drafts after completion
 * - Auto-expire old drafts (24 hours)
 * 
 * Architecture:
 * - Uses DataStore for persistent storage
 * - JSON serialization for complex data
 * - Modular and testable
 * - Thread-safe operations
 * 
 * Security:
 * - User-specific drafts (tied to userId)
 * - Automatic cleanup of old data
 * - No sensitive data stored
 */
private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(name = "vehicle_drafts")

@Serializable
data class VehicleDraft(
    val userId: String,
    val vehicleType: String,
    val categoryId: String,
    val categoryName: String,
    val selectedSubtypes: Map<String, Int>, // subtypeId -> count
    val intermediateSelection: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(): Boolean {
        val expiryTime = 24 * 60 * 60 * 1000L // 24 hours
        return System.currentTimeMillis() - timestamp > expiryTime
    }
}

class VehicleDraftRepository(private val context: Context) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private object DraftKeys {
        val DRAFT_DATA = stringPreferencesKey("vehicle_draft_data")
        val HAS_DRAFT = booleanPreferencesKey("has_draft")
        val DRAFT_TIMESTAMP = longPreferencesKey("draft_timestamp")
    }
    
    /**
     * Save vehicle registration draft
     * - Overwrites existing draft
     * - Includes timestamp for expiry
     */
    suspend fun saveDraft(draft: VehicleDraft) {
        try {
            val draftJson = json.encodeToString(draft)
            context.draftDataStore.edit { preferences ->
                preferences[DraftKeys.DRAFT_DATA] = draftJson
                preferences[DraftKeys.HAS_DRAFT] = true
                preferences[DraftKeys.DRAFT_TIMESTAMP] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // Log error for backend monitoring
            SecureLogger.logError("VehicleDraftRepo", "saving draft", e)
        }
    }
    
    /**
     * Load saved draft
     * - Returns null if no draft exists
     * - Auto-deletes expired drafts
     */
    suspend fun loadDraft(): VehicleDraft? {
        return try {
            val preferences = context.draftDataStore.data.first()
            val draftJson = preferences[DraftKeys.DRAFT_DATA] ?: return null
            
            val draft = json.decodeFromString<VehicleDraft>(draftJson)
            
            // Check if draft is expired
            if (draft.isExpired()) {
                clearDraft()
                return null
            }
            
            draft
        } catch (e: Exception) {
            SecureLogger.logError("VehicleDraftRepo", "loading draft", e)
            null
        }
    }
    
    /**
     * Check if draft exists (reactive)
     */
    val hasDraft: Flow<Boolean> = context.draftDataStore.data.map { preferences ->
        val hasDraft = preferences[DraftKeys.HAS_DRAFT] ?: false
        val timestamp = preferences[DraftKeys.DRAFT_TIMESTAMP] ?: 0L
        
        // Check if not expired
        val isExpired = System.currentTimeMillis() - timestamp > 24 * 60 * 60 * 1000L
        
        hasDraft && !isExpired
    }
    
    /**
     * Clear draft (after completion or manual cancel)
     */
    suspend fun clearDraft() {
        try {
            context.draftDataStore.edit { preferences ->
                preferences.remove(DraftKeys.DRAFT_DATA)
                preferences.remove(DraftKeys.HAS_DRAFT)
                preferences.remove(DraftKeys.DRAFT_TIMESTAMP)
            }
        } catch (e: Exception) {
            SecureLogger.logError("VehicleDraftRepo", "clearing draft", e)
        }
    }
    
    /**
     * Get draft age in minutes (for UI display)
     */
    suspend fun getDraftAge(): Long? {
        return try {
            val preferences = context.draftDataStore.data.first()
            val timestamp = preferences[DraftKeys.DRAFT_TIMESTAMP] ?: return null
            val ageMillis = System.currentTimeMillis() - timestamp
            ageMillis / (60 * 1000) // Convert to minutes
        } catch (e: Exception) {
            null
        }
    }
}
