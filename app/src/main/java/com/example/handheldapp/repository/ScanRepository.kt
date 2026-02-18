package com.example.handheldapp.repository

import com.example.handheldapp.data.api.ScanApiService
import com.example.handheldapp.data.api.ScanRequest
import com.example.handheldapp.data.model.ScanItem
import com.example.handheldapp.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val scanApi: ScanApiService
) {
    // Data class untuk history dengan status completed
    data class ScanHistoryData(
        val items: List<ScanItem>,
        val isCompleted: Boolean
    )

    // Ambil riwayat scan dengan status completed (GROUP BY - Summary)
    fun getScanHistory(doId: String): Flow<Resource<ScanHistoryData>> = flow {
        emit(Resource.Loading())
        try {
            android.util.Log.d("ScanRepository", "📊 Calling getScanHistory (GROUP BY) for DO: $doId")
            val response = scanApi.getScanHistory(doId)
            android.util.Log.d("ScanRepository", "📊 getScanHistory response: ${response.data?.size} items")
            if (response.success) {
                val historyData = ScanHistoryData(
                    items = response.data ?: emptyList(),
                    isCompleted = response.isCompleted
                )
                emit(Resource.Success(historyData))
            } else {
                emit(Resource.Error(response.message ?: "Gagal mengambil riwayat"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ScanRepository", "❌ getScanHistory error: ${e.message}")
            emit(Resource.Error(e.message ?: "Terjadi kesalahan jaringan"))
        }
    }

    // Ambil activity log scan (No GROUP BY - All Records)
    fun getScanActivityLog(doId: String): Flow<Resource<ScanHistoryData>> = flow {
        emit(Resource.Loading())
        try {
            android.util.Log.d("ScanRepository", "📝 Calling getScanActivityLog (NO GROUP BY) for DO: $doId")
            val response = scanApi.getScanActivityLog(doId)
            android.util.Log.d("ScanRepository", "📝 getScanActivityLog response: ${response.data?.size} scans")
            if (response.success) {
                val historyData = ScanHistoryData(
                    items = response.data ?: emptyList(),
                    isCompleted = response.isCompleted
                )
                emit(Resource.Success(historyData))
            } else {
                emit(Resource.Error(response.message ?: "Gagal mengambil activity log"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ScanRepository", "❌ getScanActivityLog error: ${e.message}")
            emit(Resource.Error(e.message ?: "Terjadi kesalahan jaringan"))
        }
    }

    /**
     * Cek Barcode - Mencari SKU berdasarkan barcode di kolom SKU_Barcode_pcs
     * Return: List of BarcodeProduct yang mengandung SKU_Business dan info produk
     * @param barcode Barcode yang di-scan
     * @param gudang Kode gudang untuk filter (Business di tgu_ms_product_Business)
     */
    fun checkBarcode(barcode: String, gudang: String? = null): Flow<Resource<List<ScanItem.BarcodeProduct>>> = flow {
        emit(Resource.Loading())
        try {
            // PENTING: Retrofit tidak mengirim @Query parameter jika null
            // Jadi kita konversi null -> empty string agar parameter selalu dikirim
            val gudangParam = gudang ?: ""
            android.util.Log.d("ScanRepository", "🔍 checkBarcode: barcode=$barcode, gudangParam='$gudangParam'")
            val response = scanApi.checkBarcode(barcode, gudangParam)
            android.util.Log.d("ScanRepository", "🔍 checkBarcode response: success=${response.success}, count=${response.data?.size ?: 0}")
            if (response.success) {
                // Return produk yang ditemukan (bisa 0 atau lebih)
                emit(Resource.Success(response.data ?: emptyList()))
            } else {
                // Error dari server
                emit(Resource.Error(
                    response.message ?: "Barcode tidak ditemukan di tabel master produk"
                ))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Kesalahan jaringan saat cek barcode"))
        }
    }

    /**
     * Search Products - Untuk mode manual ketika barcode rusak
     * User bisa search berdasarkan SKU atau nama produk
     * @param keyword Kata kunci pencarian
     * @param gudang Kode gudang untuk filter (Business di tgu_ms_product_Business)
     */
    suspend fun searchProducts(keyword: String, gudang: String? = null): List<ScanItem.BarcodeProduct> {
        return try {
            // Konversi null -> empty string agar parameter selalu dikirim
            val gudangParam = gudang ?: ""
            android.util.Log.d("ScanRepository", "🔍 searchProducts: keyword=$keyword, gudangParam='$gudangParam'")
            val response = scanApi.searchProducts(keyword, gudangParam)
            if (response.success) {
                response.data ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("ScanRepository", "❌ searchProducts error: ${e.message}")
            emptyList()
        }
    }

    // Submit Scan
    fun submitScan(
        doId: String,
        sku: String,
        qtyKarton: Int,
        qtyPcs: Int,
        isKarton: Boolean,
        barcode: String? = null // Optional: untuk mode manual (barcode rusak)
    ): Flow<Resource<ScanItem.SubmitResponse>> = flow {
        emit(Resource.Loading())
        try {
            val request = ScanRequest(doId, sku, barcode, qtyKarton, qtyPcs, isKarton)
            val response = scanApi.submitScan(request)
            if (response.success) {
                emit(Resource.Success(
                    ScanItem.SubmitResponse(true, response.message ?: "Scan berhasil disimpan")
                ))
            } else {
                emit(Resource.Error(response.message ?: "Gagal menyimpan scan"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Kesalahan jaringan saat submit scan"))
        }
    }

    // Complete Scan - Mark DO as completed
    fun completeScan(doId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val response = scanApi.completeScan(doId)
            if (response.success) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(response.message ?: "Gagal menyelesaikan scan"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Kesalahan jaringan saat complete scan"))
        }
    }
}