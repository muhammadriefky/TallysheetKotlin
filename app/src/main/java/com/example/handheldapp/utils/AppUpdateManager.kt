package com.example.handheldapp.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages APK download and installation for app updates.
 * Uses DownloadManager for reliable background downloads with progress tracking.
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private var currentDownloadId: Long = -1

    companion object {
        private const val APK_FILE_NAME = "warehouse_app_update.apk"
        private const val DOWNLOAD_POLL_INTERVAL = 500L // ms
    }

    /**
     * Data class representing download progress
     */
    data class DownloadProgress(
        val status: DownloadStatus,
        val progress: Int = 0, // 0-100
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val errorMessage: String? = null
    )

    enum class DownloadStatus {
        PENDING,
        RUNNING,
        PAUSED,
        SUCCESSFUL,
        FAILED,
        CANCELLED
    }

    /**
     * Starts downloading the APK from the given URL.
     * Returns a Flow that emits download progress updates.
     */
    fun downloadApk(downloadUrl: String, appVersion: String): Flow<DownloadProgress> = flow {
        // Cancel any existing download
        if (currentDownloadId != -1L) {
            downloadManager.remove(currentDownloadId)
        }

        // Clean up old APK files
        cleanupOldApkFiles()

        // Create download request
        val fileName = "warehouse_app_v${appVersion}.apk"
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Update Warehouse App")
            .setDescription("Downloading v$appVersion...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        // Add headers if needed (e.g., auth token)
        // request.addRequestHeader("Authorization", "Bearer $token")

        // Start download
        currentDownloadId = downloadManager.enqueue(request)

        emit(DownloadProgress(DownloadStatus.PENDING))

        // Poll for progress
        var isDownloading = true
        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(currentDownloadId)
            val cursor: Cursor? = downloadManager.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else DownloadManager.STATUS_FAILED
                val bytesDownloaded = if (bytesDownloadedIndex >= 0) cursor.getLong(bytesDownloadedIndex) else 0L
                val bytesTotal = if (bytesTotalIndex >= 0) cursor.getLong(bytesTotalIndex) else 0L
                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else 0

                val progress = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt() else 0

                when (status) {
                    DownloadManager.STATUS_PENDING -> {
                        emit(DownloadProgress(DownloadStatus.PENDING, progress, bytesDownloaded, bytesTotal))
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        emit(DownloadProgress(DownloadStatus.RUNNING, progress, bytesDownloaded, bytesTotal))
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        emit(DownloadProgress(DownloadStatus.PAUSED, progress, bytesDownloaded, bytesTotal))
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        emit(DownloadProgress(DownloadStatus.SUCCESSFUL, 100, bytesTotal, bytesTotal))
                        isDownloading = false
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val errorMsg = getErrorMessage(reason)
                        emit(DownloadProgress(DownloadStatus.FAILED, progress, bytesDownloaded, bytesTotal, errorMsg))
                        isDownloading = false
                    }
                }
                cursor.close()
            } else {
                emit(DownloadProgress(DownloadStatus.FAILED, errorMessage = "Download tidak ditemukan"))
                isDownloading = false
            }

            if (isDownloading) {
                delay(DOWNLOAD_POLL_INTERVAL)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Installs the downloaded APK using FileProvider
     */
    fun installApk(appVersion: String): Boolean {
        val fileName = "warehouse_app_v${appVersion}.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (!file.exists()) {
            return false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Gets the downloaded APK file if it exists
     */
    fun getDownloadedApkFile(appVersion: String): File? {
        val fileName = "warehouse_app_v${appVersion}.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        return if (file.exists()) file else null
    }

    /**
     * Cancels the current download
     */
    fun cancelDownload() {
        if (currentDownloadId != -1L) {
            downloadManager.remove(currentDownloadId)
            currentDownloadId = -1
        }
    }

    /**
     * Cleans up old APK files from downloads folder
     */
    private fun cleanupOldApkFiles() {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir?.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach {
            it.delete()
        }
    }

    /**
     * Maps DownloadManager error codes to human-readable messages
     */
    private fun getErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Download tidak dapat dilanjutkan"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage tidak ditemukan"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File sudah ada"
            DownloadManager.ERROR_FILE_ERROR -> "Error pada file"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Error data HTTP"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Storage tidak cukup"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Terlalu banyak redirect"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP error tidak dikenal"
            DownloadManager.ERROR_UNKNOWN -> "Error tidak diketahui"
            else -> "Download gagal (kode: $reason)"
        }
    }

    /**
     * Formats bytes to human-readable string (e.g., "12.5 MB")
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
