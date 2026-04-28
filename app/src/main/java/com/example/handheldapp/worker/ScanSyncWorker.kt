package com.example.handheldapp.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.handheldapp.data.local.dao.PendingScanDao
import com.example.handheldapp.data.local.entity.PendingScan
import com.example.handheldapp.data.api.ScanApiService
import com.example.handheldapp.utils.AppStatus
import com.example.handheldapp.utils.AppStatusManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker untuk sync pending scans ke server
 * Akan otomatis jalan saat device kembali online
 *
 * PENTING: Worker akan BLOCK sync jika:
 * - Server dalam maintenance mode
 * - App perlu update sebelum sync
 */
@HiltWorker
class ScanSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingScanDao: PendingScanDao,
    private val scanApiService: ScanApiService,
    private val appStatusManager: AppStatusManager
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "scan_sync_worker"
        const val WORK_NAME_PERIODIC = "scan_sync_worker_periodic"
        private const val TAG = "ScanSyncWorker"

        /**
         * Create one-time work request untuk immediate sync
         */
        fun createOneTimeWorkRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<ScanSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .addTag("sync")
                .build()
        }

        /**
         * Create periodic work request untuk background sync
         * Sync setiap 15 menit saat online
         */
        fun createPeriodicWorkRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<ScanSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .addTag("sync_periodic")
                .build()
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== SYNC WORKER STARTED ===")

        // CHECK APP STATUS DULU
        val versionCode = getVersionCode()
        appStatusManager.checkAppStatus(versionCode)

        // Block sync jika tidak diizinkan
        if (!appStatusManager.isSyncAllowed()) {
            val status = appStatusManager.getCurrentStatus()
            Log.w(TAG, "⛔ Sync BLOCKED - App status: $status")

            return@withContext when (status) {
                AppStatus.MAINTENANCE -> {
                    Log.d(TAG, "🔧 Server dalam maintenance, retry later")
                    Result.retry() // Retry nanti saat maintenance selesai
                }
                AppStatus.UPDATE_REQUIRED -> {
                    Log.d(TAG, "📲 Update required before sync")
                    // Return success tapi data tidak di-sync
                    // User harus update app dulu
                    Result.success(
                        workDataOf(
                            "blocked" to true,
                            "reason" to "update_required"
                        )
                    )
                }
                else -> Result.retry()
            }
        }

        // Lanjut sync seperti biasa
        try {
            val pendingScans = pendingScanDao.getPendingScans()

            if (pendingScans.isEmpty()) {
                Log.d(TAG, "No pending scans to sync")
                return@withContext Result.success()
            }

            Log.d(TAG, "Found ${pendingScans.size} pending scans to sync")

            // Group by DO untuk batch processing
            val scansByDo = pendingScans.groupBy { it.doId }

            // Prepare payload untuk bulk sync
            val scansPayload = pendingScans.map { scan ->
                mapOf(
                    "do_id" to scan.doId,
                    "sku" to scan.sku,
                    "barcode" to scan.barcode,
                    "qty_karton" to scan.qtyKarton,
                    "qty_pcs" to scan.qtyPcs,
                    "pcs_per_karton" to scan.pcsPerKarton,
                    "rec_trans_id" to scan.recTransId, // ★ Transaction ID dari device (MAC address)
                    "scanned_at" to dateFormat.format(Date(scan.scannedAt))
                )
            }

            Log.d(TAG, "Sending bulk sync request with ${scansPayload.size} scans")

            // Call bulk sync API
            val response = scanApiService.bulkSyncScans(
                mapOf("scans" to scansPayload)
            )

            if (response.isSuccessful) {
                val body = response.body()

                if (body?.get("success") == true) {
                    val syncedCount = (body["synced_count"] as? Number)?.toInt() ?: 0
                    Log.d(TAG, "Bulk sync success: $syncedCount scans synced")

                    // Mark all as synced
                    pendingScans.forEach { scan ->
                        pendingScanDao.markAsSynced(scan.id)
                    }

                    // Clean up synced records
                    pendingScanDao.deleteSyncedRecords()

                    // Log any errors from server
                    val errors = body["errors"] as? List<*>
                    errors?.forEach { error ->
                        Log.w(TAG, "Server error: $error")
                    }

                    return@withContext Result.success(
                        workDataOf("synced_count" to syncedCount)
                    )
                } else {
                    val message = body?.get("message")?.toString() ?: "Unknown error"
                    Log.e(TAG, "Bulk sync failed: $message")

                    // Reset to pending for retry
                    pendingScanDao.resetSyncingToPending()

                    return@withContext Result.retry()
                }
            } else {
                Log.e(TAG, "API call failed: ${response.code()} - ${response.message()}")
                pendingScanDao.resetSyncingToPending()
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Sync worker error: ${e.message}", e)

            // Reset syncing status ke pending
            try {
                pendingScanDao.resetSyncingToPending()
            } catch (dbError: Exception) {
                Log.e(TAG, "Failed to reset sync status: ${dbError.message}")
            }

            return@withContext Result.retry()
        }
    }

    /**
     * Get current app version code
     */
    private fun getVersionCode(): Int {
        return try {
            val pInfo = applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get version code: ${e.message}")
            1
        }
    }
}