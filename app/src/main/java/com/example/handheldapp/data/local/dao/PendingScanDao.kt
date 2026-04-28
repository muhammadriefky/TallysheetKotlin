package com.example.handheldapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.handheldapp.data.local.entity.PendingScan
import kotlinx.coroutines.flow.Flow

/**
 * DAO untuk operasi CRUD PendingScan
 * Digunakan untuk offline-first scanning
 */
@Dao
interface PendingScanDao {

    // ============ INSERT ============
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: PendingScan): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scans: List<PendingScan>)

    // ============ UPDATE ============
    @Update
    suspend fun update(scan: PendingScan)

    @Query("UPDATE pending_scans SET syncStatus = :status, errorMessage = :error, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, error: String? = null)

    @Query("UPDATE pending_scans SET syncStatus = :status, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markAsSynced(id: Long, status: String = "synced", syncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pending_scans SET syncStatus = 'syncing' WHERE syncStatus = 'pending' OR syncStatus = 'failed'")
    suspend fun markAllAsSyncing()

    @Query("UPDATE pending_scans SET syncStatus = 'pending' WHERE syncStatus = 'syncing'")
    suspend fun resetSyncingToPending()

    // ============ DELETE ============
    @Delete
    suspend fun delete(scan: PendingScan)

    @Query("DELETE FROM pending_scans WHERE syncStatus = 'synced'")
    suspend fun deleteSyncedRecords()

    @Query("DELETE FROM pending_scans WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM pending_scans")
    suspend fun deleteAll()

    // ============ QUERY ============

    /**
     * Get semua scan yang pending untuk di-sync
     * Urut berdasarkan waktu scan (oldest first)
     */
    @Query("""
        SELECT * FROM pending_scans 
        WHERE syncStatus = 'pending' OR syncStatus = 'failed' 
        ORDER BY scannedAt ASC
    """)
    suspend fun getPendingScans(): List<PendingScan>

    /**
     * Get semua scan yang pending sebagai Flow (untuk AutoSyncWorker)
     */
    @Query("""
        SELECT * FROM pending_scans 
        WHERE syncStatus = 'pending' OR syncStatus = 'failed' 
        ORDER BY scannedAt ASC
    """)
    fun getAllPendingScansFlow(): Flow<List<PendingScan>>

    /**
     * Get scan by DO ID (untuk tampilan lokal)
     */
    @Query("SELECT * FROM pending_scans WHERE doId = :doId ORDER BY scannedAt DESC")
    fun getScansByDoId(doId: String): Flow<List<PendingScan>>

    /**
     * Count pending scans (untuk badge indicator)
     */
    @Query("SELECT COUNT(*) FROM pending_scans WHERE syncStatus = 'pending' OR syncStatus = 'failed'")
    fun getPendingCount(): Flow<Int>

    /**
     * Count pending scans (non-flow untuk worker)
     */
    @Query("SELECT COUNT(*) FROM pending_scans WHERE syncStatus = 'pending' OR syncStatus = 'failed'")
    suspend fun getPendingCountSync(): Int

    /**
     * Get summary per SKU untuk DO tertentu (local calculation)
     * Alias: getPendingSummary
     */
    @Query("""
        SELECT doId, sku, 
            SUM(qtyKarton) as totalKarton, 
            SUM(qtyPcs) as totalPcs,
            COUNT(*) as scanCount
        FROM pending_scans 
        WHERE doId = :doId AND (syncStatus = 'pending' OR syncStatus = 'failed' OR syncStatus = 'syncing')
        GROUP BY doId, sku
    """)
    suspend fun getLocalScanSummaryByDo(doId: String): List<LocalScanSummary>

    /**
     * Alias for getLocalScanSummaryByDo (used by ScanRepository)
     */
    @Query("""
        SELECT doId, sku, 
            SUM(qtyKarton) as totalKarton, 
            SUM(qtyPcs) as totalPcs,
            COUNT(*) as scanCount
        FROM pending_scans 
        WHERE doId = :doId AND (syncStatus = 'pending' OR syncStatus = 'failed' OR syncStatus = 'syncing')
        GROUP BY doId, sku
    """)
    suspend fun getPendingSummary(doId: String): List<LocalScanSummary>

    /**
     * Get total local scans per DO
     */
    @Query("""
        SELECT SUM(qtyKarton) as totalKarton, SUM(qtyPcs) as totalPcs, COUNT(*) as scanCount
        FROM pending_scans 
        WHERE doId = :doId AND syncStatus != 'synced'
    """)
    suspend fun getLocalTotalsByDo(doId: String): LocalScanTotals?

    /**
     * Check apakah ada data pending untuk DO tertentu
     */
    @Query("SELECT EXISTS(SELECT 1 FROM pending_scans WHERE doId = :doId AND syncStatus != 'synced')")
    suspend fun hasPendingScansForDo(doId: String): Boolean
}

/**
 * Data class untuk summary per SKU
 */
data class LocalScanSummary(
    val doId: String,
    val sku: String,
    val totalKarton: Int,
    val totalPcs: Int,
    val scanCount: Int
)

/**
 * Data class untuk total scans
 */
data class LocalScanTotals(
    val totalKarton: Int?,
    val totalPcs: Int?,
    val scanCount: Int
)
