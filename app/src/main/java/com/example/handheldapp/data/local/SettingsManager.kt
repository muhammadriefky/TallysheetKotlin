package com.example.handheldapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extension untuk create DataStore instance for settings
 * Nama harus unik di seluruh aplikasi
 */
private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * Settings Manager untuk mengelola preferensi aplikasi
 * - Tema (Light/Dark/System)
 * - Ukuran Font
 * - Cache Management
 * - Notification Preferences
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.appSettingsDataStore

    companion object {
        // Theme Settings
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode") // "light", "dark", "system"

        // Font Size Settings
        private val FONT_SIZE_KEY = stringPreferencesKey("font_size") // "small", "normal", "large"

        // Notification Settings
        private val NOTIFICATION_APPROVAL_KEY = booleanPreferencesKey("notification_approval")
        private val NOTIFICATION_DO_STATUS_KEY = booleanPreferencesKey("notification_do_status")
        private val NOTIFICATION_SYNC_KEY = booleanPreferencesKey("notification_sync")
        private val NOTIFICATION_CHECK_INTERVAL_KEY = intPreferencesKey("notification_check_interval") // in minutes

        // Cache Settings
        private val LAST_CACHE_CLEAR_KEY = longPreferencesKey("last_cache_clear")
        private val AUTO_CLEAR_CACHE_KEY = booleanPreferencesKey("auto_clear_cache")
        private val CACHE_MAX_AGE_DAYS_KEY = intPreferencesKey("cache_max_age_days")

        // Offline Data Settings
        private val OFFLINE_MODE_ENABLED_KEY = booleanPreferencesKey("offline_mode_enabled")
        private val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")

        // Last Approval Check
        private val LAST_APPROVAL_CHECK_KEY = longPreferencesKey("last_approval_check")
        private val LAST_NEW_DO_CHECK_KEY = longPreferencesKey("last_new_do_check")

        // Additional Settings
        private val SYNC_ON_WIFI_ONLY_KEY = booleanPreferencesKey("sync_wifi_only")
        private val AUTO_SYNC_ENABLED_KEY = booleanPreferencesKey("auto_sync_enabled")
        private val AUTO_SYNC_INTERVAL_KEY = intPreferencesKey("auto_sync_interval") // in minutes
        private val LOW_DATA_MODE_KEY = booleanPreferencesKey("low_data_mode")
        private val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        private val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")

        // Default values
        const val DEFAULT_THEME = "system"
        const val DEFAULT_FONT_SIZE = "normal"
        const val DEFAULT_CHECK_INTERVAL = 15 // 15 minutes
        const val DEFAULT_CACHE_MAX_AGE = 7 // 7 days
        const val DEFAULT_AUTO_SYNC_INTERVAL = 30 // 30 minutes
    }

    // ==================== THEME SETTINGS ====================

    /**
     * Set tema aplikasi
     * @param mode "light", "dark", atau "system"
     */
    suspend fun setThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    /**
     * Get tema aplikasi
     */
    fun getThemeMode(): Flow<String> = dataStore.data.map {
        it[THEME_MODE_KEY] ?: DEFAULT_THEME
    }

    // ==================== FONT SIZE SETTINGS ====================

    /**
     * Set ukuran font
     * @param size "small", "normal", atau "large"
     */
    suspend fun setFontSize(size: String) {
        dataStore.edit { preferences ->
            preferences[FONT_SIZE_KEY] = size
        }
    }

    /**
     * Get ukuran font
     */
    fun getFontSize(): Flow<String> = dataStore.data.map {
        it[FONT_SIZE_KEY] ?: DEFAULT_FONT_SIZE
    }

    /**
     * Get font scale multiplier
     */
    fun getFontScale(): Flow<Float> = dataStore.data.map { preferences ->
        when (preferences[FONT_SIZE_KEY] ?: DEFAULT_FONT_SIZE) {
            "small" -> 0.85f
            "large" -> 1.15f
            else -> 1.0f
        }
    }

    // ==================== NOTIFICATION SETTINGS ====================

    /**
     * Set notification approval enabled
     */
    suspend fun setApprovalNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_APPROVAL_KEY] = enabled
        }
    }

    /**
     * Get notification approval enabled
     */
    fun isApprovalNotificationEnabled(): Flow<Boolean> = dataStore.data.map {
        it[NOTIFICATION_APPROVAL_KEY] ?: true
    }

    /**
     * Set notification DO status enabled
     */
    suspend fun setDoStatusNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_DO_STATUS_KEY] = enabled
        }
    }

    /**
     * Get notification DO status enabled
     */
    fun isDoStatusNotificationEnabled(): Flow<Boolean> = dataStore.data.map {
        it[NOTIFICATION_DO_STATUS_KEY] ?: true
    }

    /**
     * Set notification sync enabled
     */
    suspend fun setSyncNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_SYNC_KEY] = enabled
        }
    }

    /**
     * Get notification sync enabled
     */
    fun isSyncNotificationEnabled(): Flow<Boolean> = dataStore.data.map {
        it[NOTIFICATION_SYNC_KEY] ?: false
    }

    /**
     * Set notification check interval (in minutes)
     */
    suspend fun setNotificationCheckInterval(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_CHECK_INTERVAL_KEY] = minutes
        }
    }

    /**
     * Get notification check interval
     */
    fun getNotificationCheckInterval(): Flow<Int> = dataStore.data.map {
        it[NOTIFICATION_CHECK_INTERVAL_KEY] ?: DEFAULT_CHECK_INTERVAL
    }

    // ==================== CACHE SETTINGS ====================

    /**
     * Set last cache clear time
     */
    suspend fun setLastCacheClear(timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { preferences ->
            preferences[LAST_CACHE_CLEAR_KEY] = timestamp
        }
    }

    /**
     * Get last cache clear time
     */
    fun getLastCacheClear(): Flow<Long> = dataStore.data.map {
        it[LAST_CACHE_CLEAR_KEY] ?: 0L
    }

    /**
     * Set auto clear cache enabled
     */
    suspend fun setAutoClearCacheEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_CLEAR_CACHE_KEY] = enabled
        }
    }

    /**
     * Get auto clear cache enabled
     */
    fun isAutoClearCacheEnabled(): Flow<Boolean> = dataStore.data.map {
        it[AUTO_CLEAR_CACHE_KEY] ?: false
    }

    /**
     * Set cache max age in days
     */
    suspend fun setCacheMaxAgeDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[CACHE_MAX_AGE_DAYS_KEY] = days
        }
    }

    /**
     * Get cache max age in days
     */
    fun getCacheMaxAgeDays(): Flow<Int> = dataStore.data.map {
        it[CACHE_MAX_AGE_DAYS_KEY] ?: DEFAULT_CACHE_MAX_AGE
    }

    // ==================== OFFLINE MODE SETTINGS ====================

    /**
     * Set offline mode enabled
     */
    suspend fun setOfflineModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[OFFLINE_MODE_ENABLED_KEY] = enabled
        }
    }

    /**
     * Get offline mode enabled
     */
    fun isOfflineModeEnabled(): Flow<Boolean> = dataStore.data.map {
        it[OFFLINE_MODE_ENABLED_KEY] ?: true
    }

    /**
     * Set last sync time
     */
    suspend fun setLastSyncTime(timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = timestamp
        }
    }

    /**
     * Get last sync time
     */
    fun getLastSyncTime(): Flow<Long> = dataStore.data.map {
        it[LAST_SYNC_TIME_KEY] ?: 0L
    }

    // ==================== APPROVAL CHECK ====================

    /**
     * Set last approval check time
     */
    suspend fun setLastApprovalCheck(timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { preferences ->
            preferences[LAST_APPROVAL_CHECK_KEY] = timestamp
        }
    }

    /**
     * Get last approval check time
     */
    fun getLastApprovalCheck(): Flow<Long> = dataStore.data.map {
        it[LAST_APPROVAL_CHECK_KEY] ?: 0L
    }

    /**
     * Set last new DO check time
     */
    suspend fun setLastNewDOCheck(timestamp: Long = System.currentTimeMillis()) {
        dataStore.edit { preferences ->
            preferences[LAST_NEW_DO_CHECK_KEY] = timestamp
        }
    }

    /**
     * Get last new DO check time
     */
    fun getLastNewDOCheck(): Flow<Long> = dataStore.data.map {
        it[LAST_NEW_DO_CHECK_KEY] ?: 0L
    }

    // ==================== AUTO SYNC SETTINGS ====================

    /**
     * Set sync on WiFi only
     */
    suspend fun setSyncOnWifiOnly(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SYNC_ON_WIFI_ONLY_KEY] = enabled
        }
    }

    /**
     * Get sync on WiFi only
     */
    fun isSyncOnWifiOnly(): Flow<Boolean> = dataStore.data.map {
        it[SYNC_ON_WIFI_ONLY_KEY] ?: false
    }

    /**
     * Set auto sync enabled
     */
    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_SYNC_ENABLED_KEY] = enabled
        }
    }

    /**
     * Get auto sync enabled
     */
    fun isAutoSyncEnabled(): Flow<Boolean> = dataStore.data.map {
        it[AUTO_SYNC_ENABLED_KEY] ?: true
    }

    /**
     * Set auto sync interval in minutes
     */
    suspend fun setAutoSyncInterval(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[AUTO_SYNC_INTERVAL_KEY] = minutes
        }
    }

    /**
     * Get auto sync interval in minutes
     */
    fun getAutoSyncInterval(): Flow<Int> = dataStore.data.map {
        it[AUTO_SYNC_INTERVAL_KEY] ?: DEFAULT_AUTO_SYNC_INTERVAL
    }

    // ==================== PERFORMANCE SETTINGS ====================

    /**
     * Set low data mode (reduces image quality, limits sync frequency)
     */
    suspend fun setLowDataModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LOW_DATA_MODE_KEY] = enabled
        }
    }

    /**
     * Get low data mode enabled
     */
    fun isLowDataModeEnabled(): Flow<Boolean> = dataStore.data.map {
        it[LOW_DATA_MODE_KEY] ?: false
    }

    // ==================== NOTIFICATION FEEDBACK SETTINGS ====================

    /**
     * Set vibration enabled for notifications
     */
    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[VIBRATION_ENABLED_KEY] = enabled
        }
    }

    /**
     * Get vibration enabled
     */
    fun isVibrationEnabled(): Flow<Boolean> = dataStore.data.map {
        it[VIBRATION_ENABLED_KEY] ?: true
    }

    /**
     * Set sound enabled for notifications
     */
    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SOUND_ENABLED_KEY] = enabled
        }
    }

    /**
     * Get sound enabled
     */
    fun isSoundEnabled(): Flow<Boolean> = dataStore.data.map {
        it[SOUND_ENABLED_KEY] ?: true
    }

    // ==================== UTILITY ====================

    /**
     * Clear all settings (reset to default)
     */
    suspend fun clearAllSettings() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Get all settings as map for debugging
     */
    suspend fun getAllSettings(): Map<String, Any?> {
        val prefs = dataStore.data.first()
        return mapOf(
            "theme_mode" to (prefs[THEME_MODE_KEY] ?: DEFAULT_THEME),
            "font_size" to (prefs[FONT_SIZE_KEY] ?: DEFAULT_FONT_SIZE),
            "notification_approval" to (prefs[NOTIFICATION_APPROVAL_KEY] ?: true),
            "notification_do_status" to (prefs[NOTIFICATION_DO_STATUS_KEY] ?: true),
            "notification_sync" to (prefs[NOTIFICATION_SYNC_KEY] ?: false),
            "notification_check_interval" to (prefs[NOTIFICATION_CHECK_INTERVAL_KEY] ?: DEFAULT_CHECK_INTERVAL),
            "auto_clear_cache" to (prefs[AUTO_CLEAR_CACHE_KEY] ?: false),
            "cache_max_age_days" to (prefs[CACHE_MAX_AGE_DAYS_KEY] ?: DEFAULT_CACHE_MAX_AGE),
            "offline_mode_enabled" to (prefs[OFFLINE_MODE_ENABLED_KEY] ?: true),
            "last_sync_time" to (prefs[LAST_SYNC_TIME_KEY] ?: 0L),
            "last_cache_clear" to (prefs[LAST_CACHE_CLEAR_KEY] ?: 0L)
        )
    }
}
