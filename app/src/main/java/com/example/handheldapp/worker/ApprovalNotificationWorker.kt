package com.example.handheldapp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.handheldapp.R
import com.example.handheldapp.data.api.ApprovalNotificationDto
import com.example.handheldapp.data.api.NotificationApiService
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.local.SettingsManager
import com.example.handheldapp.ui.TallysheetHistoryActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background worker untuk mengecek status approval DO
 * Worker ini akan berjalan secara periodik dan mengirim notifikasi
 * ketika ada DO yang status approvalnya berubah
 */
@HiltWorker
class ApprovalNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationApiService: NotificationApiService,
    private val sessionManager: SessionManager,
    private val settingsManager: SettingsManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ApprovalNotifWorker"
        private const val WORK_NAME = "approval_notification_work"
        private const val CHANNEL_ID = "approval_notifications"
        private const val CHANNEL_NAME = "Status Approval DO"
        private const val NOTIFICATION_ID_BASE = 1000

        /**
         * Schedule periodic work untuk mengecek approval status
         */
        fun schedule(context: Context, intervalMinutes: Long = 15) {
            val workRequest = PeriodicWorkRequestBuilder<ApprovalNotificationWorker>(
                intervalMinutes, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d(TAG, "Scheduled approval check every $intervalMinutes minutes")
        }

        /**
         * Cancel scheduled work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled approval check work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting approval status check...")

        // Check if notification is enabled
        val isEnabled = settingsManager.isApprovalNotificationEnabled().first()
        if (!isEnabled) {
            Log.d(TAG, "Approval notification is disabled, skipping...")
            return Result.success()
        }

        return try {
            // Get user session info
            val branchCode = sessionManager.getBranchCode().first() ?: return Result.success()
            val companyCode = sessionManager.getCompanyCode().first() ?: return Result.success()
            val lastCheck = settingsManager.getLastApprovalCheck().first()

            // Call API to get recently approved DOs
            val response = notificationApiService.getRecentlyApprovedDOs(
                cabCode = branchCode,
                companyCode = companyCode,
                since = lastCheck
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val approvedDOs = response.body()?.data ?: emptyList()

                if (approvedDOs.isNotEmpty()) {
                    Log.d(TAG, "Found ${approvedDOs.size} newly approved DOs")

                    // Send notifications for each approved DO
                    approvedDOs.forEachIndexed { index, approvalInfo ->
                        sendApprovalNotification(
                            notificationId = NOTIFICATION_ID_BASE + index,
                            approvalInfo = approvalInfo
                        )
                    }
                }

                // Update last check timestamp
                settingsManager.setLastApprovalCheck()
            }

            Log.d(TAG, "Approval check completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking approval status: ${e.message}")
            Result.retry()
        }
    }

    private fun sendApprovalNotification(
        notificationId: Int,
        approvalInfo: ApprovalNotificationDto
    ) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi status approval Delivery Order"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open TallysheetHistoryActivity
        val intent = Intent(applicationContext, TallysheetHistoryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification content based on status
        val (title, icon, color) = when (approvalInfo.status.lowercase()) {
            "approved", "approved_staff", "approved_manager" -> {
                Triple(
                    "✅ DO Disetujui",
                    R.drawable.ic_verified,
                    androidx.core.content.ContextCompat.getColor(applicationContext, R.color.success)
                )
            }
            "rejected" -> {
                Triple(
                    "❌ DO Ditolak",
                    R.drawable.ic_warning,
                    androidx.core.content.ContextCompat.getColor(applicationContext, R.color.error)
                )
            }
            else -> {
                Triple(
                    "📋 Status DO Berubah",
                    R.drawable.ic_package,
                    androidx.core.content.ContextCompat.getColor(applicationContext, R.color.info)
                )
            }
        }

        val contentText = buildString {
            append("DO: ${approvalInfo.doNumber}")
            approvalInfo.supplierName?.let { append("\nSupplier: $it") }
            approvalInfo.approvedBy?.let { append("\nOleh: $it") }
            approvalInfo.approvedAt?.let { append("\nPada: $it") }
            // Show rejection notes if rejected
            if (approvalInfo.status.lowercase() == "rejected" && !approvalInfo.rejectionNotes.isNullOrBlank()) {
                append("\nAlasan: ${approvalInfo.rejectionNotes}")
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText("DO: ${approvalInfo.doNumber}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setColor(color)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Sent notification for DO: ${approvalInfo.doNumber}, status: ${approvalInfo.status}")
    }
}
