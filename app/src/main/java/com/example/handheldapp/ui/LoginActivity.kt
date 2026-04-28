package com.example.handheldapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.handheldapp.R
import com.example.handheldapp.data.model.Branch
import com.example.handheldapp.data.model.Company
import com.example.handheldapp.databinding.ActivityLoginBinding
import com.example.handheldapp.ui.base.BaseActivity
import com.example.handheldapp.utils.AppStatus
import com.example.handheldapp.utils.AppStatusManager
import com.example.handheldapp.utils.AppUpdateManager
import com.example.handheldapp.utils.CustomDialogHelper
import com.example.handheldapp.utils.NetworkMonitor
import com.example.handheldapp.utils.Resource
import com.example.handheldapp.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LoginActivity - 3-Step Wizard
 *
 * STEP 1: Pilih Company (Dropdown)
 * STEP 2: Pilih Branch (Dropdown)
 * STEP 3: Input Email + Password → Login → Navigate to MainActivity
 */
@AndroidEntryPoint
class LoginActivity : BaseActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    @Inject lateinit var appStatusManager: AppStatusManager
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var appUpdateManager: AppUpdateManager

    private var companiesList: List<Company> = emptyList()
    private var branchesList: List<Branch> = emptyList()

    // Simpan referensi download dialog agar bisa diupdate progress-nya
    private var activeDownloadDialog: CustomDialogHelper.DownloadProgressDialog? = null

    // =========================================================
    //  LIFECYCLE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
        checkAppStatusOnLogin()
        viewModel.loadCompanies()
    }

    // =========================================================
    //  OBSERVERS
    // =========================================================

    private fun setupObservers() {
        // Companies
        viewModel.companies.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    resource.data?.let {
                        companiesList = it
                        setupCompanyDropdown()
                        showStep1()
                    }
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Gagal memuat data perusahaan")
                }
            }
        }

        // Branches
        viewModel.branches.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    resource.data?.let {
                        branchesList = it
                        if (it.isNotEmpty()) {
                            setupBranchDropdown()
                            showStep2()
                        } else {
                            showError("Tidak ada cabang tersedia untuk perusahaan ini")
                        }
                    }
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Gagal memuat daftar cabang")
                }
            }
        }

        // Login result
        viewModel.loginResult.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    navigateToMain()
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Login gagal. Periksa email dan password Anda")
                }
            }
        }
    }

    // =========================================================
    //  LISTENERS
    // =========================================================

    private fun setupListeners() {
        // Step 1 → pilih company, auto load branches
        binding.btnNextStep1.setOnClickListener {
            val pos = binding.spinnerCompany.selectedItemPosition
            if (pos != -1 && pos < companiesList.size) {
                viewModel.selectCompany(companiesList[pos])
            }
        }

        // Step 2 → Pilih branch (WAJIB untuk semua user), lanjut ke step 3
        // SPV & Admin IT: Bisa pilih branch apa saja (full access)
        // Checker/Admin Gudang/Kepala Gudang: Hanya bisa pilih branch sesuai usr_areacode
        binding.btnNextStep2.setOnClickListener {
            val pos = binding.spinnerBranch.selectedItemPosition
            if (pos != -1 && pos < branchesList.size) {
                viewModel.selectBranch(branchesList[pos])
                showStep3()
            } else {
                showError("Pilih branch terlebih dahulu")
            }
        }

        // Step 3 → login
        binding.btnLogin.setOnClickListener {
            val email    = binding.edtUsername.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            when {
                email.isEmpty() -> {
                    binding.edtUsername.error = "Masukkan email"
                    binding.edtUsername.requestFocus()
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    binding.edtUsername.error = "Format email tidak valid"
                    binding.edtUsername.requestFocus()
                }
                password.isEmpty() -> {
                    binding.edtPassword.error = "Masukkan password"
                    binding.edtPassword.requestFocus()
                }
                !isPasswordValid(password) -> {
                    // ✅ GANTI: AlertDialog.Builder → CustomDialogHelper
                    CustomDialogHelper.showPasswordRequirements(
                        context  = this,
                        password = password
                    )
                }
                else -> {
                    viewModel.loginWithCredentials(email, password)
                }
            }
        }

        // Back navigation
        binding.btnBackStep2.setOnClickListener { showStep1() }
        binding.btnBackStep3.setOnClickListener { showStep2() }
    }

    // =========================================================
    //  DROPDOWN SETUP
    // =========================================================

    private fun setupCompanyDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            companiesList.map { it.getDisplayText() }
        )
        binding.spinnerCompany.adapter = adapter
    }

    private fun setupBranchDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            branchesList.map { it.getDisplayText() }
        )
        binding.spinnerBranch.adapter = adapter
    }

    // =========================================================
    //  PASSWORD VALIDATION
    // =========================================================

    private fun isPasswordValid(password: String): Boolean {
        if (password.length < 8) return false
        if (!password.any { it.isUpperCase() }) return false
        if (!password.any { it.isDigit() }) return false
        return true
    }

    // =========================================================
    //  STEP NAVIGATION
    // =========================================================

    private fun showStep1() {
        toggleCards(step = 1)
        updateStepIndicator(step = 1)
    }

    private fun showStep2() {
        toggleCards(step = 2)
        updateStepIndicator(step = 2)
        binding.tvSelectedCompany.text = viewModel.getSelectedCompanyName()
    }

    private fun showStep3() {
        toggleCards(step = 3)
        updateStepIndicator(step = 3)
    }

    private fun toggleCards(step: Int) {
        binding.cardStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        binding.cardStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        binding.cardStep3.visibility = if (step == 3) View.VISIBLE else View.GONE
    }

    private fun updateStepIndicator(step: Int) {
        val activeColor   = resources.getColor(android.R.color.white, theme)
        val inactiveColor = resources.getColor(android.R.color.darker_gray, theme)

        if (step >= 1) {
            binding.tvStep1Number.setBackgroundResource(R.drawable.bg_step_number_active)
            binding.tvStep1Number.setTextColor(activeColor)
        } else {
            binding.tvStep1Number.setBackgroundResource(R.drawable.bg_step_number_inactive)
            binding.tvStep1Number.setTextColor(inactiveColor)
        }

        if (step >= 2) {
            binding.tvStep2Number.setBackgroundResource(R.drawable.bg_step_number_active)
            binding.tvStep2Number.setTextColor(activeColor)
            binding.stepLine1.setBackgroundColor(resources.getColor(R.color.success, theme))
        } else {
            binding.tvStep2Number.setBackgroundResource(R.drawable.bg_step_number_inactive)
            binding.tvStep2Number.setTextColor(inactiveColor)
            binding.stepLine1.setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme))
        }

        if (step >= 3) {
            binding.tvStep3Number.setBackgroundResource(R.drawable.bg_step_number_active)
            binding.tvStep3Number.setTextColor(activeColor)
            binding.stepLine2.setBackgroundColor(resources.getColor(R.color.success, theme))
        } else {
            binding.tvStep3Number.setBackgroundResource(R.drawable.bg_step_number_inactive)
            binding.tvStep3Number.setTextColor(inactiveColor)
            binding.stepLine2.setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme))
        }
    }

    // =========================================================
    //  APP STATUS CHECK
    // =========================================================

    private fun checkAppStatusOnLogin() {
        lifecycleScope.launch {
            try {
                val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode
                val status = appStatusManager.checkAppStatus(currentVersionCode)

                Log.d(TAG, "App status: $status")
                Log.d(TAG, "Maintenance type: ${appStatusManager.getMaintenanceType()}")
                Log.d(TAG, "Offline work allowed: ${appStatusManager.isOfflineWorkAllowed()}")
                Log.d(TAG, "Has scheduled maintenance: ${appStatusManager.hasScheduledMaintenance()}")

                // CRITICAL → block semua
                if (appStatusManager.isCriticalMaintenance() ||
                    (status == AppStatus.MAINTENANCE && !appStatusManager.isOfflineWorkAllowed())
                ) {
                    showCriticalMaintenanceBlocker()
                    return@launch
                }

                when (status) {
                    AppStatus.MAINTENANCE      -> showMaintenanceAlert()
                    AppStatus.UPDATE_REQUIRED  -> showUpdateDialog(mustUpdate = true)
                    AppStatus.UPDATE_AVAILABLE -> showUpdateDialog(mustUpdate = false)
                    AppStatus.OK -> {
                        Log.d(TAG, "App status OK")
                        if (appStatusManager.hasScheduledMaintenance()) {
                            showScheduledMaintenanceAlert()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking app status: ${e.message}")
            }
        }
    }

    // =========================================================
    //  DIALOG METHODS — semua pakai CustomDialogHelper
    // =========================================================

    /**
     * ✅ GANTI: AlertDialog.Builder → CustomDialogHelper.showCriticalMaintenance
     * Blocking total, user tidak bisa dismiss
     */
    private fun showCriticalMaintenanceBlocker() {
        val message    = appStatusManager.getMaintenanceMessage()
            ?: "Server sedang dalam maintenance penting.\n\nAplikasi tidak dapat digunakan saat ini.\n\nSilakan coba beberapa saat lagi."
        val endTime    = appStatusManager.statusDetails.value?.maintenanceEnd

        CustomDialogHelper.showCriticalMaintenance(
            context      = this,
            message      = "$message\n\nHubungi IT jika urgent.",
            estimatedEnd = endTime,
            onRetry      = { checkAppStatusOnLogin() },
            onClose      = { finishAffinity() }
        )

        disableLoginUI()
    }

    /**
     * ✅ GANTI: AlertDialog.Builder → CustomDialogHelper.showMaintenanceWarning
     * Non-blocking, user bisa lanjut offline
     */
    private fun showMaintenanceAlert() {
        val message = appStatusManager.getMaintenanceMessage()
            ?: "Server sedang dalam pemeliharaan.\n\nAnda tetap bisa login dan melakukan scan, namun data akan disimpan secara offline.\n\nSinkronisasi otomatis akan berjalan setelah pemeliharaan selesai."

        CustomDialogHelper.showMaintenanceWarning(
            context = this,
            message = message
        )
    }

    /**
     * ✅ GANTI: AlertDialog.Builder → CustomDialogHelper.showScheduledMaintenance
     * Non-blocking, pengumuman sebelum maintenance dimulai
     */
    private fun showScheduledMaintenanceAlert() {
        val info = appStatusManager.getScheduledMaintenanceInfo() ?: return

        val typeTitle = when (info.maintenanceType) {
            "critical" -> "Maintenance Penting"
            "major"    -> "Maintenance Besar"
            else       -> "Maintenance Ringan"
        }

        CustomDialogHelper.showScheduledMaintenance(
            context = this,
            info = CustomDialogHelper.ScheduledMaintenanceInfo(
                title          = "$typeTitle Dijadwalkan",
                message        = "${info.message ?: "Maintenance dijadwalkan."}\n\nPastikan pekerjaan Anda tersimpan sebelum waktu maintenance dimulai.",
                scheduleDate   = info.startFormatted,
                scheduleTime   = if (!info.endFormatted.isNullOrEmpty())
                    "${info.startFormatted} – ${info.endFormatted}"
                else info.startFormatted,
                duration       = info.duration,
                timeUntilStart = info.timeUntilStart
            )
        )
    }

    /**
     * ✅ GANTI: AlertDialog.Builder → CustomDialogHelper.showUpdate
     * mustUpdate = true  → wajib, tidak bisa Nanti
     * mustUpdate = false → opsional, ada tombol Nanti
     */
    private fun showUpdateDialog(mustUpdate: Boolean) {
        val message = appStatusManager.getReleaseNotes()
            ?: if (mustUpdate)
                "Versi aplikasi Anda sudah tidak didukung.\n\nAnda harus mengupdate aplikasi ke versi terbaru untuk melanjutkan."
            else
                "Versi baru aplikasi tersedia.\n\nAnda dapat mengupdate sekarang atau nanti."

        val updateUrl         = appStatusManager.getUpdateUrl()
        val latestVersion     = appStatusManager.statusDetails.value?.latestVersion ?: "terbaru"
        val hasDirectDownload = !updateUrl.isNullOrEmpty() &&
                (updateUrl.endsWith(".apk") || updateUrl.contains("/download/"))

        CustomDialogHelper.showUpdate(
            context = this,
            config  = CustomDialogHelper.UpdateConfig(
                mustUpdate        = mustUpdate,
                version           = latestVersion,
                message           = message,
                fileInfo          = if (hasDirectDownload) "APK siap diunduh langsung" else "Hubungi admin untuk download APK",
                hasDirectDownload = hasDirectDownload
            )
        ) { action ->
            when (action) {
                CustomDialogHelper.UpdateAction.Download  -> {
                    if (hasDirectDownload) startApkDownload(updateUrl!!, latestVersion)
                    else Toast.makeText(this, "Silakan download APK dari admin", Toast.LENGTH_LONG).show()
                }
                CustomDialogHelper.UpdateAction.Later     -> { /* user lanjut login */ }
                CustomDialogHelper.UpdateAction.PlayStore -> openPlayStoreForUpdate()
            }
        }
    }

    // =========================================================
    //  APK DOWNLOAD
    // =========================================================

    /**
     * ✅ GANTI: AlertDialog progress manual → CustomDialogHelper.showDownloadProgress
     * Progress bar real-time, tombol batal
     */
    private fun startApkDownload(downloadUrl: String, version: String) {
        val progressDialog = CustomDialogHelper.showDownloadProgress(
            context  = this,
            version  = version,
            onCancel = {
                appUpdateManager.cancelDownload()
                activeDownloadDialog = null
            }
        )
        activeDownloadDialog = progressDialog

        lifecycleScope.launch {
            appUpdateManager.downloadApk(downloadUrl, version).collect { progress ->
                when (progress.status) {
                    AppUpdateManager.DownloadStatus.PENDING -> {
                        progressDialog.setStatus("Menunggu download dimulai...")
                    }
                    AppUpdateManager.DownloadStatus.RUNNING -> {
                        val downloaded = appUpdateManager.formatBytes(progress.downloadedBytes)
                        val total      = appUpdateManager.formatBytes(progress.totalBytes)
                        progressDialog.updateProgress(
                            percent    = progress.progress,
                            downloaded = downloaded,
                            total      = total
                        )
                    }
                    AppUpdateManager.DownloadStatus.PAUSED -> {
                        progressDialog.setStatus("Download dijeda...")
                    }
                    AppUpdateManager.DownloadStatus.SUCCESSFUL -> {
                        progressDialog.dismiss()
                        activeDownloadDialog = null
                        val installed = appUpdateManager.installApk(version)
                        if (!installed) {
                            showError("Gagal membuka installer. Coba install manual dari folder Download.")
                        }
                    }
                    AppUpdateManager.DownloadStatus.FAILED,
                    AppUpdateManager.DownloadStatus.CANCELLED -> {
                        progressDialog.dismiss()
                        activeDownloadDialog = null
                        // Tampilkan dialog gagal — reuse showCriticalMaintenance sebagai error dialog
                        CustomDialogHelper.showCriticalMaintenance(
                            context      = this@LoginActivity,
                            message      = "${progress.errorMessage ?: "Download gagal"}.\n\nCoba lagi atau download dari Play Store.",
                            estimatedEnd = null,
                            onRetry      = { startApkDownload(downloadUrl, version) },
                            onClose      = { openPlayStoreForUpdate() }
                        )
                    }
                }
            }
        }
    }

    private fun openPlayStoreForUpdate() {
        val updateUrl = appStatusManager.getUpdateUrl()

        if (!updateUrl.isNullOrEmpty()) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error opening update URL: ${e.message}")
            }
        }

        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                    .apply { setPackage(PLAY_STORE_PACKAGE) }
            )
        } catch (e: Exception) {
            startActivity(
                Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            )
        }
    }

    // =========================================================
    //  UI HELPERS
    // =========================================================

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled     = !isLoading
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun disableLoginUI() {
        binding.edtUsername.isEnabled = false
        binding.edtPassword.isEnabled = false
        binding.btnLogin.isEnabled    = false
        binding.btnLogin.alpha        = 0.5f
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}