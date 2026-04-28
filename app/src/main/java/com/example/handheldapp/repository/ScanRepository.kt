package com.example.handheldapp.repository

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.example.handheldapp.data.api.ScanApiService
import com.example.handheldapp.data.api.ScanRequest
import com.example.handheldapp.data.local.dao.CachedProductDao
import com.example.handheldapp.data.local.dao.PendingScanDao
import com.example.handheldapp.data.local.entity.PendingScan
import com.example.handheldapp.data.model.ScanItem
import com.example.handheldapp.utils.AppStatus
import com.example.handheldapp.utils.AppStatusManager
import com.example.handheldapp.utils.NetworkMonitor
import com.example.handheldapp.utils.Resource
import com.example.handheldapp.utils.TransactionIdGenerator
import com.example.handheldapp.worker.AutoSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val scanApi: ScanApiService,
    private val pendingScanDao: PendingScanDao,
    private val cachedProductDao: CachedProductDao,
    private val networkMonitor: NetworkMonitor,
    private val appStatusManager: AppStatusManager,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ScanRepository"

        /**
         * ISO 8601 formatter for timestamps (compatible with API 21+)
         * Format: "2026-04-10T14:30:25.123Z"
         */
        private val iso8601Formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        /**
         * Convert timestamp millis to ISO 8601 string
         * Compatible with API 21+
         */
        private fun Long.toIso8601String(): String {
            return iso8601Formatter.format(Date(this))
        }

        /**
         * Get current timestamp as ISO 8601 string
         * Compatible with API 21+
         */
        private fun getCurrentIso8601(): String {
            return System.currentTimeMillis().toIso8601String()
        }
    }

    // Data class untuk history dengan status completed
    // ★ UPDATED: Added totals for grand total display
    data class ScanHistoryData(
        val items: List<ScanItem>,
        val isCompleted: Boolean,
        val grandTotalPcs: Int = 0,  // ★ Total PCS keseluruhan (hasil konversi)
        val totalKarton: Int = 0,     // ★ Total Karton
        val totalPcsOnly: Int = 0     // ★ Total PCS saja (tanpa konversi)
    )

    // Data class untuk bulk sync response
    data class BulkSyncResponse(
        val totalSynced: Int,
        val totalFailed: Int,
        val failures: List<BulkSyncFailure> = emptyList()
    )

    data class BulkSyncFailure(
        val id: Long,
        val sku: String,
        val reason: String
    )

    // Ambil riwayat scan dengan status completed (GROUP BY - Summary)
    // ★ UPDATED: Now includes totals from API
    fun getScanHistory(doId: String): Flow<Resource<ScanHistoryData>> = flow {
        emit(Resource.Loading())
        try {
            android.util.Log.d("ScanRepository", "📊 Calling getScanHistory (GROUP BY) for DO: $doId")
            val response = scanApi.getScanHistory(doId)
            android.util.Log.d("ScanRepository", "📊 getScanHistory response: ${response.data?.size} items")
            android.util.Log.d("ScanRepository", "📊 Totals: grandTotalPcs=${response.totals?.grandTotalPcs}, totalKarton=${response.totals?.totalKarton}")
            if (response.success) {
                val historyData = ScanHistoryData(
                    items = response.data ?: emptyList(),
                    isCompleted = response.isCompleted,
                    grandTotalPcs = response.totals?.grandTotalPcs ?: 0,
                    totalKarton = response.totals?.totalKarton ?: 0,
                    totalPcsOnly = response.totals?.totalPcsOnly ?: 0
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
     * Cek Barcode - OFFLINE CAPABLE dengan MAINTENANCE MODE support
     * Priority:
     * 1. Maintenance mode → CACHE FIRST (server bisa mati/upgrade)
     * 2. Offline → CACHE ONLY
     * 3. Online normal → API FIRST, fallback cache
     *
     * @param barcode Barcode yang di-scan
     * @param gudang Kode gudang untuk filter (Business di tgu_ms_product_Business)
     */
    fun checkBarcode(barcode: String, gudang: String? = null): Flow<Resource<List<ScanItem.BarcodeProduct>>> = flow {
        emit(Resource.Loading())

        val isOnline = networkMonitor.isCurrentlyConnected()
        val isMaintenance = appStatusManager.getCurrentStatus() == com.example.handheldapp.utils.AppStatus.MAINTENANCE
        val allowSync = appStatusManager.isSyncAllowed()  // true = bisa API, false = cache only
        val maintenanceType = appStatusManager.getMaintenanceType() ?: "minor"

        Log.d(TAG, "🔍 checkBarcode: barcode=$barcode, gudang=$gudang, isOnline=$isOnline")
        Log.d(TAG, "   isMaintenance=$isMaintenance, allowSync=$allowSync, type=$maintenanceType")

        // PRIORITY 1: MAINTENANCE MODE - Check if sync still allowed
        if (isMaintenance) {
            // MINOR maintenance = allowSync true → masih bisa call API
            if (allowSync && isOnline) {
                Log.d(TAG, "🟢 MINOR MAINTENANCE - API still available")
                // Fall through to normal API flow below
            } else {
                // MAJOR/CRITICAL or offline → cache only
                Log.d(TAG, "🟠 MAINTENANCE MODE (${maintenanceType.uppercase()}) - using cache only")
                val cached = checkBarcodeFromCache(barcode, gudang)

                if (cached.isNotEmpty()) {
                    Log.d(TAG, "✅ Found ${cached.size} products in cache")
                    emit(Resource.Success(cached))
                } else {
                    val helpText = if (maintenanceType == "major") {
                        "Barcode belum ada di cache. Data akan otomatis sync setelah maintenance selesai."
                    } else {
                        "Barcode tidak ditemukan di cache offline. Hubungi IT untuk bantuan."
                    }
                    emit(Resource.Error(helpText))
                }
                return@flow
            }
        }

        // PRIORITY 2: OFFLINE → CACHE ONLY
        if (!isOnline) {
            Log.d(TAG, "📴 Offline mode, checking local cache")
            val cached = checkBarcodeFromCache(barcode, gudang)

            if (cached.isNotEmpty()) {
                Log.d(TAG, "✅ Found ${cached.size} products in cache")
                emit(Resource.Success(cached))
            } else {
                emit(Resource.Error("Barcode tidak ditemukan di cache offline. Sync data saat online."))
            }
            return@flow
        }

        // PRIORITY 3: ONLINE NORMAL → API FIRST, fallback cache
        try {
            val gudangParam = gudang ?: ""
            val response = scanApi.checkBarcode(barcode, gudangParam)

            if (response.success) {
                emit(Resource.Success(response.data ?: emptyList()))
            } else {
                // Fallback ke cache jika API tidak menemukan
                Log.d(TAG, "⚠️ API returned empty, checking cache...")
                val cached = checkBarcodeFromCache(barcode, gudang)
                if (cached.isNotEmpty()) {
                    Log.d(TAG, "✅ Found ${cached.size} products in cache (API fallback)")
                    emit(Resource.Success(cached))
                } else {
                    emit(Resource.Error(response.message ?: "Barcode tidak ditemukan"))
                }
            }
        } catch (e: Exception) {
            // Network error - fallback ke cache
            Log.w(TAG, "⚠️ API error, falling back to cache: ${e.message}")
            val cached = checkBarcodeFromCache(barcode, gudang)
            if (cached.isNotEmpty()) {
                emit(Resource.Success(cached))
            } else {
                emit(Resource.Error("Koneksi gagal dan barcode tidak ada di cache offline"))
            }
        }
    }

    /**
     * Check barcode dari local cache
     */
    private suspend fun checkBarcodeFromCache(barcode: String, gudang: String?): List<ScanItem.BarcodeProduct> {
        val cachedProducts = if (!gudang.isNullOrEmpty()) {
            cachedProductDao.findByBarcode(barcode, gudang)
        } else {
            cachedProductDao.findByBarcodeAll(barcode)
        }

        return cachedProducts.map { product ->
            ScanItem.BarcodeProduct(
                sku = product.sku,
                productName = product.productName,
                pcsPerKarton = parsePcsPerKarton(product.pcsPerKarton)
            )
        }
    }

    /**
     * Parse pcs per karton dari string ke Int
     * PENTING: Hanya ambil angka jika diikuti "PC", abaikan "KG"
     * Input: "20 PC / CTN" -> 20, "25 KG / CTN" -> 1 (default)
     * Match: "20 PC / CTN", "24PC/CTN", "15 pc/ctn"
     * No match (use default 1): "25 KG / CTN", "10 KG/CTN"
     */
    private fun parsePcsPerKarton(value: String?): Int {
        if (value.isNullOrBlank()) return 1

        // Regex: angka diikuti "PC" (case insensitive)
        val regex = Regex("(\\d+)\\s*PC", RegexOption.IGNORE_CASE)
        val match = regex.find(value)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }

    /**
     * Search Products - OFFLINE CAPABLE
     * Untuk mode manual ketika barcode rusak
     * @param keyword Kata kunci pencarian
     * @param gudang Kode gudang untuk filter
     */
    suspend fun searchProducts(keyword: String, gudang: String? = null): List<ScanItem.BarcodeProduct> {
        val isOnline = networkMonitor.isCurrentlyConnected()

        return if (isOnline) {
            try {
                val gudangParam = gudang ?: ""
                Log.d(TAG, "🔍 searchProducts (online): keyword=$keyword, gudang=$gudangParam")
                val response = scanApi.searchProducts(keyword, gudangParam)
                if (response.success) {
                    response.data ?: emptyList()
                } else {
                    // Fallback ke cache
                    Log.d(TAG, "⚠️ API search failed, using cache")
                    searchProductsFromCache(keyword, gudang)
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Search API error, using cache: ${e.message}")
                searchProductsFromCache(keyword, gudang)
            }
        } else {
            Log.d(TAG, "📴 searchProducts (offline): keyword=$keyword")
            searchProductsFromCache(keyword, gudang)
        }
    }

    /**
     * Search products dari local cache
     */
    private suspend fun searchProductsFromCache(keyword: String, gudang: String?): List<ScanItem.BarcodeProduct> {
        val cached = if (!gudang.isNullOrEmpty()) {
            cachedProductDao.searchProducts(keyword, gudang)
        } else {
            cachedProductDao.searchProductsAll(keyword)
        }

        return cached.map { product ->
            ScanItem.BarcodeProduct(
                sku = product.sku,
                productName = product.productName,
                pcsPerKarton = parsePcsPerKarton(product.pcsPerKarton)
            )
        }
    }

    // Submit Scan - OFFLINE-FIRST with maintenance/update awareness
    fun submitScan(
        doId: String,
        sku: String,
        qtyKarton: Int,
        qtyPcs: Int,
        isKarton: Boolean,
        barcode: String? = null, // Optional: untuk mode manual (barcode rusak)
        pcsPerKarton: Int = 1    // Konversi: 1 karton = X pcs
    ): Flow<Resource<ScanItem.SubmitResponse>> = flow {
        emit(Resource.Loading())

        val isOnline = networkMonitor.isCurrentlyConnected()
        val syncAllowed = appStatusManager.isSyncAllowed()
        val appStatus = appStatusManager.getCurrentStatus()

        // Generate rec_trans_id dengan format: MAC|BARCODE-PCSxxxxxxxxxxxxx
        val recTransId = TransactionIdGenerator.generate(context, barcode, qtyKarton, qtyPcs, pcsPerKarton)
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📡 SUBMIT SCAN DEBUG INFO:")
        Log.d(TAG, "   DO ID: $doId")
        Log.d(TAG, "   SKU: $sku")
        Log.d(TAG, "   Qty: $qtyKarton KRT, $qtyPcs PCS")
        Log.d(TAG, "   isOnline: $isOnline")
        Log.d(TAG, "   syncAllowed: $syncAllowed")
        Log.d(TAG, "   appStatus: $appStatus")
        Log.d(TAG, "   pcsPerKarton: $pcsPerKarton")
        Log.d(TAG, "   recTransId: $recTransId")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // ✅ PRIORITY 1: ONLINE - Always try online submit first if network available
        if (isOnline) {
            // Check if sync should be blocked (major maintenance, critical update, etc)
            val shouldBlockSync = !syncAllowed && (
                    appStatus == AppStatus.MAINTENANCE ||
                            appStatus == AppStatus.UPDATE_REQUIRED
                    )

            if (shouldBlockSync) {
                val maintenanceType = appStatusManager.getMaintenanceType() ?: "minor"
                Log.w(TAG, "⚠️ Sync blocked: status=$appStatus, maintenanceType=$maintenanceType")

                // Save locally for major maintenance or critical updates
                saveToLocalAndScheduleSync(doId, sku, barcode, qtyKarton, qtyPcs, pcsPerKarton, recTransId)

                val message = when (appStatus) {
                    AppStatus.MAINTENANCE -> "Tersimpan lokal (maintenance). Akan sync setelah maintenance selesai."
                    AppStatus.UPDATE_REQUIRED -> "Tersimpan lokal. Update app untuk sync data."
                    else -> "Tersimpan lokal, akan sync otomatis"
                }

                emit(Resource.Success(ScanItem.SubmitResponse(true, message)))
                return@flow
            }

            // ONLINE + NOT BLOCKED: Try submit to server
            try {
                Log.d(TAG, "🚀 TRYING API SUBMIT...")
                val scannedAt = getCurrentIso8601()
                val request = ScanRequest(doId, sku, barcode, qtyKarton, qtyPcs, isKarton, pcsPerKarton, recTransId, scannedAt)

                Log.d(TAG, "📤 Request payload: doId=$doId, sku=$sku, qtyK=$qtyKarton, qtyP=$qtyPcs")
                val response = scanApi.submitScan(request)
                Log.d(TAG, "📥 API Response received: success=${response.success}")

                if (response.success) {
                    Log.d(TAG, "✅ ✅ ✅ ONLINE SUBMIT SUCCESS - DO Status: ${response.doStatus}")
                    Log.d(TAG, "   Message: ${response.message}")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    emit(Resource.Success(
                        ScanItem.SubmitResponse(
                            success = true,
                            message = response.message ?: "Scan berhasil disimpan",
                            doStatus = response.doStatus
                        )
                    ))
                } else {
                    // Server error - fallback to local
                    Log.w(TAG, "⚠️ ⚠️ ⚠️ API RETURNED ERROR: ${response.message}")
                    Log.w(TAG, "   Falling back to local storage...")
                    Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    saveToLocalAndScheduleSync(doId, sku, barcode, qtyKarton, qtyPcs, pcsPerKarton, recTransId)
                    emit(Resource.Success(
                        ScanItem.SubmitResponse(
                            true,
                            "Tersimpan lokal, akan sync saat server ready"
                        )
                    ))
                }
            } catch (e: Exception) {
                // Network error - fallback to local
                Log.e(TAG, "❌ ❌ ❌ API CALL EXCEPTION - Type: ${e.javaClass.simpleName}")
                Log.e(TAG, "   Message: ${e.message}")
                Log.e(TAG, "   Cause: ${e.cause?.message}")
                Log.e(TAG, "   Stack: ${e.stackTrace.take(3).joinToString(" -> ")}")
                Log.e(TAG, "   ⚠️ KEMUNGKINAN PENYEBAB:")
                Log.e(TAG, "      1. API URL tidak accessible dari emulator")
                Log.e(TAG, "      2. Laravel server belum running")
                Log.e(TAG, "      3. Network firewall block")
                Log.e(TAG, "   💡 SOLUSI:")
                Log.e(TAG, "      - Emulator: pastikan BASE_URL = http://10.0.2.2:8000/api/")
                Log.e(TAG, "      - Real Device: pastikan BASE_URL = http://192.168.x.x:8000/api/")
                Log.e(TAG, "      - Cek Laravel: php artisan serve --host=0.0.0.0")
                Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                saveToLocalAndScheduleSync(doId, sku, barcode, qtyKarton, qtyPcs, pcsPerKarton, recTransId)
                emit(Resource.Success(
                    ScanItem.SubmitResponse(
                        true,
                        "Tersimpan lokal (offline), akan sync otomatis"
                    )
                ))
            }
        } else {
            // OFFLINE: Save locally
            Log.d(TAG, "📴 Device offline - saving locally")
            saveToLocalAndScheduleSync(doId, sku, barcode, qtyKarton, qtyPcs, pcsPerKarton, recTransId)

            emit(Resource.Success(
                ScanItem.SubmitResponse(
                    true,
                    "Tersimpan lokal (offline), akan sync otomatis saat online"
                )
            ))
        }
    }

    /**
     * Simpan scan ke database lokal dan schedule WorkManager untuk sync
     */
    private suspend fun saveToLocalAndScheduleSync(
        doId: String,
        sku: String,
        barcode: String?,
        qtyKarton: Int,
        qtyPcs: Int,
        pcsPerKarton: Int = 1,
        recTransId: String? = null
    ) {
        // TODO: Get userId, branchCode, companyCode from SessionManager atau SharedPreferences
        // For now, using placeholder values
        val pendingScan = PendingScan(
            doId = doId,
            sku = sku,
            barcode = barcode,
            qtyKarton = qtyKarton,
            qtyPcs = qtyPcs,
            pcsPerKarton = pcsPerKarton,
            recTransId = recTransId,
            scannedAt = System.currentTimeMillis(),
            userId = 0, // TODO: Get from session
            branchCode = "", // TODO: Get from session
            companyCode = "" // TODO: Get from session
        )

        pendingScanDao.insert(pendingScan)
        Log.d(TAG, "✅ Saved to local DB: $pendingScan")

        // Schedule sync worker using AutoSyncWorker
        workManager.enqueueUniqueWork(
            AutoSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )
        Log.d(TAG, "📅 Sync worker scheduled")
    }

    /**
     * Get jumlah pending scans yang belum di-sync
     */
    fun getPendingScansCount(): Flow<Int> = pendingScanDao.getPendingCount()

    /**
     * Alias for getPendingScansCount for AutoSyncWorker compatibility
     */
    fun getPendingScanCount(): Flow<Int> = getPendingScansCount()

    /**
     * Get all pending scans
     */
    fun getAllPendingScans(): Flow<List<PendingScan>> = pendingScanDao.getAllPendingScansFlow()

    /**
     * Alias for getAllPendingScans for AutoSyncWorker compatibility
     */
    fun getPendingScans(): Flow<List<PendingScan>> = getAllPendingScans()

    /**
     * Get pending summary per DO untuk tampilan di UI
     */
    suspend fun getPendingSummaryForDo(doId: String) = pendingScanDao.getPendingSummary(doId)

    /**
     * Trigger manual sync (jika user mau force sync)
     */
    fun triggerManualSync() {
        workManager.enqueueUniqueWork(
            AutoSyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )
        Log.d(TAG, "🔄 Manual sync triggered")
    }

    /**
     * Direct sync - untuk langsung sync tanpa WorkManager
     * Berguna untuk dapat feedback real-time di UI
     *
     * @return Result dengan jumlah synced dan failed
     */
    suspend fun directSync(): Result<BulkSyncResponse> {
        return try {
            val pendingScans = pendingScanDao.getPendingScans()

            if (pendingScans.isEmpty()) {
                Log.d(TAG, "📭 No pending scans to sync")
                return Result.success(BulkSyncResponse(0, 0))
            }

            Log.d(TAG, "🚀 Direct sync starting: ${pendingScans.size} pending scans")
            bulkSyncPendingScans(pendingScans)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Direct sync error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Bulk sync pending scans ke server
     * Digunakan oleh AutoSyncWorker setelah maintenance selesai
     *
     * @param pendingScans List of pending scans to sync
     * @return Result with BulkSyncResponse (synced count, failed count, failures)
     */
    suspend fun bulkSyncPendingScans(pendingScans: List<PendingScan>): Result<BulkSyncResponse> {
        if (pendingScans.isEmpty()) {
            return Result.success(BulkSyncResponse(0, 0))
        }

        return try {
            Log.d(TAG, "🚀 bulkSyncPendingScans: ${pendingScans.size} scans")

            // Convert PendingScan to request format
            val scans = pendingScans.map { pending ->
                // ★ Convert timestamp millis to ISO 8601 string for backend (compatible API 21+)
                val scannedAtTime = pending.scannedAt.toIso8601String()

                mapOf(
                    "id" to pending.id,
                    "do_id" to pending.doId,
                    "sku" to pending.sku,
                    "barcode" to (pending.barcode ?: ""),
                    "qty_karton" to pending.qtyKarton,
                    "qty_pcs" to pending.qtyPcs,
                    "pcs_per_karton" to pending.pcsPerKarton,
                    "rec_trans_id" to (pending.recTransId ?: ""),
                    "scanned_at" to scannedAtTime // ★ Send as ISO 8601 string, not millis
                )
            }

            val request = mapOf("scans" to scans)

            // Simpan IDs untuk delete setelah sync berhasil
            val pendingIds = pendingScans.map { it.id }

            // Call API
            val response = scanApi.bulkSyncScans(request)

            if (response.isSuccessful) {
                val body = response.body()

                // API returns: synced_count, errors (array)
                val totalSynced = (body?.get("synced_count") as? Number)?.toInt()
                    ?: (body?.get("total_synced") as? Number)?.toInt()
                    ?: 0
                val errorsArray = (body?.get("errors") as? List<*>) ?: emptyList<Any>()
                val totalFailed = errorsArray.size

                Log.d(TAG, "✅ Bulk sync SUCCESS: synced=$totalSynced, failed=$totalFailed")
                Log.d(TAG, "📋 Response body: $body")

                // Delete synced scans from local database
                // Jika ada yang berhasil sync, hapus semua yang dikirim (kecuali yang error)
                if (totalSynced > 0) {
                    pendingScanDao.deleteByIds(pendingIds)
                    Log.d(TAG, "🗑️ Deleted ${pendingIds.size} synced scans from local DB")
                }

                Result.success(BulkSyncResponse(totalSynced, totalFailed))
            } else {
                val error = "Bulk sync failed: ${response.code()} ${response.message()}"
                Log.e(TAG, "❌ $error")
                Result.failure(Exception(error))
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Bulk sync exception: ${e.message}", e)
            Result.failure(e)
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