package com.example.handheldapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity untuk menyimpan cache master product
 * Digunakan untuk validasi barcode saat offline
 *
 * Data di-sync dari server saat:
 * - Login sukses
 * - App startup (jika data > 24 jam)
 * - Manual refresh
 */
@Entity(
    tableName = "cached_products",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["sku"]),
        Index(value = ["gudangCode"])
    ]
)
data class CachedProduct(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sku: String,                    // SKU_Business
    val barcode: String,                // SKU_Barcode_pcs
    val productName: String,            // SKU_description
    val pcsPerKarton: String = "1",     // SKU_convertpcs - String karena bisa "5 KG / PC"
    val gudangCode: String,             // Business

    val lastSyncAt: Long = System.currentTimeMillis()
)
