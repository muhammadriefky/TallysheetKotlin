package com.example.handheldapp.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.handheldapp.repository.ScanRepository
import com.example.handheldapp.utils.AppStatusManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

/**
 * Background worker untuk auto-sync pending scans setelah maintenance selesai
 *
 * Trigger conditions:
 * 1. App status berubah dari MAINTENANCE ke OK
 * 2. Periodic check setiap 15 menit (jika ada pending data)
 * 3. Manual trigger dari UI (force sync button)
 *
 * Behavior:
 * - Check app status dulu sebelum sync
 * - Jika allow_sync = true, lakukan bulk sync
 * - Jika allow_sync = false, skip dan retry later
 * - Cleanup data lokal yang sudah berhasil di-sync
 */
@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanRepository: ScanRepository,
    private val appStatusManager: AppStatusManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoSyncWorker"
        const val WORK_NAME = "auto_sync_pending_scans"

        /**
         * Schedule periodic sync worker
         * Runs every 15 minutes untuk check pending data
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Hanya run jika ada internet
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<AutoSyncWorker>(
                15, TimeUnit.MINUTES // Check setiap 15 menit
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP, // Jangan replace jika sudah ada
                    periodicWork
                )

            Log.d(TAG, "✅ Periodic auto-sync worker scheduled (every 15 minutes)")
        }

        /**
         * Trigger one-time sync immediately
         * Digunakan saat maintenance selesai atau user tap force sync
         */
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeWork = OneTimeWorkRequestBuilder<AutoSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_immediate",
                    ExistingWorkPolicy.REPLACE,
                    oneTimeWork
                )

            Log.d(TAG, "🔄 Immediate sync triggered")
        }

        /**
         * Cancel all auto-sync workers
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "❌ Auto-sync worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "🔄 AutoSyncWorker started")

        return try {
            // Step 1: Check app status
            val isSyncAllowed = appStatusManager.isSyncAllowed()

            if (!isSyncAllowed) {
                Log.w(TAG, "⚠️ Sync not allowed (maintenance or update required)")
                Log.w(TAG, "   Status: ${appStatusManager.getCurrentStatus()}")
                return Result.retry() // Retry later
            }

            // Step 2: Check ada pending scans atau tidak
            val pendingCount = scanRepository.getPendingScanCount().firstOrNull() ?: 0

            if (pendingCount == 0) {
                Log.d(TAG, "✅ No pending scans to sync")
                return Result.success()
            }

            Log.d(TAG, "📦 Found $pendingCount pending scans")

            // Step 3: Get pending scans
            val pendingScans = scanRepository.getPendingScans().firstOrNull() ?: emptyList()

            if (pendingScans.isEmpty()) {
                Log.d(TAG, "✅ No pending scans (race condition check)")
                return Result.success()
            }

            Log.d(TAG, "🚀 Starting bulk sync for ${pendingScans.size} scans")

            // Step 4: Bulk sync
            val result = scanRepository.bulkSyncPendingScans(pendingScans)

            result.fold(
                onSuccess = { response ->
                    Log.d(TAG, "✅ Bulk sync SUCCESS")
                    Log.d(TAG, "   Synced: ${response.totalSynced}")
                    Log.d(TAG, "   Failed: ${response.totalFailed}")

                    // Success output data for notification
                    val outputData = workDataOf(
                        "success" to true,
                        "total_synced" to response.totalSynced,
                        "total_failed" to response.totalFailed
                    )

                    Result.success(outputData)
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Bulk sync FAILED: ${error.message}")

                    // Retry dengan backoff
                    Result.retry()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ AutoSyncWorker error: ${e.message}", e)
            Result.retry()
        }
    }
}
