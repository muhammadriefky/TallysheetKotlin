package com.example.handheldapp.repository

import android.util.Log
import com.example.handheldapp.data.api.DeliveryOrderApiService
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.local.SettingsManager
import com.example.handheldapp.data.local.dao.CachedDeliveryOrderDao
import com.example.handheldapp.data.local.entity.CachedDeliveryOrder
import com.example.handheldapp.utils.NetworkStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository untuk mengelola data offline dan sinkronisasi
 * Mengimplementasikan offline-first architecture
 */
@Singleton
class OfflineSyncRepository @Inject constructor(
    private val deliveryOrderApiService: DeliveryOrderApiService,
    private val cachedDeliveryOrderDao: CachedDeliveryOrderDao,
    private val sessionManager: SessionManager,
    private val settingsManager: SettingsManager,
    private val networkStateManager: NetworkStateManager
) {
    companion object {
        private const val TAG = "OfflineSyncRepository"
    }

    // ==================== DELIVERY ORDER SYNC ====================

    /**
     * Sync delivery orders dari server ke local database
     * Returns: Jumlah DO baru yang di-sync
     */
    suspend fun syncDeliveryOrders(forceSync: Boolean = false): SyncResult {
        val branchCode = sessionManager.getBranchCode().first() ?: return SyncResult.Error("Branch code not found")
        val companyCode = sessionManager.getCompanyCode().first() ?: return SyncResult.Error("Company code not found")

        // Check if offline mode is enabled and we're offline
        val isOfflineMode = settingsManager.isOfflineModeEnabled().first()
        val isConnected = networkStateManager.isConnected.value

        if (!isConnected) {
            if (isOfflineMode) {
                Log.d(TAG, "Offline mode: Using cached data")
                return SyncResult.OfflineMode
            } else {
                return SyncResult.Error("No internet connection")
            }
        }

        return try {
            Log.d(TAG, "Syncing delivery orders for branch: $branchCode")

            val response = deliveryOrderApiService.getDeliveryOrders(
                branchCode = branchCode,
                companyCode = companyCode,
                status = "all" // Get all DOs for caching
            )

            if (response.success && response.data != null) {
                val deliveryOrders = response.data

                // Get existing DOs to determine which are new
                val existingIds = cachedDeliveryOrderDao.getAllByBranch(branchCode, companyCode)
                    .first()
                    .map { it.id }
                    .toSet()

                // Convert to cached entities
                val cachedOrders = deliveryOrders.map { do_ ->
                    val doId = do_.dohId.toLongOrNull() ?: 0L
                    val isNew = doId !in existingIds
                    CachedDeliveryOrder(
                        id = doId,
                        doCode = do_.dohNodo ?: "",
                        doDate = null, // Not available in current model
                        doNumberSupplier = do_.dohNoSj,
                        supplierCode = null,
                        supplierName = do_.dohSupplier,
                        progressStatus = do_.dohStatus,
                        approvalStatus = do_.staffStatus,
                        branchCode = branchCode,
                        companyCode = companyCode,
                        totalQtyKarton = do_.qtyKartonTarget,
                        totalQtyPcs = do_.qtyPcsTarget,
                        scannedQtyKarton = do_.qtyKartonScanned,
                        scannedQtyPcs = do_.qtyPcsScanned,
                        itemCount = do_.totalItems,
                        createdAt = null,
                        arrivedAt = null,
                        lastSyncAt = System.currentTimeMillis(),
                        isNew = isNew
                    )
                }

                // Insert/update all
                cachedDeliveryOrderDao.insertAll(cachedOrders)

                // Count new DOs
                val newCount = cachedOrders.count { it.isNew }

                // Update last sync time
                settingsManager.setLastSyncTime()

                Log.d(TAG, "Synced ${cachedOrders.size} DOs, $newCount new")
                SyncResult.Success(cachedOrders.size, newCount)
            } else {
                val errorMsg = response.message ?: "Unknown error"
                Log.e(TAG, "Sync failed: $errorMsg")
                SyncResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
            SyncResult.Error(e.message ?: "Sync failed")
        }
    }

    /**
     * Get delivery orders (offline-first)
     * Jika offline mode enabled dan tidak ada koneksi, gunakan data local
     */
    suspend fun getDeliveryOrdersOfflineFirst(): Flow<List<CachedDeliveryOrder>> {
        val branchCode = sessionManager.getBranchCode().first() ?: ""
        val companyCode = sessionManager.getCompanyCode().first() ?: ""

        val isOfflineMode = settingsManager.isOfflineModeEnabled().first()
        val isConnected = networkStateManager.isConnected.value

        // Try to sync if online
        if (isConnected && !isOfflineMode) {
            syncDeliveryOrders()
        }

        return cachedDeliveryOrderDao.getAllByBranch(branchCode, companyCode)
    }

    /**
     * Get scannable DOs (offline-first)
     */
    suspend fun getScannableDOsOfflineFirst(): Flow<List<CachedDeliveryOrder>> {
        val branchCode = sessionManager.getBranchCode().first() ?: ""
        val companyCode = sessionManager.getCompanyCode().first() ?: ""

        return cachedDeliveryOrderDao.getScannableDOs(branchCode, companyCode)
    }

    /**
     * Get new DO count (for notification badge)
     */
    suspend fun getNewDOCount(): Flow<Int> {
        val branchCode = sessionManager.getBranchCode().first() ?: ""
        val companyCode = sessionManager.getCompanyCode().first() ?: ""
        return cachedDeliveryOrderDao.getNewDOCount(branchCode, companyCode)
    }

    /**
     * Mark DO as seen
     */
    suspend fun markDOAsSeen(doId: Long) {
        cachedDeliveryOrderDao.markAsSeen(doId)
    }

    /**
     * Mark all DOs as seen
     */
    suspend fun markAllDOsAsSeen() {
        val branchCode = sessionManager.getBranchCode().first() ?: return
        cachedDeliveryOrderDao.markAllAsSeen(branchCode)
    }

    /**
     * Search DOs locally
     */
    suspend fun searchDOsLocally(keyword: String): List<CachedDeliveryOrder> {
        val branchCode = sessionManager.getBranchCode().first() ?: ""
        val companyCode = sessionManager.getCompanyCode().first() ?: ""
        return cachedDeliveryOrderDao.search(branchCode, companyCode, keyword)
    }

    /**
     * Clear old cached data
     */
    suspend fun clearOldCache(maxAgeDays: Int) {
        val threshold = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        cachedDeliveryOrderDao.deleteOlderThan(threshold)
        Log.d(TAG, "Cleared cached DOs older than $maxAgeDays days")
    }

    /**
     * Get DO by ID from local cache
     */
    suspend fun getDOFromCache(doId: Long): CachedDeliveryOrder? {
        return cachedDeliveryOrderDao.getById(doId)
    }

    /**
     * Clear all cached DOs
     */
    suspend fun clearAllCache() {
        cachedDeliveryOrderDao.deleteAll()
        Log.d(TAG, "Cleared all cached DOs")
    }

    // ==================== HELPER ====================

    /**
     * Check if we should use offline data
     */
    suspend fun shouldUseOfflineData(): Boolean {
        val isOfflineMode = settingsManager.isOfflineModeEnabled().first()
        val isConnected = networkStateManager.isConnected.value
        return isOfflineMode || !isConnected
    }

    /**
     * Get cached DO count
     */
    suspend fun getCachedDOCount(): Int {
        val branchCode = sessionManager.getBranchCode().first() ?: ""
        val companyCode = sessionManager.getCompanyCode().first() ?: ""
        return cachedDeliveryOrderDao.getCountByBranch(branchCode, companyCode)
    }

    /**
     * Sync result sealed class
     */
    sealed class SyncResult {
        data class Success(val totalCount: Int, val newCount: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object OfflineMode : SyncResult()
    }
}
