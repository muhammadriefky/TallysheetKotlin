package com.example.handheldapp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.handheldapp.R
import com.example.handheldapp.data.api.NewDODto
import com.example.handheldapp.data.api.NotificationApiService
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.local.SettingsManager
import com.example.handheldapp.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background worker untuk mengecek DO baru dari server
 * Worker ini akan berjalan secara periodik dan mengirim notifikasi
 * ketika ada DO baru yang tersedia untuk di-scan
 */
@HiltWorker
class NewDONotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationApiService: NotificationApiService,
    private val sessionManager: SessionManager,
    private val settingsManager: SettingsManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "NewDONotifWorker"
        private const val WORK_NAME = "new_do_notification_work"
        private const val CHANNEL_ID = "new_do_notifications"
        private const val CHANNEL_NAME = "DO Baru"
        private const val NOTIFICATION_ID = 2000

        /**
         * Schedule periodic work untuk mengecek DO baru
         */
        fun schedule(context: Context, intervalMinutes: Long = 15) {
            val workRequest = PeriodicWorkRequestBuilder<NewDONotificationWorker>(
                intervalMinutes, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d(TAG, "Scheduled new DO check every $intervalMinutes minutes")
        }

        /**
         * Cancel scheduled work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled new DO check work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting new DO check...")

        // Check if notification is enabled
        val isEnabled = settingsManager.isDoStatusNotificationEnabled().first()
        if (!isEnabled) {
            Log.d(TAG, "New DO notification is disabled, skipping...")
            return Result.success()
        }

        return try {
            // Get user session info
            val branchCode = sessionManager.getBranchCode().first() ?: return Result.success()
            val companyCode = sessionManager.getCompanyCode().first() ?: return Result.success()
            val lastCheck = settingsManager.getLastNewDOCheck().first()

            Log.d(TAG, "Checking new DOs since: $lastCheck")

            // Call API to get new DOs
            val response = notificationApiService.getNewDeliveryOrders(
                branchCode = branchCode,
                companyCode = companyCode,
                since = lastCheck
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val newDOs = response.body()?.data ?: emptyList()

                if (newDOs.isNotEmpty()) {
                    Log.d(TAG, "Found ${newDOs.size} new DOs")

                    // Send notification
                    sendNewDONotification(newDOs.size, newDOs.firstOrNull())
                }

                // Update last check timestamp
                settingsManager.setLastNewDOCheck()
            } else {
                Log.e(TAG, "API error: ${response.body()?.message}")
            }

            Log.d(TAG, "New DO check completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking new DOs: ${e.message}")
            Result.retry()
        }
    }

    private fun sendNewDONotification(count: Int, firstDO: NewDODto?) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi Delivery Order baru"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open MainActivity
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "delivery_orders")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification content
        val title = if (count == 1) "📦 DO Baru Tersedia" else "📦 $count DO Baru Tersedia"
        val contentText = if (count == 1 && firstDO != null) {
            "DO ${firstDO.doCode} dari ${firstDO.supplierName ?: "supplier"} siap untuk di-scan"
        } else {
            "$count Delivery Order baru siap untuk di-scan"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_package)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setColor(ContextCompat.getColor(applicationContext, R.color.primary))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Sent notification for $count new DOs")
    }
}
