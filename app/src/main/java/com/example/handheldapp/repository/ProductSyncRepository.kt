package com.example.handheldapp.repository

import android.util.Log
import com.example.handheldapp.data.api.ProductApiService
import com.example.handheldapp.data.local.dao.CachedProductDao
import com.example.handheldapp.data.local.entity.CachedProduct
import com.example.handheldapp.utils.NetworkMonitor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository untuk sync master products ke local cache
 * Digunakan untuk validasi barcode saat offline
 */
@Singleton
class ProductSyncRepository @Inject constructor(
    private val productApi: ProductApiService,
    private val cachedProductDao: CachedProductDao,
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        private const val TAG = "ProductSyncRepository"
        // Sync ulang jika data lebih dari 24 jam
        private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Sync master products dari server ke local cache
     * @param gudangCode Kode gudang untuk filter
     * @param forceSync Force sync meskipun data masih fresh
     */
    suspend fun syncMasterProducts(gudangCode: String, forceSync: Boolean = false): SyncResult {
        if (!networkMonitor.isCurrentlyConnected()) {
            Log.w(TAG, "⚠️ Cannot sync - device is offline")
            return SyncResult.Error("Device offline, tidak bisa sync")
        }

        // Check if we need to sync
        if (!forceSync) {
            val lastSync = cachedProductDao.getLastSyncTime(gudangCode)
            val timeSinceSync = System.currentTimeMillis() - (lastSync ?: 0)

            if (lastSync != null && timeSinceSync < SYNC_INTERVAL_MS) {
                val cachedCount = cachedProductDao.getCountByGudang(gudangCode)
                Log.d(TAG, "✅ Cache still fresh (${timeSinceSync / 1000 / 60} minutes old), $cachedCount products cached")
                return SyncResult.AlreadySynced(cachedCount)
            }
        }

        return try {
            Log.d(TAG, "🔄 Starting master product sync for gudang: $gudangCode")

            val response = productApi.getMasterProducts(gudangCode)

            if (response.isSuccessful && response.body()?.success == true) {
                val products = response.body()?.data?.mapNotNull { dto ->
                    // Skip products without barcode
                    if (dto.barcode.isNullOrBlank()) return@mapNotNull null

                    CachedProduct(
                        sku = dto.sku,
                        barcode = dto.barcode,
                        productName = dto.productName ?: dto.sku,
                        pcsPerKarton = dto.pcsPerKarton ?: "1",  // String
                        gudangCode = dto.gudangCode ?: gudangCode,
                        lastSyncAt = System.currentTimeMillis()
                    )
                } ?: emptyList()

                if (products.isNotEmpty()) {
                    // Clear old data for this gudang, then insert new
                    cachedProductDao.deleteByGudang(gudangCode)
                    cachedProductDao.insertAll(products)

                    Log.d(TAG, "✅ Synced ${products.size} products for gudang $gudangCode")
                    SyncResult.Success(products.size)
                } else {
                    Log.w(TAG, "⚠️ No products returned from server")
                    SyncResult.Empty
                }
            } else {
                val error = "Server error: ${response.code()}"
                Log.e(TAG, "❌ $error")
                SyncResult.Error(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync failed: ${e.message}", e)
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Get cached product count untuk gudang
     */
    suspend fun getCachedCount(gudangCode: String): Int {
        return cachedProductDao.getCountByGudang(gudangCode)
    }

    /**
     * Get total cached product count
     */
    suspend fun getTotalCachedCount(): Int {
        return cachedProductDao.getCount()
    }

    /**
     * Check if cache exists and is fresh
     */
    suspend fun isCacheValid(gudangCode: String): Boolean {
        val lastSync = cachedProductDao.getLastSyncTime(gudangCode) ?: return false
        val timeSinceSync = System.currentTimeMillis() - lastSync
        return timeSinceSync < SYNC_INTERVAL_MS
    }

    /**
     * Clear all cached products
     */
    suspend fun clearCache() {
        cachedProductDao.deleteAll()
        Log.d(TAG, "🗑️ Cache cleared")
    }

    /**
     * Sync result sealed class
     */
    sealed class SyncResult {
        data class Success(val count: Int) : SyncResult()
        data class AlreadySynced(val cachedCount: Int) : SyncResult()
        object Empty : SyncResult()
        data class Error(val message: String) : SyncResult()
    }
}
