package com.example.handheldapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.repository.StagingAreaRepository
import com.example.handheldapp.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StagingAreaViewModel @Inject constructor(
    private val repository: StagingAreaRepository
) : ViewModel() {

    private val _stagingAreas = MutableLiveData<Resource<List<StagingArea>>>()
    val stagingAreas: LiveData<Resource<List<StagingArea>>> = _stagingAreas

    private val _assignmentResult = MutableLiveData<Resource<String>?>()
    val assignmentResult: LiveData<Resource<String>?> = _assignmentResult

    private var currentBranchCode: String = ""

    fun loadStagingAreas(branchCode: String) {
        currentBranchCode = branchCode
        _stagingAreas.value = Resource.Loading()

        viewModelScope.launch {
            repository.getStagingAreas(branchCode).collect { resource ->
                _stagingAreas.value = resource
            }
        }
    }

    fun refreshStagingAreas() {
        if (currentBranchCode.isNotEmpty()) {
            loadStagingAreas(currentBranchCode)
        }
    }

    fun assignDeliveryOrder(deliveryOrderId: String, stagingAreaId: Int, userId: Int) {
        _assignmentResult.value = Resource.Loading()

        viewModelScope.launch {
            repository.assignDeliveryOrder(deliveryOrderId, stagingAreaId, userId).collect { resource ->
                _assignmentResult.value = resource
            }
        }
    }

    fun clearAssignmentResult() {
        _assignmentResult.value = null
    }
}
