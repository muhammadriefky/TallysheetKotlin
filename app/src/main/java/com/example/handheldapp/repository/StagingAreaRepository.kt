package com.example.handheldapp.repository

import com.example.handheldapp.data.api.StagingAreaApiService
import com.example.handheldapp.data.api.StagingAssignmentRequest
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.data.model.StagingAreaAssignment
import com.example.handheldapp.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StagingAreaRepository @Inject constructor(
    private val stagingAreaApi: StagingAreaApiService
) {

    fun getStagingAreas(branchCode: String): Flow<Resource<List<StagingArea>>> = flow {
        try {
            emit(Resource.Loading())
            val response = stagingAreaApi.getStagingAreas(branchCode)

            if (response.success && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "Failed to fetch staging areas"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)

    fun assignDeliveryOrder(
        deliveryOrderId: String,
        stagingAreaId: Int,
        userId: Int
    ): Flow<Resource<String>> = flow {
        try {
            emit(Resource.Loading())
            val request = StagingAssignmentRequest(deliveryOrderId, stagingAreaId, userId)
            val response = stagingAreaApi.assignDeliveryOrder(request)

            if (response.success && response.data != null) {
                val message = "${response.data.stagingAreaCode} - ${response.data.stagingAreaName}"
                emit(Resource.Success(message))
            } else {
                emit(Resource.Error(response.message ?: "Failed to assign delivery order"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)

    fun getAssignment(deliveryOrderId: String): Flow<Resource<StagingAreaAssignment>> = flow {
        try {
            emit(Resource.Loading())
            val response = stagingAreaApi.getAssignment(deliveryOrderId)

            if (response.success && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "No assignment found"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)
}
