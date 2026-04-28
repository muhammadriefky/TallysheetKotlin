package com.example.handheldapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity untuk cache Delivery Order secara offline
 * Data ini di-sync dari server dan tersedia untuk akses offline
 */
@Entity(
    tableName = "cached_delivery_orders",
    indices = [
        Index(value = ["doCode"]),
        Index(value = ["branchCode"]),
        Index(value = ["progressStatus"]),
        Index(value = ["lastSyncAt"])
    ]
)
data class CachedDeliveryOrder(
    @PrimaryKey
    val id: Long,

    // DO Info
    val doCode: String,
    val doDate: String? = null,
    val doNumberSupplier: String? = null,

    // Supplier Info
    val supplierCode: String? = null,
    val supplierName: String? = null,

    // Status
    val progressStatus: String? = null, // arrival, scanning, scan_completed, etc.
    val approvalStatus: String? = null, // pending, approved, rejected

    // Branch & Company
    val branchCode: String,
    val companyCode: String,

    // Quantities
    val totalQtyKarton: Int = 0,
    val totalQtyPcs: Int = 0,
    val scannedQtyKarton: Int = 0,
    val scannedQtyPcs: Int = 0,

    // Item count
    val itemCount: Int = 0,

    // Timestamps
    val createdAt: String? = null,
    val arrivedAt: String? = null,

    // Sync metadata
    val lastSyncAt: Long = System.currentTimeMillis(),
    val isNew: Boolean = false, // Flag untuk DO baru yang belum dilihat user
    val notifiedAt: Long? = null // Timestamp notifikasi dikirim
) {
    companion object {
        // Progress Status
        const val STATUS_ARRIVAL = "arrival"
        const val STATUS_SCANNING = "scanning"
        const val STATUS_SCAN_COMPLETED = "scan_completed"
        const val STATUS_COMPLETED = "completed"

        // Approval Status
        const val APPROVAL_PENDING = "pending"
        const val APPROVAL_APPROVED = "approved"
        const val APPROVAL_REJECTED = "rejected"
    }

    /**
     * Check apakah DO ini bisa di-scan
     */
    fun canScan(): Boolean {
        return progressStatus in listOf(STATUS_ARRIVAL, STATUS_SCANNING)
    }

    /**
     * Get display status
     */
    fun getDisplayStatus(): String {
        return when (progressStatus) {
            STATUS_ARRIVAL -> "Menunggu Scan"
            STATUS_SCANNING -> "Sedang Scan"
            STATUS_SCAN_COMPLETED -> "Scan Selesai"
            STATUS_COMPLETED -> "Selesai"
            else -> progressStatus ?: "Unknown"
        }
    }
}
