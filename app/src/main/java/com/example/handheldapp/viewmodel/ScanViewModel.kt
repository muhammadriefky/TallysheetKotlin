package com.example.handheldapp.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.handheldapp.data.model.ScanItem
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.data.model.StagingAreaAssignment
import com.example.handheldapp.repository.ProductSyncRepository
import com.example.handheldapp.repository.ScanRepository
import com.example.handheldapp.repository.StagingAreaRepository
import com.example.handheldapp.utils.AppStatusManager
import com.example.handheldapp.utils.NetworkMonitor
import com.example.handheldapp.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val stagingAreaRepository: StagingAreaRepository,
    private val networkMonitor: NetworkMonitor,
    private val productSyncRepository: ProductSyncRepository,
    private val appStatusManager: AppStatusManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _scanHistory = MutableLiveData<Resource<List<ScanItem>>>()
    val scanHistory: LiveData<Resource<List<ScanItem>>> = _scanHistory

    // ★ Totals for grand total display - using ScanRepository.ScanHistoryData totals
    private val _scanTotals = MutableLiveData<ScanRepository.ScanHistoryData?>()
    val scanTotals: LiveData<ScanRepository.ScanHistoryData?> = _scanTotals

    private val _scanActivityLog = MutableLiveData<Resource<List<ScanItem>>>()
    val scanActivityLog: LiveData<Resource<List<ScanItem>>> = _scanActivityLog

    private val _barcodeCheckResult = MutableLiveData<Resource<List<ScanItem.BarcodeProduct>>>()
    val barcodeCheckResult: LiveData<Resource<List<ScanItem.BarcodeProduct>>> = _barcodeCheckResult

    private val _scanSubmitResult = MutableLiveData<Resource<ScanItem.SubmitResponse>>()
    val scanSubmitResult: LiveData<Resource<ScanItem.SubmitResponse>> = _scanSubmitResult

    private val _completeScanResult = MutableLiveData<Resource<Unit>>()
    val completeScanResult: LiveData<Resource<Unit>> = _completeScanResult

    // DO Completion Status - untuk mengecek apakah DO sudah selesai saat activity dibuka
    private val _isDoCompleted = MutableLiveData<Boolean>()
    val isDoCompleted: LiveData<Boolean> = _isDoCompleted

    // ✅ Current DO Status - update real-time setelah submit scan
    private val _currentDoStatus = MutableLiveData<String?>()
    val currentDoStatus: LiveData<String?> = _currentDoStatus

    // Staging Area LiveData
    private val _stagingAreas = MutableLiveData<Resource<List<StagingArea>>>()
    val stagingAreas: LiveData<Resource<List<StagingArea>>> = _stagingAreas

    private val _stagingAssignment = MutableLiveData<Resource<StagingAreaAssignment>>()
    val stagingAssignment: LiveData<Resource<StagingAreaAssignment>> = _stagingAssignment

    private val _stagingAssignResult = MutableLiveData<Resource<String>>()
    val stagingAssignResult: LiveData<Resource<String>> = _stagingAssignResult

    // === OFFLINE MODE ===
    // Network status - true = online, false = offline
    val isOnline: LiveData<Boolean> = networkMonitor.isOnline.asLiveData()

    // Pending scans count
    val pendingScansCount: LiveData<Int> = repository.getPendingScansCount().asLiveData()

    private var currentDoId: String? = null
    private var currentGudangCode: String? = null // Kode gudang untuk filter scan
    private var _isStagingSelected = false
    val isStagingSelected: Boolean get() = _isStagingSelected

    fun setDeliveryOrderId(id: String) {
        currentDoId = id
        loadHistory()
        loadActivityLog()
    }

    // === MASTER PRODUCT SYNC ===
    private val _productSyncStatus = MutableLiveData<ProductSyncStatus>()
    val productSyncStatus: LiveData<ProductSyncStatus> = _productSyncStatus

    data class ProductSyncStatus(
        val isSyncing: Boolean = false,
        val cachedCount: Int = 0,
        val message: String? = null,
        val isError: Boolean = false
    )

    /**
     * Set gudang code untuk filter scan
     * Dipanggil dari ScanActivity setelah mendapatkan DO detail
     * AUTO-SYNC master products untuk gudang ini
     */
    fun setGudangCode(gudangCode: String?) {
        android.util.Log.d("ScanViewModel", "🏭 setGudangCode CALLED with value: '${gudangCode ?: "NULL"}'")
        currentGudangCode = gudangCode
        android.util.Log.d("ScanViewModel", "🏭 currentGudangCode NOW: '${currentGudangCode ?: "NULL"}'")

        // Auto-sync master products untuk gudang ini
        if (!gudangCode.isNullOrEmpty()) {
            syncMasterProductsForGudang(gudangCode)
        }
    }

    /**
     * Sync master products untuk gudang tertentu
     * Dipanggil otomatis saat setGudangCode() atau manual dari UI
     */
    fun syncMasterProductsForGudang(gudangCode: String, forceSync: Boolean = false) {
        viewModelScope.launch {
            _productSyncStatus.value = ProductSyncStatus(isSyncing = true, message = "Menyinkronkan master produk...")

            when (val result = productSyncRepository.syncMasterProducts(gudangCode, forceSync)) {
                is ProductSyncRepository.SyncResult.Success -> {
                    android.util.Log.d("ScanViewModel", "✅ Product sync success: ${result.count} products for gudang $gudangCode")
                    _productSyncStatus.value = ProductSyncStatus(
                        isSyncing = false,
                        cachedCount = result.count,
                        message = "${result.count} produk di-cache untuk offline"
                    )
                }
                is ProductSyncRepository.SyncResult.AlreadySynced -> {
                    android.util.Log.d("ScanViewModel", "⏳ Products already synced: ${result.cachedCount} products")
                    _productSyncStatus.value = ProductSyncStatus(
                        isSyncing = false,
                        cachedCount = result.cachedCount,
                        message = "Cache sudah up-to-date (${result.cachedCount} produk)"
                    )
                }
                is ProductSyncRepository.SyncResult.Empty -> {
                    android.util.Log.d("ScanViewModel", "📭 No products to sync for gudang $gudangCode")
                    _productSyncStatus.value = ProductSyncStatus(
                        isSyncing = false,
                        cachedCount = 0,
                        message = "Tidak ada produk untuk gudang ini",
                        isError = true
                    )
                }
                is ProductSyncRepository.SyncResult.Error -> {
                    android.util.Log.e("ScanViewModel", "❌ Product sync error: ${result.message}")
                    // Try to get cached count anyway
                    val cachedCount = try {
                        productSyncRepository.getCachedCount(gudangCode)
                    } catch (e: Exception) { 0 }

                    _productSyncStatus.value = ProductSyncStatus(
                        isSyncing = false,
                        cachedCount = cachedCount,
                        message = if (cachedCount > 0) {
                            "Sync gagal, pakai cache ($cachedCount produk)"
                        } else {
                            "Sync gagal: ${result.message}"
                        },
                        isError = cachedCount == 0
                    )
                }
            }
        }
    }

    /**
     * Force sync master products (manual refresh dari UI)
     */
    fun forceSyncMasterProducts() {
        currentGudangCode?.let { syncMasterProductsForGudang(it, forceSync = true) }
    }

    fun loadHistory() {
        currentDoId?.let { id ->
            viewModelScope.launch {
                repository.getScanHistory(id).collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            _scanHistory.value = Resource.Success(resource.data?.items ?: emptyList())
                            // Update completion status dari API response
                            if (resource.data?.isCompleted == true) {
                                _isDoCompleted.value = true
                            }
                            // ★ Update totals untuk grand total display
                            _scanTotals.value = resource.data
                        }
                        is Resource.Error -> {
                            _scanHistory.value = Resource.Error(resource.message ?: "Error")
                        }
                        is Resource.Loading -> {
                            _scanHistory.value = Resource.Loading()
                        }
                    }
                }
            }
        }
    }

    fun loadActivityLog() {
        currentDoId?.let { id ->
            viewModelScope.launch {
                repository.getScanActivityLog(id).collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            _scanActivityLog.value = Resource.Success(resource.data?.items ?: emptyList())
                        }
                        is Resource.Error -> {
                            _scanActivityLog.value = Resource.Error(resource.message ?: "Error")
                        }
                        is Resource.Loading -> {
                            _scanActivityLog.value = Resource.Loading()
                        }
                    }
                }
            }
        }
    }

    fun refreshScanHistory() {
        loadHistory()
    }

    fun refreshActivityLog() {
        loadActivityLog()
    }

    fun checkBarcode(barcode: String) {
        android.util.Log.d("ScanViewModel", "🔍 checkBarcode called: barcode=$barcode, currentGudangCode=${currentGudangCode ?: "NULL"}")
        viewModelScope.launch {
            repository.checkBarcode(barcode, currentGudangCode).collect { resource ->
                _barcodeCheckResult.value = resource
            }
        }
    }

    /**
     * Search products untuk mode manual (barcode rusak)
     * Callback dengan hasil search
     * Filter berdasarkan gudang code jika tersedia
     */
    fun searchProducts(keyword: String, callback: (List<ScanItem.BarcodeProduct>?) -> Unit) {
        viewModelScope.launch {
            val products = repository.searchProducts(keyword, currentGudangCode)
            callback(products)
        }
    }

    fun submitScan(sku: String, qtyKarton: Int, qtyPcs: Int, barcode: String? = null, pcsPerKarton: Int = 1) {
        val doId = currentDoId ?: return
        viewModelScope.launch {
            repository.submitScan(
                doId = doId,
                sku = sku,
                qtyKarton = qtyKarton,
                qtyPcs = qtyPcs,
                isKarton = qtyKarton > 0, // true jika ada input karton
                barcode = barcode, // Optional: untuk mode manual
                pcsPerKarton = pcsPerKarton // Konversi: 1 karton = X pcs
            ).collect { resource ->
                _scanSubmitResult.value = resource
                if (resource is Resource.Success) {
                    // ✅ Update current DO status dari response
                    resource.data?.doStatus?.let { newStatus ->
                        _currentDoStatus.value = newStatus
                        android.util.Log.d("ScanViewModel", "✅ DO Status updated: $newStatus")
                    }
                    // Refresh kedua tab (Summary dan Activity Log)
                    loadHistory() // Refresh summary (GROUP BY SKU)
                    loadActivityLog() // Refresh activity log (setiap scan = 1 row)
                }
            }
        }
    }

    fun completeScan() {
        val doId = currentDoId ?: return
        viewModelScope.launch {
            repository.completeScan(doId).collect { resource ->
                _completeScanResult.value = resource
            }
        }
    }

    // ========== Staging Area Methods ==========

    /**
     * Check if current DO already has a staging assignment
     */
    fun checkStagingAssignment() {
        val doId = currentDoId ?: return
        viewModelScope.launch {
            stagingAreaRepository.getAssignment(doId).collect { resource ->
                _stagingAssignment.value = resource
                _isStagingSelected = resource is Resource.Success && resource.data != null
            }
        }
    }

    /**
     * Load available staging areas for branch
     */
    fun loadStagingAreas(branchCode: String) {
        viewModelScope.launch {
            stagingAreaRepository.getStagingAreas(branchCode).collect { resource ->
                _stagingAreas.value = resource
            }
        }
    }

    /**
     * Assign current DO to staging area
     */
    fun assignStagingArea(stagingAreaId: Int, userId: Int) {
        val doId = currentDoId ?: return
        viewModelScope.launch {
            stagingAreaRepository.assignDeliveryOrder(doId, stagingAreaId, userId).collect { resource ->
                _stagingAssignResult.value = resource
                if (resource is Resource.Success) {
                    _isStagingSelected = true
                    checkStagingAssignment() // Refresh assignment data
                }
            }
        }
    }

    fun setStagingSelected(selected: Boolean) {
        _isStagingSelected = selected
    }

    fun clearStagingAssignResult() {
        _stagingAssignResult.value = null!!
    }

    // === OFFLINE MODE METHODS ===

    // Sync status for UI feedback
    private val _syncStatus = MutableLiveData<SyncStatus>()
    val syncStatus: LiveData<SyncStatus> = _syncStatus

    data class SyncStatus(
        val isSyncing: Boolean = false,
        val message: String? = null,
        val isSuccess: Boolean = false
    )

    /**
     * Trigger manual sync of pending scans
     * 1. Refresh app status dari server dulu
     * 2. Lalu langsung sync (bukan WorkManager) agar dapat feedback real-time
     */
    fun triggerManualSync() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus(isSyncing = true, message = "Mengecek status server...")

            try {
                // Refresh status dari server dulu
                val versionCode = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionCode

                appStatusManager.checkAppStatus(versionCode)

                // Check if sync allowed now
                if (appStatusManager.isSyncAllowed()) {
                    _syncStatus.value = SyncStatus(isSyncing = true, message = "Menyinkronkan...")

                    // Direct sync untuk dapat hasil real-time
                    val result = repository.directSync()

                    result.fold(
                        onSuccess = { response ->
                            android.util.Log.d("ScanViewModel", "✅ Direct sync success: synced=${response.totalSynced}, failed=${response.totalFailed}")

                            val message = when {
                                response.totalSynced > 0 && response.totalFailed == 0 ->
                                    "Berhasil sync ${response.totalSynced} scan"
                                response.totalSynced > 0 && response.totalFailed > 0 ->
                                    "Sync: ${response.totalSynced} berhasil, ${response.totalFailed} gagal"
                                response.totalFailed > 0 ->
                                    "Gagal sync ${response.totalFailed} scan"
                                else ->
                                    "Tidak ada data untuk disync"
                            }

                            _syncStatus.value = SyncStatus(
                                isSyncing = false,
                                message = message,
                                isSuccess = response.totalFailed == 0
                            )
                        },
                        onFailure = { error ->
                            android.util.Log.e("ScanViewModel", "❌ Direct sync failed: ${error.message}")
                            _syncStatus.value = SyncStatus(
                                isSyncing = false,
                                message = "Sync gagal: ${error.message}",
                                isSuccess = false
                            )
                        }
                    )
                } else {
                    _syncStatus.value = SyncStatus(
                        isSyncing = false,
                        message = "Sync tidak diizinkan (maintenance/update required)",
                        isSuccess = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ScanViewModel", "Sync error: ${e.message}")
                _syncStatus.value = SyncStatus(
                    isSyncing = false,
                    message = "Error: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }
}