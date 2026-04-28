package com.example.handheldapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.handheldapp.R
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.databinding.ActivityProfileBinding
import com.example.handheldapp.ui.base.BaseActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * Profile Activity - Menampilkan dan mengelola profil user
 *
 * Fitur:
 * - View profile info (nama, role, cabang, dll)
 * - Edit profile (nama, dll)
 * - Statistik pribadi (scan count, dll)
 * - Activity log
 * - Notifikasi pribadi
 * - Data management
 * - Logout
 */
@AndroidEntryPoint
class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding

    @Inject
    lateinit var sessionManager: SessionManager

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadProfileData()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Profil Saya"
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun loadProfileData() {
        lifecycleScope.launch {
            try {
                // Load user data from session
                val userName = sessionManager.getUserName().first()
                val branchCode = sessionManager.getBranchCode().first()
                val branchName = sessionManager.getBranchName().first()
                val userId = sessionManager.getUserId().first()

                // Display user info
                binding.tvUserName.text = userName ?: "User"
                binding.tvUserId.text = "@${userId ?: "unknown"}"
                binding.tvUserBranch.text = "${branchName ?: "Unknown"} - ${branchCode ?: "XXX"}"

                // Load profile image if available
                // TODO: Load from server
                loadDefaultAvatar()

                // Load statistics (mock data - TODO: implement real API)
                loadStatistics()

            } catch (e: Exception) {
                Snackbar.make(binding.root, "Gagal memuat data profile", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDefaultAvatar() {
        // Set default avatar image
        binding.ivProfilePhoto.setImageResource(R.drawable.ic_person)
    }

    private fun loadStatistics() {
        // TODO: Load from API
        // For now, show mock data
        binding.tvScanCount.text = "24"
        binding.tvItemCount.text = "156"
        binding.tvApprovalCount.text = "8"

        binding.tvLastActivity.text = "Scan DO-260304-0012\n2 jam yang lalu"
    }

    private fun setupClickListeners() {
        // Edit Profile
        binding.layoutEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        // View Statistics Detail
        binding.layoutStatistics.setOnClickListener {
            // TODO: Navigate to statistics detail
            Snackbar.make(binding.root, "Statistik detail (coming soon)", Snackbar.LENGTH_SHORT).show()
        }

        // Activity Log
        binding.layoutActivityLog.setOnClickListener {
            // TODO: Navigate to activity log
            Snackbar.make(binding.root, "Activity Log (coming soon)", Snackbar.LENGTH_SHORT).show()
        }

        // Scan History
        binding.layoutScanHistory.setOnClickListener {
            // TODO: Navigate to scan history
            Snackbar.make(binding.root, "Riwayat Scan (coming soon)", Snackbar.LENGTH_SHORT).show()
        }

        // Notifications
        binding.layoutNotifications.setOnClickListener {
            // TODO: Navigate to notifications
            val badge = binding.badgeNotificationCount.text.toString().toIntOrNull() ?: 0
            Snackbar.make(binding.root, "Notifikasi: $badge unread (coming soon)", Snackbar.LENGTH_SHORT).show()
        }

        // Offline Data Management
        binding.layoutOfflineData.setOnClickListener {
            showOfflineDataDialog()
        }

        // Scan Preferences
        binding.layoutScanPreferences.setOnClickListener {
            // TODO: Navigate to scan preferences
            Snackbar.make(binding.root, "Preferensi Scan (coming soon)", Snackbar.LENGTH_SHORT).show()
        }

        // Logout
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    // ==================== DIALOGS ====================

    private fun showEditProfileDialog() {
        // TODO: Navigate to Edit Profile Activity
        Snackbar.make(binding.root, "Edit Profile (coming soon)", Snackbar.LENGTH_SHORT).show()
    }

    private fun showOfflineDataDialog() {
        lifecycleScope.launch {
            val offlineDataSize = calculateOfflineDataSize()
            val lastSyncTime = "2 jam yang lalu" // TODO: Get from SettingsManager

            val message = """
                Data Offline Tersimpan: $offlineDataSize
                Terakhir Sync: $lastSyncTime
                
                Apa yang ingin Anda lakukan?
            """.trimIndent()

            AlertDialog.Builder(this@ProfileActivity)
                .setTitle("Data Offline")
                .setMessage(message)
                .setPositiveButton("Sync Sekarang") { _, _ ->
                    performSync()
                }
                .setNeutralButton("Hapus Data") { _, _ ->
                    showClearOfflineDataConfirmation()
                }
                .setNegativeButton("Tutup", null)
                .show()
        }
    }

    private fun calculateOfflineDataSize(): String {
        // TODO: Calculate actual size
        return "145 MB"
    }

    private fun performSync() {
        // TODO: Trigger sync
        Snackbar.make(binding.root, "Sinkronisasi dimulai...", Snackbar.LENGTH_SHORT).show()
    }

    private fun showClearOfflineDataConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Data Offline")
            .setMessage("Apakah Anda yakin ingin menghapus semua data offline?\n\nData yang akan dihapus:\n• Riwayat scan\n• Cache produk\n• Data temporary\n\nData login akan tetap tersimpan.")
            .setPositiveButton("Hapus") { _, _ ->
                clearOfflineData()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun clearOfflineData() {
        lifecycleScope.launch {
            try {
                // TODO: Clear offline data from database
                Snackbar.make(binding.root, "Data offline berhasil dihapus", Snackbar.LENGTH_SHORT).show()
                loadStatistics() // Refresh stats
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Gagal menghapus data: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Apakah Anda yakin ingin keluar?\n\nData offline akan tetap tersimpan dan bisa digunakan untuk login offline.")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                // Clear session but keep offline data
                sessionManager.clearSession()

                // Navigate to login
                val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

                Snackbar.make(binding.root, "Logout berhasil", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Gagal logout: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
