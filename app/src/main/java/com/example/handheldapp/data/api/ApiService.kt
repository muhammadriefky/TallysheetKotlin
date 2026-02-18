package com.example.handheldapp.data.api

import com.example.handheldapp.data.model.Branch
import com.example.handheldapp.data.model.Company
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.data.model.ScanItem
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.data.model.StagingAreaAssignment
import com.example.handheldapp.data.model.User
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Auth API Service - Login flow
 */
interface AuthApiService {

    // Sesuaikan endpoint dengan Route::get('/companies', ...)
    @GET("companies")
    suspend fun getCompanies(): ApiResponse<List<Company>>

    // Sesuaikan param dengan $request->query('com_code')
    @GET("users")
    suspend fun getUsers(@Query("com_code") companyCode: String): ApiResponse<List<User>>

    // PERUBAHAN: Hilangkan 'auth/' jika di Route Laravel hanya Route::post('/login')
    @POST("login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<ApiLoginResponse>

    // Sesuaikan param dengan $request->query('usr_code') di Laravel Step 4
    @GET("branches")
    suspend fun getBranches(@Query("usr_code") usrCode: String): ApiResponse<List<Branch>>

    @POST("logout")
    suspend fun logout(): ApiResponse<Unit>
}

/**
 * Delivery Order API Service
 */
interface DeliveryOrderApiService {
    @GET("delivery-orders")
    suspend fun getDeliveryOrders(
        @Query("branch") branchCode: String,
        @Query("company") companyCode: String,
        @Query("date") date: String? = null
    ): ApiResponse<List<DeliveryOrder>>

    @GET("delivery-orders/{id}")
    suspend fun getDeliveryOrderById(@Path("id") id: String): ApiResponse<DeliveryOrder>

    // Sesuai Laravel: Route::get('{id}/scans', [DeliveryOrderController::class, 'getScans']);
    // URL: /api/delivery-orders/{id}/scans
    @GET("delivery-orders/{id}/scans")
    suspend fun getScansByDeliveryOrder(@Path("id") id: String): ApiResponse<List<ScanItem>>
}

/**
 * Scan API Service - Untuk barcode scanning & tally sheet
 */
interface ScanApiService {

    /**
     * Cek barcode di master produk (SKU_Barcode_pcs)
     * GET /api/scans/check/{barcode}?gudang={code}
     */
    @GET("scans/check/{barcode}")
    suspend fun checkBarcode(
        @Path("barcode") barcode: String,
        @Query("gudang") gudang: String  // Non-null, selalu dikirim (bisa empty string)
    ): BarcodeCheckResponse

    /**
     * Search products untuk mode manual (barcode rusak)
     * GET /api/scans/products/search?q={keyword}&gudang={code}
     */
    @GET("scans/products/search")
    suspend fun searchProducts(
        @Query("q") keyword: String,
        @Query("gudang") gudang: String  // Non-null, selalu dikirim (bisa empty string)
    ): BarcodeCheckResponse

    /**
     * Submit scan ke system
     * POST /api/scans/submit
     */
    @POST("scans/submit")
    suspend fun submitScan(
        @Body request: ScanRequest
    ): BaseApiResponse

    /**
     * Get scan history by DO ID (Ringkasan - GROUP BY SKU)
     * GET /api/scans/history?do_id={doId}
     */
    @GET("scans/history")
    suspend fun getScanHistory(
        @Query("do_id") doId: String
    ): ScanHistoryResponse

    /**
     * Get scan activity log (Riwayat lengkap semua scan)
     * GET /api/scans/activity-log?do_id={doId}
     */
    @GET("scans/activity-log")
    suspend fun getScanActivityLog(
        @Query("do_id") doId: String
    ): ScanHistoryResponse

    /**
     * Complete scan DO
     * POST /api/scans/{doId}/complete
     */
    @POST("scans/{doId}/complete")
    suspend fun completeScan(
        @Path("doId") doId: String
    ): BaseApiResponse
}

/**
 * Staging Area API Service
 */
interface StagingAreaApiService {

    /**
     * Get list of staging areas for branch
     * GET /api/staging-areas?branch_code=XXX
     */
    @GET("staging-areas")
    suspend fun getStagingAreas(
        @Query("branch_code") branchCode: String
    ): ApiResponse<List<StagingArea>>

    /**
     * Assign delivery order to staging area
     * POST /api/staging-areas/assign
     */
    @POST("staging-areas/assign")
    suspend fun assignDeliveryOrder(
        @Body request: StagingAssignmentRequest
    ): StagingAssignmentResponse

    /**
     * Get staging area assignment for delivery order
     * GET /api/staging-areas/assignment/{deliveryOrderId}
     */
    @GET("staging-areas/assignment/{deliveryOrderId}")
    suspend fun getAssignment(
        @Path("deliveryOrderId") deliveryOrderId: String
    ): ApiResponse<StagingAreaAssignment>
}

// ============================================
// API Response Models
// ============================================

/**
 * API Response wrapper
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)

/**
 * Login Request
 */
data class LoginRequest(
    val company_code: String,
    val username: String,
    val password: String
)

/**
 * API Login Response (from server)
 * Different from LoginResponse in data.model which includes branches
 */
data class ApiLoginResponse(
    val token: String,
    val user: User
)

// ============================================
// Scan API Request & Response Models
// ============================================

/**
 * Scan Request
 */
data class ScanRequest(
    @SerializedName("do_id") val doId: String,
    @SerializedName("sku") val sku: String,
    @SerializedName("barcode") val barcode: String? = null, // Optional: untuk mode manual (barcode rusak)
    @SerializedName("qty_karton") val qtyKarton: Int,
    @SerializedName("qty_pcs") val qtyPcs: Int,
    @SerializedName("is_karton") val isKarton: Boolean
)

/**
 * Base API Response
 */
data class BaseApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: Any? = null
)

/**
 * Barcode Check Response
 */
data class BarcodeCheckResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("data") val data: List<ScanItem.BarcodeProduct>?
)

/**
 * Scan History Response
 */
data class ScanHistoryResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<ScanItem>?,
    @SerializedName("is_completed") val isCompleted: Boolean = false, // Status DO completed
    @SerializedName("info") val info: HistoryInfo? = null
)

/**
 * History Info
 */
data class HistoryInfo(
    @SerializedName("no_do") val noDo: String,
    @SerializedName("count") val count: Int,
    @SerializedName("status") val status: String? = null
)

// ============================================
// Staging Area API Request & Response Models
// ============================================

/**
 * Staging Assignment Request
 */
data class StagingAssignmentRequest(
    @SerializedName("delivery_order_id") val deliveryOrderId: String,
    @SerializedName("staging_area_id") val stagingAreaId: Int,
    @SerializedName("user_id") val userId: Int
)

/**
 * Staging Assignment Response
 */
data class StagingAssignmentResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: StagingAssignmentData?
)

/**
 * Staging Assignment Data
 */
data class StagingAssignmentData(
    @SerializedName("assignment_id") val assignmentId: Int,
    @SerializedName("staging_area_code") val stagingAreaCode: String,
    @SerializedName("staging_area_name") val stagingAreaName: String
)

