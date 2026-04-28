package com.example.handheldapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.handheldapp.data.local.entity.CachedProduct
import kotlinx.coroutines.flow.Flow

/**
 * DAO untuk operasi CRUD pada cached_products
 * Digunakan untuk validasi barcode saat offline
 */
@Dao
interface CachedProductDao {

    /**
     * Cari product berdasarkan barcode DAN gudang (exact match)
     */
    @Query("SELECT * FROM cached_products WHERE barcode = :barcode AND gudangCode = :gudangCode")
    suspend fun findByBarcode(barcode: String, gudangCode: String): List<CachedProduct>

    /**
     * Cari product berdasarkan barcode saja (semua gudang)
     */
    @Query("SELECT * FROM cached_products WHERE barcode = :barcode")
    suspend fun findByBarcodeAll(barcode: String): List<CachedProduct>

    /**
     * Cari berdasarkan SKU
     */
    @Query("SELECT * FROM cached_products WHERE sku = :sku AND gudangCode = :gudangCode")
    suspend fun findBySku(sku: String, gudangCode: String): CachedProduct?

    /**
     * Search products (untuk mode manual) - dengan filter gudang
     */
    @Query("SELECT * FROM cached_products WHERE (sku LIKE '%' || :keyword || '%' OR productName LIKE '%' || :keyword || '%') AND gudangCode = :gudangCode LIMIT 20")
    suspend fun searchProducts(keyword: String, gudangCode: String): List<CachedProduct>

    /**
     * Search products tanpa filter gudang
     */
    @Query("SELECT * FROM cached_products WHERE sku LIKE '%' || :keyword || '%' OR productName LIKE '%' || :keyword || '%' LIMIT 20")
    suspend fun searchProductsAll(keyword: String): List<CachedProduct>

    /**
     * Get all products untuk gudang tertentu
     */
    @Query("SELECT * FROM cached_products WHERE gudangCode = :gudangCode")
    fun getAllByGudang(gudangCode: String): Flow<List<CachedProduct>>

    /**
     * Get total count
     */
    @Query("SELECT COUNT(*) FROM cached_products")
    suspend fun getCount(): Int

    /**
     * Get count per gudang
     */
    @Query("SELECT COUNT(*) FROM cached_products WHERE gudangCode = :gudangCode")
    suspend fun getCountByGudang(gudangCode: String): Int

    /**
     * Get last sync time untuk gudang tertentu
     */
    @Query("SELECT MAX(lastSyncAt) FROM cached_products WHERE gudangCode = :gudangCode")
    suspend fun getLastSyncTime(gudangCode: String): Long?

    /**
     * Insert all products (replace if exists based on primary key)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<CachedProduct>)

    /**
     * Delete products by gudang (before re-sync)
     */
    @Query("DELETE FROM cached_products WHERE gudangCode = :gudangCode")
    suspend fun deleteByGudang(gudangCode: String)

    /**
     * Delete all cached products
     */
    @Query("DELETE FROM cached_products")
    suspend fun deleteAll()
}