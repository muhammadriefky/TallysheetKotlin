package com.example.handheldapp.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.handheldapp.R
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.local.SettingsManager
import com.example.handheldapp.databinding.ActivitySettingBinding
import com.example.handheldapp.repository.ProductSyncRepository
import com.example.handheldapp.ui.base.BaseActivity
import com.example.handheldapp.utils.AppStatusManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingBinding

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var appStatusManager: AppStatusManager

    @Inject
    lateinit var productSyncRepository: ProductSyncRepository

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadCurrentSettings()
        setupClickListeners()
        loadAppInfo()
        calculateCacheSize()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            // Load Theme
            val themeMode = settingsManager.getThemeMode().first()
            binding.tvThemeValue.text = getThemeDisplayText(themeMode)

            // Load Font Size
            val fontSize = settingsManager.getFontSize().first()
            binding.tvFontSizeValue.text = getFontSizeDisplayText(fontSize)

            // Load Notification Settings
            val approvalNotif = settingsManager.isApprovalNotificationEnabled().first()
            binding.switchApprovalNotif.isChecked = approvalNotif

            val doStatusNotif = settingsManager.isDoStatusNotificationEnabled().first()
            binding.switchDoStatusNotif.isChecked = doStatusNotif

            // Load Check Interval
            val checkInterval = settingsManager.getNotificationCheckInterval().first()
            binding.tvCheckIntervalValue.text = "Setiap $checkInterval menit"

            // Load Last Sync Time
            val lastSync = settingsManager.getLastSyncTime().first()
            binding.tvLastSync.text = if (lastSync > 0) {
                dateFormat.format(Date(lastSync))
            } else {
                "Belum pernah"
            }

            // Load Sync Settings
            val syncWifiOnly = settingsManager.isSyncOnWifiOnly().first()
            binding.switchSyncWifiOnly.isChecked = syncWifiOnly

            val autoSync = settingsManager.isAutoSyncEnabled().first()
            binding.switchAutoSync.isChecked = autoSync

            val autoSyncInterval = settingsManager.getAutoSyncInterval().first()
            binding.tvAutoSyncIntervalValue.text = "Setiap $autoSyncInterval menit"

            // Load Performance Settings
            val lowDataMode = settingsManager.isLowDataModeEnabled().first()
            binding.switchLowDataMode.isChecked = lowDataMode

            val vibrationEnabled = settingsManager.isVibrationEnabled().first()
            binding.switchVibration.isChecked = vibrationEnabled

            val soundEnabled = settingsManager.isSoundEnabled().first()
            binding.switchSound.isChecked = soundEnabled
        }
    }

    private fun setupClickListeners() {
        // Theme Selection
        binding.layoutTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        // Font Size Selection
        binding.layoutFontSize.setOnClickListener {
            showFontSizeSelectionDialog()
        }

        // Notification Switches
        binding.switchApprovalNotif.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsManager.setApprovalNotificationEnabled(isChecked)
                // Schedule or cancel worker based on setting
                scheduleApprovalCheckWorker()
                Snackbar.make(
                    binding.root,
                    if (isChecked) "Notifikasi approval diaktifkan" else "Notifikasi approval dinonaktifkan",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        binding.switchDoStatusNotif.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsManager.setDoStatusNotificationEnabled(isChecked)
                Snackbar.make(
                    binding.root,
                    if (isChecked) "Notifikasi status DO diaktifkan" else "Notifikasi status DO dinonaktifkan",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        // Check Interval
        binding.layoutCheckInterval.setOnClickListener {
            showCheckIntervalDialog()
        }

        // Sync Settings
        binding.switchSyncWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsManager.setSyncOnWifiOnly(isChecked)
                Snackbar.make(
                    binding.root,
                    if (isChecked) "Sinkronisasi hanya via WiFi" else "Sinkronisasi via WiFi/Data",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsManager.setAutoSyncEnabled(isChecked)
                binding.layoutAutoSyncInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
                Snackbar.make(
                    binding.root,
                    if (isChecked) "Sinkronisasi otomatis diaktifkan" else "Sinkronisasi otomatis dinonaktifkan",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        binding.layoutAutoSyncInterval.setOnClickListener {
            showCheckIntervalDialog()
        }

        // Performance Settings
        binding.switchLowDataMode.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsManager.setLowDataModeEnabled(isChecked)
                Snackbar.make(
                    binding.root,
                    if (isChecked) "Mode hemat data diaktifkan" else "Mode hemat data dinonaktifkan",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsManager.setVibrationEnabled(isChecked)
                Snackbar.make(
                    binding.root,
                    if (isChecked) "Getaran diaktifkan" else "Getaran dinonaktifkan",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsManager.setSoundEnabled(isChecked)
                Snackbar.make(
                    binding.root,
                    if (isChecked) "Suara diaktifkan" else "Suara dinonaktifkan",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        // Cache Clear
        binding.btnClearCache.setOnClickListener {
            showClearCacheConfirmation()
        }

        // Sync Now
        binding.btnSyncNow.setOnClickListener {
            performSync()
        }

        // Developer Info
        binding.layoutDeveloper.setOnClickListener {
            showAboutDialog()
        }

        // Reset Settings
        binding.btnResetSettings.setOnClickListener {
            showResetSettingsConfirmation()
        }
    }

    private fun loadAppInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvAppVersion.text = "v${packageInfo.versionName}"
            binding.tvBuildNumber.text = "${packageInfo.versionCode}"

            // Check if update available
            lifecycleScope.launch {
                val status = appStatusManager.appStatus.first()
                when (status) {
                    com.example.handheldapp.utils.AppStatus.UPDATE_AVAILABLE,
                    com.example.handheldapp.utils.AppStatus.UPDATE_REQUIRED -> {
                        binding.chipUpdateStatus.visibility = View.VISIBLE
                        binding.chipUpdateStatus.text = "Update Tersedia"
                        binding.chipUpdateStatus.setChipBackgroundColorResource(R.color.warning)
                    }
                    else -> {
                        binding.chipUpdateStatus.visibility = View.VISIBLE
                        binding.chipUpdateStatus.text = "Terbaru"
                        binding.chipUpdateStatus.setChipBackgroundColorResource(R.color.success)
                    }
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            binding.tvAppVersion.text = "Unknown"
            binding.tvBuildNumber.text = "-"
        }
    }

    private fun calculateCacheSize() {
        lifecycleScope.launch {
            val cacheDir = cacheDir
            val externalCacheDir = externalCacheDir

            var totalSize = 0L
            totalSize += getDirSize(cacheDir)
            externalCacheDir?.let { totalSize += getDirSize(it) }

            binding.tvCacheSize.text = formatFileSize(totalSize)
        }
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        if (dir.exists()) {
            val files = dir.listFiles()
            files?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    // ==================== DIALOGS ====================

    private fun showThemeSelectionDialog() {
        val themes = arrayOf("Terang", "Gelap", "Ikuti Sistem")
        val themeValues = arrayOf("light", "dark", "system")

        lifecycleScope.launch {
            val currentTheme = settingsManager.getThemeMode().first()
            val currentIndex = themeValues.indexOf(currentTheme).coerceAtLeast(0)

            var selectedIndex = currentIndex

            AlertDialog.Builder(this@SettingActivity)
                .setTitle("Pilih Tema")
                .setSingleChoiceItems(themes, currentIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton("Simpan") { _, _ ->
                    lifecycleScope.launch {
                        settingsManager.setThemeMode(themeValues[selectedIndex])
                        binding.tvThemeValue.text = themes[selectedIndex]
                        applyTheme(themeValues[selectedIndex])
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun showFontSizeSelectionDialog() {
        val sizes = arrayOf("Kecil", "Normal", "Besar", "Sangat Besar")
        val sizeValues = arrayOf("small", "normal", "large", "xlarge")

        lifecycleScope.launch {
            val currentSize = settingsManager.getFontSize().first()
            val currentIndex = sizeValues.indexOf(currentSize).coerceAtLeast(0)

            var selectedIndex = currentIndex

            AlertDialog.Builder(this@SettingActivity)
                .setTitle("Pilih Ukuran Font")
                .setSingleChoiceItems(sizes, currentIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton("Simpan") { _, _ ->
                    lifecycleScope.launch {
                        settingsManager.setFontSize(sizeValues[selectedIndex])
                        binding.tvFontSizeValue.text = sizes[selectedIndex]

                        // Recreate activity to apply font size immediately
                        recreate()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun showCheckIntervalDialog() {
        val intervals = arrayOf("5 menit", "10 menit", "15 menit", "30 menit", "1 jam")
        val intervalValues = arrayOf(5, 10, 15, 30, 60)

        lifecycleScope.launch {
            val currentInterval = settingsManager.getNotificationCheckInterval().first()
            val currentIndex = intervalValues.indexOf(currentInterval).coerceAtLeast(2) // Default 15 menit

            var selectedIndex = currentIndex

            AlertDialog.Builder(this@SettingActivity)
                .setTitle("Interval Pengecekan Notifikasi")
                .setSingleChoiceItems(intervals, currentIndex) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton("Simpan") { _, _ ->
                    lifecycleScope.launch {
                        settingsManager.setNotificationCheckInterval(intervalValues[selectedIndex])
                        binding.tvCheckIntervalValue.text = "Setiap ${intervals[selectedIndex]}"
                        // Reschedule worker with new interval
                        scheduleApprovalCheckWorker()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun showClearCacheConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Cache")
            .setMessage("Apakah Anda yakin ingin menghapus cache aplikasi?\n\nIni akan menghapus data sementara seperti gambar yang di-cache dan file temporary.")
            .setPositiveButton("Hapus") { _, _ ->
                clearCache()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showResetSettingsConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Reset Pengaturan")
            .setMessage("Apakah Anda yakin ingin mengembalikan semua pengaturan ke default?\n\nIni tidak akan menghapus data login atau data offline Anda.")
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    settingsManager.clearAllSettings()
                    loadCurrentSettings()
                    applyTheme("system")
                    Snackbar.make(binding.root, "Pengaturan direset ke default", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAboutDialog() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)

            AlertDialog.Builder(this)
                .setTitle("Tentang Aplikasi")
                .setMessage("""
                    Warehouse Handheld App
                    
                    Versi: ${packageInfo.versionName}
                    Build: ${packageInfo.versionCode}
                    
                    Aplikasi ini dikembangkan untuk membantu operasional gudang dalam proses penerimaan barang menggunakan sistem tallysheet dan barcode scanning.
                    
                    Fitur Utama:
                    • Scan barcode untuk penerimaan barang
                    • Tracking status DO dan approval
                    • Mode offline untuk operasi tanpa jaringan
                    • Sinkronisasi otomatis dengan server
                    
                    © 2026 Development Team
                    Semua hak dilindungi.
                """.trimIndent())
                .setPositiveButton("Tutup", null)
                .setIcon(R.drawable.ic_warehouse)
                .show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Gagal memuat info aplikasi", Snackbar.LENGTH_SHORT).show()
        }
    }

    // ==================== ACTIONS ====================

    private fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun clearCache() {
        lifecycleScope.launch {
            try {
                // Clear internal cache
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()

                // Clear external cache
                externalCacheDir?.deleteRecursively()
                externalCacheDir?.mkdirs()

                // Update timestamp
                settingsManager.setLastCacheClear()

                // Recalculate cache size
                calculateCacheSize()

                Snackbar.make(binding.root, "Cache berhasil dihapus", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Gagal menghapus cache: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun performSync() {
        binding.btnSyncNow.isEnabled = false
        binding.btnSyncNow.text = "Syncing..."

        lifecycleScope.launch {
            try {
                // Get gudang code from session
                val gudangCode = sessionManager.getBranchCode().first() ?: ""

                // Perform product sync
                val result = productSyncRepository.syncMasterProducts(
                    gudangCode = gudangCode,
                    forceSync = true
                )

                when (result) {
                    is ProductSyncRepository.SyncResult.Success -> {
                        settingsManager.setLastSyncTime()
                        val now = System.currentTimeMillis()
                        binding.tvLastSync.text = dateFormat.format(Date(now))
                        Snackbar.make(
                            binding.root,
                            "Sinkronisasi berhasil: ${result.count} produk",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    is ProductSyncRepository.SyncResult.AlreadySynced -> {
                        Snackbar.make(binding.root, "Data sudah sinkron", Snackbar.LENGTH_SHORT).show()
                    }
                    is ProductSyncRepository.SyncResult.Empty -> {
                        Snackbar.make(binding.root, "Tidak ada data untuk sinkronisasi", Snackbar.LENGTH_SHORT).show()
                    }
                    is ProductSyncRepository.SyncResult.Error -> {
                        Snackbar.make(
                            binding.root,
                            "Gagal sinkronisasi: ${result.message}",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Error: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            } finally {
                binding.btnSyncNow.isEnabled = true
                binding.btnSyncNow.text = "Sync"
            }
        }
    }

    private fun scheduleApprovalCheckWorker() {
        lifecycleScope.launch {
            val isEnabled = settingsManager.isApprovalNotificationEnabled().first()
            if (isEnabled) {
                val interval = settingsManager.getNotificationCheckInterval().first().toLong()
                com.example.handheldapp.worker.ApprovalNotificationWorker.schedule(
                    context = this@SettingActivity,
                    intervalMinutes = interval
                )
                android.util.Log.d("SettingsActivity", "✅ Approval check worker scheduled every $interval minutes")
            } else {
                // Cancel the worker if disabled
                com.example.handheldapp.worker.ApprovalNotificationWorker.cancel(this@SettingActivity)
                android.util.Log.d("SettingsActivity", "❌ Approval check worker cancelled")
            }
        }
    }

    // ==================== HELPERS ====================

    private fun getThemeDisplayText(mode: String): String {
        return when (mode) {
            "light" -> "Terang"
            "dark" -> "Gelap"
            else -> "Ikuti Sistem"
        }
    }

    private fun getFontSizeDisplayText(size: String): String {
        return when (size) {
            "small" -> "Kecil"
            "large" -> "Besar"
            "xlarge" -> "Sangat Besar"
            else -> "Normal"
        }
    }
}
