package com.example.handheldapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.handheldapp.data.api.AppVersionApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager untuk app status (maintenance, update required, etc)
 *
 * Status Flow:
 * - OK: Normal operation - semua fitur berjalan normal
 * - MAINTENANCE: Server maintenance - user bisa input offline, tapi TIDAK bisa sync
 * - UPDATE_REQUIRED: Maintenance selesai, HARUS update sebelum sync
 */
@Singleton
class AppStatusManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appVersionApi: AppVersionApiService,
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        private const val TAG = "AppStatusManager"
        private const val PREFS_NAME = "app_status_cache"
        private const val KEY_STATUS = "status"
        private const val KEY_MAINTENANCE_TYPE = "maintenance_type"
        private const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"
        private const val KEY_RELEASE_NOTES = "release_notes"
        private const val KEY_UPDATE_URL = "update_url"
        private const val KEY_ALLOW_SYNC = "allow_sync"
        private const val KEY_ALLOW_OFFLINE_WORK = "allow_offline_work"
        private const val KEY_FORCE_UPDATE = "force_update"
        private const val KEY_CACHED_AT = "cached_at"
        private const val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _appStatus = MutableStateFlow(loadCachedStatus())
    val appStatus: StateFlow<AppStatus> = _appStatus

    private val _statusDetails = MutableStateFlow(loadCachedStatusDetails())
    val statusDetails: StateFlow<AppStatusDetails?> = _statusDetails

    /**
     * Check app status dari server
     * Panggil saat app startup dan periodic
     *
     * Flow:
     * 1. Cek network → Jika offline, pakai cached status
     * 2. Call API dengan timeout 10s
     * 3. Jika sukses → Update cache + return status
     * 4. Jika error/timeout → Pakai cached status (app tetap jalan)
     */
    suspend fun checkAppStatus(currentVersionCode: Int): AppStatus {
        // Try to call API (fast timeout)
        return try {
            Log.d(TAG, "🔄 Checking app status, versionCode=$currentVersionCode")

            // API call with timeout (handled in Retrofit config)
            val response = appVersionApi.checkVersion(currentVersionCode, "android")

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data

                val status = when (data?.status) {
                    "maintenance" -> AppStatus.MAINTENANCE
                    "update_required" -> AppStatus.UPDATE_REQUIRED
                    "update_available" -> AppStatus.UPDATE_AVAILABLE
                    else -> AppStatus.OK
                }

                // Parse scheduled maintenance info
                val scheduledInfo = data?.scheduledMaintenance?.let { sm ->
                    ScheduledMaintenanceInfo(
                        isScheduled = sm.isScheduled ?: false,
                        maintenanceType = sm.maintenanceType,
                        startTime = sm.startTime,
                        endTime = sm.endTime,
                        startFormatted = sm.startFormatted,
                        endFormatted = sm.endFormatted,
                        duration = sm.duration,
                        message = sm.message,
                        timeUntilStart = sm.timeUntilStart
                    )
                }

                val details = AppStatusDetails(
                    status = status,
                    maintenanceType = data?.maintenanceType ?: "minor",
                    allowOfflineInput = data?.allowOfflineInput ?: true,
                    allowOfflineWork = data?.allowOfflineWork ?: true,
                    allowSync = data?.allowSync ?: true,
                    isMaintenance = data?.isMaintenance ?: false,
                    maintenanceMessage = data?.maintenanceMessage,
                    maintenanceEnd = data?.maintenanceEnd,
                    forceUpdate = data?.forceUpdate ?: false,
                    mustUpdate = data?.mustUpdate ?: false,
                    updateAvailable = data?.updateAvailable ?: false,
                    forceUpdateBeforeSync = data?.forceUpdateBeforeSync ?: false,
                    latestVersion = data?.latestVersion,
                    latestVersionCode = data?.latestVersionCode,
                    minVersionCode = data?.minVersionCode,
                    updateUrl = data?.updateUrl,
                    releaseNotes = data?.releaseNotes,
                    hasScheduledMaintenance = data?.hasScheduledMaintenance ?: false,
                    scheduledMaintenance = scheduledInfo
                )

                // Update in-memory state
                _appStatus.value = status
                _statusDetails.value = details

                // Cache to persistent storage
                cacheStatus(status, details)

                Log.d(TAG, "✅ App status from server: $status")
                Log.d(TAG, "   maintenanceType: ${data?.maintenanceType}")
                Log.d(TAG, "   allowOfflineWork: ${data?.allowOfflineWork}")
                Log.d(TAG, "   allowOfflineInput: ${data?.allowOfflineInput}")
                Log.d(TAG, "   allowSync: ${data?.allowSync}")
                Log.d(TAG, "   Cached for offline use")

                status
            } else {
                Log.w(TAG, "⚠️ Server error: ${response.code()}, using cached status")
                useCachedStatus()
            }
        } catch (e: Exception) {
            // Network error, timeout, atau server mati
            Log.e(TAG, "❌ API call failed: ${e.message}")
            Log.e(TAG, "   Using cached status to allow offline operation")
            useCachedStatus()
        }
    }

    /**
     * Use cached status when server unreachable
     */
    private fun useCachedStatus(): AppStatus {
        val cachedStatus = loadCachedStatus()
        val cachedDetails = loadCachedStatusDetails()

        _appStatus.value = cachedStatus
        _statusDetails.value = cachedDetails

        val cachedAt = prefs.getLong(KEY_CACHED_AT, 0)
        val cacheAge = System.currentTimeMillis() - cachedAt

        Log.d(TAG, "📦 Using cached status: $cachedStatus")
        Log.d(TAG, "   Cache age: ${cacheAge / 1000}s ago")

        return cachedStatus
    }

    /**
     * Cache status to SharedPreferences (persistent)
     */
    private fun cacheStatus(status: AppStatus, details: AppStatusDetails) {
        prefs.edit().apply {
            putString(KEY_STATUS, status.name)
            putString(KEY_MAINTENANCE_TYPE, details.maintenanceType)
            putString(KEY_MAINTENANCE_MESSAGE, details.maintenanceMessage)
            putString(KEY_RELEASE_NOTES, details.releaseNotes)
            putString(KEY_UPDATE_URL, details.updateUrl)
            putBoolean(KEY_ALLOW_SYNC, details.allowSync)
            putBoolean(KEY_ALLOW_OFFLINE_WORK, details.allowOfflineWork)
            putBoolean(KEY_FORCE_UPDATE, details.forceUpdate)
            putLong(KEY_CACHED_AT, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Load cached status from SharedPreferences
     */
    private fun loadCachedStatus(): AppStatus {
        val statusName = prefs.getString(KEY_STATUS, AppStatus.OK.name) ?: AppStatus.OK.name
        return try {
            AppStatus.valueOf(statusName)
        } catch (e: Exception) {
            AppStatus.OK
        }
    }

    /**
     * Load cached status details
     */
    private fun loadCachedStatusDetails(): AppStatusDetails? {
        val cachedAt = prefs.getLong(KEY_CACHED_AT, 0)
        if (cachedAt == 0L) return null

        val cachedStatus = loadCachedStatus()
        return AppStatusDetails(
            status = cachedStatus,
            maintenanceType = prefs.getString(KEY_MAINTENANCE_TYPE, "minor") ?: "minor",
            allowOfflineInput = true,
            allowOfflineWork = prefs.getBoolean(KEY_ALLOW_OFFLINE_WORK, true),
            allowSync = prefs.getBoolean(KEY_ALLOW_SYNC, true),
            isMaintenance = cachedStatus == AppStatus.MAINTENANCE,
            maintenanceMessage = prefs.getString(KEY_MAINTENANCE_MESSAGE, null),
            maintenanceEnd = null,
            forceUpdate = prefs.getBoolean(KEY_FORCE_UPDATE, false),
            mustUpdate = cachedStatus == AppStatus.UPDATE_REQUIRED,
            updateAvailable = cachedStatus == AppStatus.UPDATE_AVAILABLE,
            forceUpdateBeforeSync = cachedStatus == AppStatus.UPDATE_REQUIRED,
            latestVersion = null,
            latestVersionCode = null,
            minVersionCode = null,
            updateUrl = prefs.getString(KEY_UPDATE_URL, null),
            releaseNotes = prefs.getString(KEY_RELEASE_NOTES, null),
            hasScheduledMaintenance = false,
            scheduledMaintenance = null
        )
    }

    /**
     * Check if sync is allowed
     * false saat maintenance atau update_required
     */
    fun isSyncAllowed(): Boolean {
        return _statusDetails.value?.allowSync ?: true
    }

    /**
     * Check if offline work is allowed (untuk critical maintenance)
     */
    fun isOfflineWorkAllowed(): Boolean {
        return _statusDetails.value?.allowOfflineWork ?: true
    }

    /**
     * Check if there's scheduled maintenance (belum dimulai)
     */
    fun hasScheduledMaintenance(): Boolean {
        return _statusDetails.value?.hasScheduledMaintenance ?: false
    }

    /**
     * Get scheduled maintenance info
     */
    fun getScheduledMaintenanceInfo(): ScheduledMaintenanceInfo? {
        return _statusDetails.value?.scheduledMaintenance
    }

    /**
     * Get maintenance type (minor/major/critical)
     */
    fun getMaintenanceType(): String? {
        return _statusDetails.value?.maintenanceType
    }

    /**
     * Check if maintenance is minor type (zero downtime)
     */
    fun isMinorMaintenance(): Boolean {
        return _statusDetails.value?.maintenanceType == "minor" && _statusDetails.value?.isMaintenance == true
    }

    /**
     * Check if maintenance is major type (limited downtime)
     */
    fun isMajorMaintenance(): Boolean {
        return _statusDetails.value?.maintenanceType == "major" && _statusDetails.value?.isMaintenance == true
    }

    /**
     * Check if maintenance is critical type (force update)
     */
    fun isCriticalMaintenance(): Boolean {
        return _statusDetails.value?.maintenanceType == "critical" && _statusDetails.value?.isMaintenance == true
    }

    /**
     * Get user-friendly maintenance type description
     */
    fun getMaintenanceTypeDescription(): String {
        return when (_statusDetails.value?.maintenanceType) {
            "minor" -> "Maintenance ringan - Operasional normal"
            "major" -> "Upgrade server - Data sync setelah selesai"
            "critical" -> "Update penting - Mohon update aplikasi"
            else -> "Server maintenance"
        }
    }

    /**
     * Check if force update is required before sync
     */
    fun isUpdateRequiredBeforeSync(): Boolean {
        return _statusDetails.value?.forceUpdateBeforeSync ?: false
    }

    /**
     * Get maintenance message
     */
    fun getMaintenanceMessage(): String? {
        return _statusDetails.value?.maintenanceMessage
    }

    /**
     * Get release notes (for update dialog)
     */
    fun getReleaseNotes(): String? {
        return _statusDetails.value?.releaseNotes
    }

    /**
     * Get update URL (Play Store or APK download)
     */
    fun getUpdateUrl(): String? {
        return _statusDetails.value?.updateUrl
    }

    /**
     * Get current status
     */
    fun getCurrentStatus(): AppStatus {
        return _appStatus.value
    }

    /**
     * Reset status (for testing)
     */
    fun resetStatus() {
        _appStatus.value = AppStatus.OK
        _statusDetails.value = null
    }
}

/**
 * App status enum
 */
enum class AppStatus {
    OK,               // Normal operation
    MAINTENANCE,      // Server maintenance - offline input only
    UPDATE_REQUIRED,  // MUST update (no "Nanti" button)
    UPDATE_AVAILABLE  // Update available but optional (has "Nanti" button)
}

/**
 * Scheduled Maintenance Info - untuk pengumuman sebelum maintenance
 */
data class ScheduledMaintenanceInfo(
    val isScheduled: Boolean,
    val maintenanceType: String?,
    val startTime: String?,
    val endTime: String?,
    val startFormatted: String?,    // "Senin, 26 Februari 2026 10:00"
    val endFormatted: String?,      // "12:00"
    val duration: String?,          // "2 jam"
    val message: String?,
    val timeUntilStart: String?     // "dalam 5 jam"
)

/**
 * Detailed app status info dengan graceful degradation
 */
data class AppStatusDetails(
    val status: AppStatus,
    val maintenanceType: String,         // minor/major/critical
    val allowOfflineInput: Boolean,
    val allowOfflineWork: Boolean,       // False untuk critical maintenance
    val allowSync: Boolean,
    val isMaintenance: Boolean,
    val maintenanceMessage: String?,
    val maintenanceEnd: String?,
    val forceUpdate: Boolean,            // Toggle dari admin
    val mustUpdate: Boolean,             // WAJIB update (tidak ada tombol Nanti)
    val updateAvailable: Boolean,        // Ada update tapi opsional (ada tombol Nanti)
    val forceUpdateBeforeSync: Boolean,
    val latestVersion: String?,
    val latestVersionCode: Int?,
    val minVersionCode: Int?,
    val updateUrl: String?,
    val releaseNotes: String?,
    // Scheduled Maintenance (pengumuman sebelum maintenance)
    val hasScheduledMaintenance: Boolean = false,
    val scheduledMaintenance: ScheduledMaintenanceInfo? = null
)
