package com.example.handheldapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.handheldapp.data.local.dao.CachedBranchDao
import com.example.handheldapp.data.local.dao.CachedCompanyDao
import com.example.handheldapp.data.local.dao.CachedDeliveryOrderDao
import com.example.handheldapp.data.local.dao.CachedProductDao
import com.example.handheldapp.data.local.dao.CachedUserDao
import com.example.handheldapp.data.local.dao.PendingScanDao
import com.example.handheldapp.data.local.entity.CachedBranch
import com.example.handheldapp.data.local.entity.CachedCompany
import com.example.handheldapp.data.local.entity.CachedDeliveryOrder
import com.example.handheldapp.data.local.entity.CachedProduct
import com.example.handheldapp.data.local.entity.CachedUser
import com.example.handheldapp.data.local.entity.PendingScan

/**
 * Room Database untuk local storage
 * Digunakan untuk offline-first architecture
 *
 * Tables:
 * - pending_scans: Menyimpan scan yang belum di-sync ke server
 * - cached_products: Cache master product untuk validasi barcode offline
 * - cached_companies: Cache companies untuk login offline
 * - cached_branches: Cache branches untuk login offline
 * - cached_users: Cache users untuk login offline
 * - cached_delivery_orders: Cache DO untuk akses offline
 */
@Database(
    entities = [
        PendingScan::class,
        CachedProduct::class,
        CachedCompany::class,
        CachedBranch::class,
        CachedUser::class,
        CachedDeliveryOrder::class
    ],
    version = 9,  // v8: Added CachedDeliveryOrder for offline mode
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingScanDao(): PendingScanDao
    abstract fun cachedProductDao(): CachedProductDao
    abstract fun cachedCompanyDao(): CachedCompanyDao
    abstract fun cachedBranchDao(): CachedBranchDao
    abstract fun cachedUserDao(): CachedUserDao
    abstract fun cachedDeliveryOrderDao(): CachedDeliveryOrderDao

    companion object {
        const val DATABASE_NAME = "handheld_app_db"
    }
}
