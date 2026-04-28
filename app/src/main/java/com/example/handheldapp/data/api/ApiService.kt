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

    // NEW: Get branches by company (BEFORE login) - untuk flow Company → Branch → Login
    @GET("branches-by-company")
    suspend fun getBranchesByCompany(@Query("com_code") companyCode: String): ApiResponse<List<Branch>>

    // DEPRECATED: Sesuaikan param dengan $request->query('usr_code') di Laravel Step 4
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
        @Query("date") date: String? = null,
        @Query("status") status: String = "active" // active=belum selesai, history=sudah selesai, all=semua
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
     * ✅ Returns SubmitScanResponse with do_status
     */
    @POST("scans/submit")
    suspend fun submitScan(
        @Body request: ScanRequest
    ): SubmitScanResponse

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

    /**
     * Bulk sync offline scans
     * POST /api/scans/bulk-sync
     */
    @POST("scans/bulk-sync")
    suspend fun bulkSyncScans(
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): retrofit2.Response<Map<String, Any>>
}

/**
 * App Version & Maintenance API Service
 */
interface AppVersionApiService {

    /**
     * Check app version and maintenance status
     * GET /api/app/version/check?version_code={code}&platform={android|ios}
     */
    @GET("app/version/check")
    suspend fun checkVersion(
        @Query("version_code") versionCode: Int,
        @Query("platform") platform: String = "android"
    ): retrofit2.Response<AppVersionResponse>

    /**
     * Get maintenance status only
     * GET /api/app/maintenance/status
     */
    @GET("app/maintenance/status")
    suspend fun getMaintenanceStatus(
        @Query("platform") platform: String = "android"
    ): retrofit2.Response<MaintenanceStatusResponse>
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
 * Updated: Using email for login (wdms_users table)
 * ★ SECURITY FIX: branch_code wajib dikirim untuk validasi di backend
 */
data class LoginRequest(
    @SerializedName("username")
    val email: String,  // Backend expect field name "username" (contains email value)
    val password: String,
    @SerializedName("company_code")
    val companyCode: String,  // ★ WAJIB: Company yang dipilih user
    @SerializedName("branch_code")
    val branchCode: String  // ★ WAJIB: Branch yang dipilih user
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
 * ★ UPDATED: Added scanned_at untuk tracking waktu scan yang akurat
 */
data class ScanRequest(
    @SerializedName("do_id") val doId: String,
    @SerializedName("sku") val sku: String,
    @SerializedName("barcode") val barcode: String? = null, // Optional: untuk mode manual (barcode rusak)
    @SerializedName("qty_karton") val qtyKarton: Int,
    @SerializedName("qty_pcs") val qtyPcs: Int,
    @SerializedName("is_karton") val isKarton: Boolean,
    @SerializedName("pcs_per_karton") val pcsPerKarton: Int = 1,  // Konversi: 1 karton = X pcs
    @SerializedName("rec_trans_id") val recTransId: String? = null, // Format: MAC|BARCODE-PCSxxxxxxxxxxxxx
    @SerializedName("scanned_at") val scannedAt: String? = null // ★ Timestamp scan dari device (ISO 8601)
)

/**
 * Base API Response
 */
/**
 * Submit Scan Response
 * ✅ Include do_status untuk real-time status update
 */
data class SubmitScanResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("do_status") val doStatus: String? = null, // ✅ Status DO terbaru
    @SerializedName("data") val data: Any? = null
)

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
 * ★ UPDATED: Added totals for grand total PCS
 */
data class ScanHistoryResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<ScanItem>?,
    @SerializedName("is_completed") val isCompleted: Boolean = false, // Status DO completed
    @SerializedName("totals") val totals: ScanTotals? = null, // ★ Total keseluruhan
    @SerializedName("info") val info: HistoryInfo? = null
)

/**
 * ★ Scan Totals for grand total display
 */
data class ScanTotals(
    @SerializedName("grand_total_pcs") val grandTotalPcs: Int = 0,
    @SerializedName("total_karton") val totalKarton: Int = 0,
    @SerializedName("total_pcs_only") val totalPcsOnly: Int = 0
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

// ============================================
// App Version & Maintenance Response Models
// ============================================

/**
 * App Version Check Response
 */
data class AppVersionResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: AppVersionData?
)

/**
 * App Version Data - Updated dengan status flags
 *
 * Status values:
 * - 'ok': Normal operation
 * - 'maintenance': Server maintenance
 * - 'update_required': MUST update (no "Nanti" button)
 * - 'update_available': Update available but optional (has "Nanti" button)
 */
data class AppVersionData(
    // Status: 'ok', 'maintenance', 'update_required', 'update_available'
    @SerializedName("status") val status: String?,

    // Version info
    @SerializedName("current_version") val currentVersion: Int?,
    @SerializedName("latest_version") val latestVersion: String?,
    @SerializedName("latest_version_code") val latestVersionCode: Int?,
    @SerializedName("min_version_code") val minVersionCode: Int?,

    // Active Maintenance info (sedang berlangsung)
    @SerializedName("is_maintenance") val isMaintenance: Boolean?,
    @SerializedName("maintenance_type") val maintenanceType: String?,  // minor/major/critical
    @SerializedName("maintenance_message") val maintenanceMessage: String?,
    @SerializedName("maintenance_start") val maintenanceStart: String?,
    @SerializedName("maintenance_end") val maintenanceEnd: String?,

    // Scheduled Maintenance info (dijadwalkan, belum dimulai)
    @SerializedName("has_scheduled_maintenance") val hasScheduledMaintenance: Boolean?,
    @SerializedName("scheduled_maintenance") val scheduledMaintenance: ScheduledMaintenanceData?,

    // Update info - IMPORTANT for update logic
    @SerializedName("must_update") val mustUpdate: Boolean?,           // WAJIB update, tidak ada tombol Nanti
    @SerializedName("update_available") val updateAvailable: Boolean?, // Ada update tapi opsional, ada tombol Nanti
    @SerializedName("force_update") val forceUpdate: Boolean?,         // Toggle force update dari admin
    @SerializedName("force_update_before_sync") val forceUpdateBeforeSync: Boolean?,
    @SerializedName("update_url") val updateUrl: String?,
    @SerializedName("release_notes") val releaseNotes: String?,

    // Behavior flags - PENTING!
    @SerializedName("allow_offline_input") val allowOfflineInput: Boolean?,
    @SerializedName("allow_offline_work") val allowOfflineWork: Boolean?,  // Untuk critical maintenance
    @SerializedName("allow_sync") val allowSync: Boolean?,
    @SerializedName("allow_offline_mode") val allowOfflineMode: Boolean? // Legacy
)

/**
 * Scheduled Maintenance Data - untuk pengumuman sebelum maintenance dimulai
 */
data class ScheduledMaintenanceData(
    @SerializedName("is_scheduled") val isScheduled: Boolean?,
    @SerializedName("maintenance_type") val maintenanceType: String?,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("start_formatted") val startFormatted: String?,  // "Senin, 26 Februari 2026 10:00"
    @SerializedName("end_formatted") val endFormatted: String?,      // "12:00"
    @SerializedName("duration") val duration: String?,               // "2 jam"
    @SerializedName("message") val message: String?,
    @SerializedName("time_until_start") val timeUntilStart: String?  // "dalam 5 jam"
)

/**
 * Maintenance Status Response
 */
data class MaintenanceStatusResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("is_maintenance") val isMaintenance: Boolean,
    @SerializedName("allow_offline_mode") val allowOfflineMode: Boolean,
    @SerializedName("maintenance_end") val maintenanceEnd: String?
)

/**
 * Bulk Sync Request Item
 */
data class BulkScanItem(
    @SerializedName("sku") val sku: String,
    @SerializedName("qty_karton") val qtyKarton: Int,
    @SerializedName("qty_pcs") val qtyPcs: Int,
    @SerializedName("scanned_at") val scannedAt: String
)

/**
 * Bulk Sync Response
 */
data class BulkSyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("synced_count") val syncedCount: Int? = 0,
    @SerializedName("tls_codes") val tlsCodes: List<String>? = null,
    @SerializedName("errors") val errors: List<BulkSyncError>? = null
)

/**
 * Bulk Sync Error
 */
data class BulkSyncError(
    @SerializedName("do_id") val doId: String,
    @SerializedName("error") val error: String
)

// ============================================
// Product API Service - untuk offline cache
// ============================================

/**
 * Product API Service - untuk sync master product ke local cache
 */
interface ProductApiService {

    /**
     * Get master products untuk offline cache
     * GET /api/products/master?gudang={gudangCode}
     */
    @GET("products/master")
    suspend fun getMasterProducts(
        @Query("gudang") gudangCode: String
    ): retrofit2.Response<MasterProductResponse>

    /**
     * Get products count per gudang
     * GET /api/products/count
     */
    @GET("products/count")
    suspend fun getProductsCount(): retrofit2.Response<ProductCountResponse>
}

/**
 * Master Product Response
 */
data class MasterProductResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("count") val count: Int?,
    @SerializedName("total_in_db") val totalInDb: Int?,
    @SerializedName("data") val data: List<MasterProductDto>?,
    @SerializedName("sync_time") val syncTime: Long?
)

/**
 * Master Product DTO
 */
data class MasterProductDto(
    @SerializedName("sku") val sku: String,
    @SerializedName("barcode") val barcode: String?,
    @SerializedName("product_name") val productName: String?,
    @SerializedName("pcs_per_karton") val pcsPerKarton: String?,  // String karena bisa berisi "5 KG / PC"
    @SerializedName("gudang_code") val gudangCode: String?
)

/**
 * Product Count Response
 */
data class ProductCountResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("total") val total: Int?,
    @SerializedName("per_gudang") val perGudang: List<GudangCount>?
)

/**
 * Gudang Count
 */
data class GudangCount(
    @SerializedName("gudang") val gudang: String,
    @SerializedName("count") val count: Int
)

/**
 * Notification API Service - untuk cek status approval dan notifikasi
 */
interface NotificationApiService {

    /**
     * Get recently approved/rejected DOs (for notification)
     * GET /api/notifications/approvals?branch={code}&company={code}&since={timestamp}
     */
    @GET("notifications/approvals")
    suspend fun getRecentlyApprovedDOs(
        @Query("branch") cabCode: String,
        @Query("company") companyCode: String,
        @Query("since") since: Long = 0
    ): retrofit2.Response<RecentlyApprovedResponse>

    /**
     * Get newly created DOs (for notification)
     * GET /api/notifications/new-dos?branch={code}&company={code}&since={timestamp}
     */
    @GET("notifications/new-dos")
    suspend fun getNewDeliveryOrders(
        @Query("branch") branchCode: String,
        @Query("company") companyCode: String,
        @Query("since") since: Long = 0
    ): retrofit2.Response<NewDOResponse>
}

/**
 * Recently Approved Response
 */
data class RecentlyApprovedResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<ApprovalNotificationDto>?
)

/**
 * Approval Notification DTO
 */
data class ApprovalNotificationDto(
    @SerializedName("do_number") val doNumber: String,
    @SerializedName("tallysheet_code") val tallysheetCode: String?,
    @SerializedName("status") val status: String,
    @SerializedName("approved_by") val approvedBy: String?,
    @SerializedName("approved_at") val approvedAt: String?,
    @SerializedName("rejection_notes") val rejectionNotes: String?,
    @SerializedName("supplier_name") val supplierName: String?
)

/**
 * New DO Response
 */
data class NewDOResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<NewDODto>?,
    @SerializedName("count") val count: Int = 0
)

/**
 * New DO DTO
 */
data class NewDODto(
    @SerializedName("id") val id: Long,
    @SerializedName("do_code") val doCode: String,
    @SerializedName("supplier_name") val supplierName: String?,
    @SerializedName("progress_status") val progressStatus: String?,
    @SerializedName("created_at") val createdAt: String?
)
