package com.example.handheldapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity untuk menyimpan scan yang pending (belum di-sync ke server)
 * Data ini akan di-sync otomatis saat online kembali
 */
@Entity(
    tableName = "pending_scans",
    indices = [
        Index(value = ["doId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["scannedAt"])
    ]
)
data class PendingScan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // DO Info
    val doId: String,
    val doCode: String? = null, // do_h_code untuk referensi

    // Scan Data
    val sku: String,
    val barcode: String? = null,
    val qtyKarton: Int,
    val qtyPcs: Int,
    val pcsPerKarton: Int = 1, // Konversi: 1 karton = X pcs
    val productName: String? = null,
    val recTransId: String? = null, // Format: MAC|BARCODE-PCSxxxxxxxxxxxxx

    // Metadata
    val scannedAt: Long = System.currentTimeMillis(),
    val userId: Int,
    val userName: String? = null,
    val branchCode: String,
    val companyCode: String,

    // Sync Status
    val syncStatus: String = SYNC_PENDING, // pending, syncing, synced, failed
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val syncedAt: Long? = null
) {
    companion object {
        const val SYNC_PENDING = "pending"
        const val SYNC_SYNCING = "syncing"
        const val SYNC_SYNCED = "synced"
        const val SYNC_FAILED = "failed"
    }

    /**
     * Check apakah bisa di-retry
     */
    fun canRetry(): Boolean = retryCount < 5 && syncStatus != SYNC_SYNCED
}
