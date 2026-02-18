package com.example.handheldapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.handheldapp.data.model.ScanItem
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.data.model.StagingAreaAssignment
import com.example.handheldapp.repository.ScanRepository
import com.example.handheldapp.repository.StagingAreaRepository
import com.example.handheldapp.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val stagingAreaRepository: StagingAreaRepository
) : ViewModel() {

    private val _scanHistory = MutableLiveData<Resource<List<ScanItem>>>()
    val scanHistory: LiveData<Resource<List<ScanItem>>> = _scanHistory

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

    // Staging Area LiveData
    private val _stagingAreas = MutableLiveData<Resource<List<StagingArea>>>()
    val stagingAreas: LiveData<Resource<List<StagingArea>>> = _stagingAreas

    private val _stagingAssignment = MutableLiveData<Resource<StagingAreaAssignment>>()
    val stagingAssignment: LiveData<Resource<StagingAreaAssignment>> = _stagingAssignment

    private val _stagingAssignResult = MutableLiveData<Resource<String>>()
    val stagingAssignResult: LiveData<Resource<String>> = _stagingAssignResult

    private var currentDoId: String? = null
    private var currentGudangCode: String? = null // Kode gudang untuk filter scan
    private var _isStagingSelected = false
    val isStagingSelected: Boolean get() = _isStagingSelected

    fun setDeliveryOrderId(id: String) {
        currentDoId = id
        loadHistory()
        loadActivityLog()
    }

    /**
     * Set gudang code untuk filter scan
     * Dipanggil dari ScanActivity setelah mendapatkan DO detail
     */
    fun setGudangCode(gudangCode: String?) {
        android.util.Log.d("ScanViewModel", "🏭 setGudangCode CALLED with value: '${gudangCode ?: "NULL"}'")
        currentGudangCode = gudangCode
        android.util.Log.d("ScanViewModel", "🏭 currentGudangCode NOW: '${currentGudangCode ?: "NULL"}'")
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

    fun submitScan(sku: String, qtyKarton: Int, qtyPcs: Int, barcode: String? = null) {
        val doId = currentDoId ?: return
        viewModelScope.launch {
            repository.submitScan(
                doId = doId,
                sku = sku,
                qtyKarton = qtyKarton,
                qtyPcs = qtyPcs,
                isKarton = qtyKarton > 0, // true jika ada input karton
                barcode = barcode // Optional: untuk mode manual
            ).collect { resource ->
                _scanSubmitResult.value = resource
                if (resource is Resource.Success) {
                    loadHistory() // Refresh summary
                    loadActivityLog() // Refresh activity log
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
}