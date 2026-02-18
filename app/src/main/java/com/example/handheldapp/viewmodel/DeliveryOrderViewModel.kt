package com.example.handheldapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.repository.DeliveryOrderRepository
import com.example.handheldapp.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel untuk MainActivity
 * Tampilkan list Delivery Orders dengan filter dan sorting
 */
@HiltViewModel
class DeliveryOrderViewModel @Inject constructor(
    private val doRepository: DeliveryOrderRepository
) : ViewModel() {

    // List semua DOs
    private val _deliveryOrders = MutableLiveData<Resource<List<DeliveryOrder>>>()
    val deliveryOrders: LiveData<Resource<List<DeliveryOrder>>> = _deliveryOrders

    // Detail satu DO (untuk header ScanActivity)
    private val _deliveryOrderDetail = MutableLiveData<Resource<DeliveryOrder>>()
    val deliveryOrderDetail: LiveData<Resource<DeliveryOrder>> = _deliveryOrderDetail

    // Filter state
    private var currentBranchCode: String = ""
    private var currentCompanyCode: String = ""
    private var currentFilter: String = "Semua" // Semua, Berlangsung, Selesai
    private var currentDateFilter: String? = null // YYYY-MM-DD format
    private var allDeliveryOrders: List<DeliveryOrder> = emptyList()

    /**
     * Set date filter for DO list
     */
    fun setDateFilter(date: String?) {
        currentDateFilter = date
    }

    /**
     * Load semua DOs untuk cabang dan company yang login
     * Sorting: DESC (terbaru di atas)
     */
    fun loadDeliveryOrders(branchCode: String, companyCode: String = "") {
        currentBranchCode = branchCode
        currentCompanyCode = companyCode
        _deliveryOrders.value = Resource.Loading()

        viewModelScope.launch {
            doRepository.getDeliveryOrders(branchCode, companyCode, currentDateFilter).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        allDeliveryOrders = resource.data ?: emptyList()
                        applyFilter()
                    }
                    is Resource.Error -> {
                        _deliveryOrders.value = resource
                    }
                    is Resource.Loading -> {
                        _deliveryOrders.value = resource
                    }
                }
            }
        }
    }

    /**
     * Refresh data (untuk SwipeRefreshLayout)
     */
    fun refreshDeliveryOrders() {
        loadDeliveryOrders(currentBranchCode, currentCompanyCode)
    }

    /**
     * Filter DOs by status
     * - "Semua" = tampilkan semua
     * - "Berlangsung" = progress < 100%
     * - "Selesai" = progress = 100%
     */
    fun filterByStatus(filter: String) {
        currentFilter = filter
        applyFilter()
    }

    /**
     * Apply filter ke list
     */
    private fun applyFilter() {
        val filtered = when (currentFilter) {
            "Berlangsung" -> allDeliveryOrders.filter {
                val status = it.dohStatus?.lowercase()
                status !in listOf("selesai", "scan_completed", "completed", "approved_staff", "approved_manager", "approved")
            }
            "Selesai" -> allDeliveryOrders.filter {
                val status = it.dohStatus?.lowercase()
                status in listOf("selesai", "scan_completed", "completed", "approved_staff", "approved_manager", "approved")
            }
            else -> allDeliveryOrders
        }

        _deliveryOrders.value = Resource.Success(filtered)
    }

    /**
     * Load detail satu DO by ID
     * Untuk header di ScanActivity
     */
    fun loadDeliveryOrderDetail(doId: String) {
        viewModelScope.launch {
            doRepository.getDeliveryOrderById(doId).collect { resource ->
                _deliveryOrderDetail.value = resource
            }
        }
    }

    /**
     * Get current filter (untuk highlight chip yang aktif)
     */
    fun getCurrentFilter(): String = currentFilter
}
