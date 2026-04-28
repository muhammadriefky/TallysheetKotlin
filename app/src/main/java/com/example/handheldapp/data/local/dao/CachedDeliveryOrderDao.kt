package com.example.handheldapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.handheldapp.data.local.entity.CachedDeliveryOrder
import kotlinx.coroutines.flow.Flow

/**
 * DAO untuk operasi CRUD CachedDeliveryOrder
 * Digunakan untuk offline mode - akses DO tanpa internet
 */
@Dao
interface CachedDeliveryOrderDao {

    // ============ INSERT/UPDATE ============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deliveryOrder: CachedDeliveryOrder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deliveryOrders: List<CachedDeliveryOrder>)

    @Update
    suspend fun update(deliveryOrder: CachedDeliveryOrder)

    // ============ DELETE ============

    @Delete
    suspend fun delete(deliveryOrder: CachedDeliveryOrder)

    @Query("DELETE FROM cached_delivery_orders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cached_delivery_orders WHERE branchCode = :branchCode")
    suspend fun deleteByBranch(branchCode: String)

    @Query("DELETE FROM cached_delivery_orders WHERE lastSyncAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM cached_delivery_orders")
    suspend fun deleteAll()

    // ============ QUERY ============

    /**
     * Get all DOs for a branch (sorted by date desc)
     */
    @Query("""
        SELECT * FROM cached_delivery_orders 
        WHERE branchCode = :branchCode AND companyCode = :companyCode
        ORDER BY createdAt DESC
    """)
    fun getAllByBranch(branchCode: String, companyCode: String): Flow<List<CachedDeliveryOrder>>

    /**
     * Get DO by ID
     */
    @Query("SELECT * FROM cached_delivery_orders WHERE id = :id")
    suspend fun getById(id: Long): CachedDeliveryOrder?

    /**
     * Get DO by code
     */
    @Query("SELECT * FROM cached_delivery_orders WHERE doCode = :doCode")
    suspend fun getByCode(doCode: String): CachedDeliveryOrder?

    /**
     * Get DOs that can be scanned (arrival or scanning status)
     */
    @Query("""
        SELECT * FROM cached_delivery_orders 
        WHERE branchCode = :branchCode 
        AND companyCode = :companyCode
        AND progressStatus IN ('arrival', 'scanning')
        ORDER BY createdAt DESC
    """)
    fun getScannableDOs(branchCode: String, companyCode: String): Flow<List<CachedDeliveryOrder>>

    /**
     * Get new DOs that haven't been notified
     */
    @Query("""
        SELECT * FROM cached_delivery_orders 
        WHERE branchCode = :branchCode 
        AND companyCode = :companyCode
        AND isNew = 1
        AND notifiedAt IS NULL
        ORDER BY createdAt DESC
    """)
    suspend fun getNewUnnotifiedDOs(branchCode: String, companyCode: String): List<CachedDeliveryOrder>

    /**
     * Mark DO as notified
     */
    @Query("UPDATE cached_delivery_orders SET notifiedAt = :timestamp WHERE id = :id")
    suspend fun markAsNotified(id: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark DO as seen (not new anymore)
     */
    @Query("UPDATE cached_delivery_orders SET isNew = 0 WHERE id = :id")
    suspend fun markAsSeen(id: Long)

    /**
     * Mark all DOs as seen
     */
    @Query("UPDATE cached_delivery_orders SET isNew = 0 WHERE branchCode = :branchCode")
    suspend fun markAllAsSeen(branchCode: String)

    /**
     * Get count of new DOs (for badge)
     */
    @Query("""
        SELECT COUNT(*) FROM cached_delivery_orders 
        WHERE branchCode = :branchCode 
        AND companyCode = :companyCode
        AND isNew = 1
    """)
    fun getNewDOCount(branchCode: String, companyCode: String): Flow<Int>

    /**
     * Get count of new DOs (non-flow for worker)
     */
    @Query("""
        SELECT COUNT(*) FROM cached_delivery_orders 
        WHERE branchCode = :branchCode 
        AND companyCode = :companyCode
        AND isNew = 1
    """)
    suspend fun getNewDOCountSync(branchCode: String, companyCode: String): Int

    /**
     * Get total count
     */
    @Query("SELECT COUNT(*) FROM cached_delivery_orders")
    suspend fun getCount(): Int

    /**
     * Get count by branch
     */
    @Query("""
        SELECT COUNT(*) FROM cached_delivery_orders 
        WHERE branchCode = :branchCode AND companyCode = :companyCode
    """)
    suspend fun getCountByBranch(branchCode: String, companyCode: String): Int

    /**
     * Get last sync time
     */
    @Query("SELECT MAX(lastSyncAt) FROM cached_delivery_orders WHERE branchCode = :branchCode")
    suspend fun getLastSyncTime(branchCode: String): Long?

    /**
     * Search DOs by keyword (DO code or supplier name)
     */
    @Query("""
        SELECT * FROM cached_delivery_orders 
        WHERE branchCode = :branchCode 
        AND companyCode = :companyCode
        AND (doCode LIKE '%' || :keyword || '%' OR supplierName LIKE '%' || :keyword || '%')
        ORDER BY createdAt DESC
        LIMIT 50
    """)
    suspend fun search(branchCode: String, companyCode: String, keyword: String): List<CachedDeliveryOrder>

    /**
     * Get DOs by status
     */
    @Query("""
        SELECT * FROM cached_delivery_orders 
        WHERE branchCode = :branchCode 
        AND companyCode = :companyCode
        AND progressStatus = :status
        ORDER BY createdAt DESC
    """)
    fun getByStatus(branchCode: String, companyCode: String, status: String): Flow<List<CachedDeliveryOrder>>

    /**
     * Update scanned quantities
     */
    @Query("""
        UPDATE cached_delivery_orders 
        SET scannedQtyKarton = :karton, scannedQtyPcs = :pcs, lastSyncAt = :syncAt
        WHERE id = :id
    """)
    suspend fun updateScannedQty(id: Long, karton: Int, pcs: Int, syncAt: Long = System.currentTimeMillis())

    /**
     * Update progress status
     */
    @Query("UPDATE cached_delivery_orders SET progressStatus = :status, lastSyncAt = :syncAt WHERE id = :id")
    suspend fun updateProgressStatus(id: Long, status: String, syncAt: Long = System.currentTimeMillis())
}
