package com.example.handheldapp.repository

import com.example.handheldapp.data.api.DeliveryOrderApiService
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.data.model.ScanItem
import com.example.handheldapp.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeliveryOrderRepository @Inject constructor(
    private val doApi: DeliveryOrderApiService
) {

    /**
     * Ambil semua Delivery Orders dengan filter branch, company, dan date
     */
    fun getDeliveryOrders(branchCode: String, companyCode: String, date: String? = null): Flow<Resource<List<DeliveryOrder>>> = flow {
        emit(Resource.Loading())
        try {
            val response = doApi.getDeliveryOrders(branchCode, companyCode, date)

            if (response.success && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "Gagal mengambil data Delivery Order"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Terjadi kesalahan jaringan"))
        }
    }

    /**
     * Ambil detail satu Delivery Order by ID
     */
    fun getDeliveryOrderById(doId: String): Flow<Resource<DeliveryOrder>> = flow {
        emit(Resource.Loading())
        try {
            // PERBAIKAN: Interface API kamu menggunakan @Path("id") id: String
            // Jadi tidak perlu dikonversi ke Int jika di interface sudah String
            val response = doApi.getDeliveryOrderById(doId)

            if (response.success && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "Gagal mengambil detail DO"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Terjadi kesalahan jaringan"))
        }
    }

    /**
     * Ambil semua scan items untuk satu DO
     */
    fun getScansByDeliveryOrder(doId: String): Flow<Resource<List<ScanItem>>> = flow {
        emit(Resource.Loading())
        try {
            // PERBAIKAN: Nama fungsi di interface kamu adalah 'getScansByDeliveryOrder'
            // dan parameternya adalah String id
            val response = doApi.getScansByDeliveryOrder(doId)

            if (response.success && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "Gagal mengambil riwayat scan"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Terjadi kesalahan jaringan"))
        }
    }
}