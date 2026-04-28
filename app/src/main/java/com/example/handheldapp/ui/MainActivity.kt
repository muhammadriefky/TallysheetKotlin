package com.example.handheldapp.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handheldapp.R
import com.example.handheldapp.adapter.DeliveryOrderAdapter
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.databinding.ActivityMainBinding
import com.example.handheldapp.repository.ProductSyncRepository
import com.example.handheldapp.ui.base.BaseActivity
import com.example.handheldapp.utils.AppStatus
import com.example.handheldapp.utils.AppStatusManager
import com.example.handheldapp.utils.AppUpdateManager
import com.example.handheldapp.utils.NetworkMonitor
import com.example.handheldapp.utils.Resource
import com.example.handheldapp.viewmodel.DeliveryOrderViewModel
import com.example.handheldapp.viewmodel.StagingAreaViewModel
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DeliveryOrderViewModel by viewModels()
    private val stagingViewModel: StagingAreaViewModel by viewModels()

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var appStatusManager: AppStatusManager

    @Inject
    lateinit var productSyncRepository: ProductSyncRepository

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    @Inject
    lateinit var settingsManager: com.example.handheldapp.data.local.SettingsManager

    private lateinit var adapter: DeliveryOrderAdapter
    private lateinit var toggle: ActionBarDrawerToggle // Tombol Hamburger
    private var branchCode: String = ""
    private var companyCode: String = ""

    // For staging selection
    private var pendingStagingDo: DeliveryOrder? = null
    private var availableStagingAreas: List<StagingArea> = emptyList()

    // Date filter
    private var selectedDate: Calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

    // Search
    private var searchJob: Job? = null
    private var allDeliveryOrders: List<DeliveryOrder> = emptyList()
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Setup Toolbar & Sidebar
        setupToolbarAndDrawer()

        // 2. Setup UI Components
        setupRecyclerView()
        setupFilterChips()
        setupSwipeRefresh()
        setupDateFilter()
        setupSearch()
        setupObservers()
        setupAppStatusObservers() // App status & network monitoring
        setupUpdateButton()
        setupNotificationButton()
        setupNavigationHeaderButtons()

        // 3. Load Data
        loadBranchAndUserInfo()

        // 4. Check app status & sync products
        checkAppStatusOnStart()
    }

    private fun setupToolbarAndDrawer() {
        // Gunakan Toolbar dari layout
        setSupportActionBar(binding.toolbar)

        // Konfigurasi Toggle Sidebar (Tombol Hamburger)
        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open, // Tambahkan di strings.xml
            R.string.navigation_drawer_close // Tambahkan di strings.xml
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Listener untuk menu sidebar
        binding.navView.setNavigationItemSelectedListener(this)
    }

    private fun loadBranchAndUserInfo() {
        lifecycleScope.launch {
            branchCode = sessionManager.getBranchCode().first() ?: ""
            companyCode = sessionManager.getCompanyCode().first() ?: ""
            val branchName = sessionManager.getBranchName().first() ?: "-"
            val userName = sessionManager.getUserName().first() ?: "User"

            if (branchCode.isEmpty()) {
                navigateToLogin()
                return@launch
            }

            // Update Toolbar Branch Text
            binding.tvToolbarBranch.text = "Cabang: $branchName"

            // Update Header Sidebar (Nama, Branch, & Role)
            val headerView = binding.navView.getHeaderView(0)
            headerView.findViewById<TextView>(R.id.tvNavName)?.text = userName
            headerView.findViewById<TextView>(R.id.tvNavEmail)?.text = "Branch: $branchName"

            // Update role chip (optional - bisa disesuaikan dengan role user dari session)
            val chipRole = headerView.findViewById<com.google.android.material.chip.Chip>(R.id.chipRole)
            chipRole?.text = "Warehouse Staff"

            // PENTING: Set date filter ke hari ini SEBELUM load data
            val todayDate = dateFormat.format(selectedDate.time)
            viewModel.setDateFilter(todayDate)

            // Load hanya DO aktif (belum selesai scan) - exclude yang sudah completed
            viewModel.loadDeliveryOrders(branchCode, companyCode, statusFilter = "active")
        }
    }

    // --- Handling Klik Menu Sidebar ---
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Sudah di home, refresh data
                viewModel.refreshDeliveryOrders()
                Snackbar.make(binding.root, "Dashboard", Snackbar.LENGTH_SHORT).show()
            }

            // Tally Sheet Submenu
            R.id.nav_tallysheet_aktif -> {
                // Sudah di halaman ini, refresh dengan filter active saja
                viewModel.loadDeliveryOrders(branchCode, companyCode, statusFilter = "active")
                binding.chipBerlangsung.isChecked = true
                Snackbar.make(binding.root, "Menampilkan Tallysheet Aktif", Snackbar.LENGTH_SHORT).show()
            }
            R.id.nav_tallysheet_history -> {
                // Navigate ke History Tallysheet Activity
                startActivity(Intent(this, TallysheetHistoryActivity::class.java))
            }

            // Warehouse Operations
            R.id.nav_putaway -> {
                showComingSoonDialog(
                    "📦 Put Away / Penempatan Rak",
                    "Fitur ini memungkinkan Anda untuk:\n" +
                            "• Menentukan lokasi rak untuk barang hasil scan\n" +
                            "• Auto-suggest rak berdasarkan kategori\n" +
                            "• Tracking real-time posisi barang di gudang\n" +
                            "• Print label lokasi rak\n\n" +
                            "Status: Dalam pengembangan"
                )
            }
            R.id.nav_stock_opname -> {
                showComingSoonDialog(
                    "📊 Stock Opname",
                    "Fitur ini memungkinkan Anda untuk:\n" +
                            "• Melakukan pengecekan fisik stok gudang\n" +
                            "• Scan barcode untuk validasi stok\n" +
                            "• Deteksi selisih stok otomatis\n" +
                            "• Generate laporan stock opname\n\n" +
                            "Status: Dalam pengembangan"
                )
            }
            R.id.nav_mutasi -> {
                showComingSoonDialog(
                    "🔄 Mutasi Barang",
                    "Fitur ini memungkinkan Anda untuk:\n" +
                            "• Transfer barang antar rak\n" +
                            "• Transfer barang antar gudang\n" +
                            "• Tracking history mutasi\n" +
                            "• Approval mutasi by supervisor\n\n" +
                            "Status: Dalam pengembangan"
                )
            }
            R.id.nav_btb -> {
                showComingSoonDialog(
                    "⚠️ BTB (Barang Tidak Bagus)",
                    "Fitur ini memungkinkan Anda untuk:\n" +
                            "• Catat barang rusak/reject\n" +
                            "• Foto dokumentasi kerusakan\n" +
                            "• Proses retur ke supplier\n" +
                            "• Tracking status BTB\n\n" +
                            "Status: Dalam pengembangan"
                )
            }

            // Reports
            R.id.nav_history -> {
                showComingSoonDialog(
                    "📜 History Scan Lengkap",
                    "Fitur ini memungkinkan Anda untuk:\n" +
                            "• Melihat semua history scan\n" +
                            "• Filter by tanggal, user, DO\n" +
                            "• Export data ke Excel/PDF\n" +
                            "• Analisis performa scan\n\n" +
                            "Status: Dalam pengembangan\n\n" +
                            "Saat ini gunakan menu 'Tallysheet History' untuk melihat riwayat."
                )
            }
            R.id.nav_reports -> {
                showComingSoonDialog(
                    "📈 Laporan Gudang",
                    "Fitur ini memungkinkan Anda untuk:\n" +
                            "• Dashboard analytics gudang\n" +
                            "• Laporan harian/bulanan\n" +
                            "• Grafik performa operasional\n" +
                            "• Export multi-format report\n\n" +
                            "Status: Dalam pengembangan"
                )
            }

            // Settings & Account
            R.id.nav_profile -> {
                // Navigate to Profile Activity
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            R.id.nav_settings -> {
                // Navigate to Settings Activity
                startActivity(Intent(this, SettingActivity::class.java))
            }
            R.id.nav_logout -> {
                showLogoutConfirmation()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Apakah Anda yakin ingin logout?")
            .setPositiveButton("Logout") { _, _ ->
                lifecycleScope.launch {
                    sessionManager.clearSession()
                    navigateToLogin()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Show informative dialog for coming soon features
     */
    private fun showComingSoonDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Mengerti") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }

    // --- Fungsi Navigasi & UI (Tetap Sama) ---

    private fun setupRecyclerView() {
        adapter = DeliveryOrderAdapter { deliveryOrder -> navigateToScan(deliveryOrder) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFilterChips() {
        // MainActivity hanya untuk DO Aktif (belum selesai scan)
        // Chip "Selesai" dan "Semua" disembunyikan karena history ada di halaman terpisah
        binding.chipBerlangsung.isChecked = true
        binding.chipBerlangsung.setOnClickListener {
            viewModel.loadDeliveryOrders(branchCode, companyCode, statusFilter = "active")
        }

        // Sembunyikan chip yang tidak diperlukan
        binding.chipSemua.visibility = View.GONE
        binding.chipSelesai.visibility = View.GONE
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshDeliveryOrders() }
    }

    private fun setupDateFilter() {
        // Set default to today
        updateDateFilterDisplay()

        binding.btnDateFilter.setOnClickListener {
            showDatePicker()
        }

        binding.btnClearDateFilter.setOnClickListener {
            // Clear filter - load all dates
            selectedDate = Calendar.getInstance()
            binding.btnDateFilter.text = "Semua Tanggal"
            binding.btnClearDateFilter.visibility = View.GONE
            viewModel.setDateFilter(null)
            viewModel.refreshDeliveryOrders()
        }
    }

    private fun setupSearch() {
        // Search with debounce
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce 300ms
                    currentSearchQuery = s?.toString()?.trim() ?: ""
                    applyLocalFilter()
                }
            }
        })

        // Search on keyboard action
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentSearchQuery = binding.etSearch.text?.toString()?.trim() ?: ""
                applyLocalFilter()
                // Hide keyboard
                binding.etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun applyLocalFilter() {
        if (allDeliveryOrders.isEmpty()) {
            binding.tvDoCount.text = "0 Items"
            return
        }

        val query = currentSearchQuery.lowercase()

        val filtered = if (query.isEmpty()) {
            allDeliveryOrders
        } else {
            allDeliveryOrders.filter { do_ ->
                // Search by DO number
                do_.dohNodo?.lowercase()?.contains(query) == true ||
                        // Search by Surat Jalan
                        do_.dohNoSj?.lowercase()?.contains(query) == true ||
                        // Search by Supplier code/name
                        do_.dohSupplier?.lowercase()?.contains(query) == true ||
                        // Search by Staging code
                        do_.stagingCode?.lowercase()?.contains(query) == true
            }
        }

        // Collapse all before updating list
        adapter.collapseAll()
        adapter.submitList(filtered)

        // Update DO count display (next to "Delivery Orders" title)
        binding.tvDoCount.text = "${filtered.size} Items"

        // Update statistics based on filtered/displayed list
        updateStatistics(filtered)

        showEmptyState(filtered.isEmpty())

        // Show result count
        if (query.isNotEmpty()) {
            supportActionBar?.subtitle = "${filtered.size} hasil ditemukan"
        } else {
            lifecycleScope.launch {
                val branchName = sessionManager.getBranchName().first() ?: "-"
                supportActionBar?.subtitle = branchName
            }
        }
    }

    private fun showDatePicker() {
        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH)
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            selectedDate.set(selectedYear, selectedMonth, selectedDay)
            updateDateFilterDisplay()
            binding.btnClearDateFilter.visibility = View.VISIBLE

            // Apply date filter
            val dateStr = dateFormat.format(selectedDate.time)
            viewModel.setDateFilter(dateStr)
            viewModel.refreshDeliveryOrders()
        }, year, month, day).show()
    }

    private fun updateDateFilterDisplay() {
        val today = Calendar.getInstance()
        val isToday = selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                selectedDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

        binding.btnDateFilter.text = if (isToday) {
            "📅 Hari Ini"
        } else {
            "📅 ${displayDateFormat.format(selectedDate.time)}"
        }
        // Filter akan di-set di loadBranchAndUserInfo() saat load data
    }

    private fun setupObservers() {
        viewModel.deliveryOrders.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    allDeliveryOrders = resource.data ?: emptyList()
                    // Statistics will be updated in applyLocalFilter() based on displayed list
                    applyLocalFilter() // Apply search filter if any
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Gagal memuat data")
                }
            }
        }

        // Observe staging areas for selection
        stagingViewModel.stagingAreas.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    // Show loading indicator if needed
                }
                is Resource.Success -> {
                    availableStagingAreas = resource.data?.filter { it.isAvailable } ?: emptyList()
                    pendingStagingDo?.let { deliveryOrder ->
                        showStagingSelectionDialog(deliveryOrder)
                    }
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, "Gagal memuat staging: ${resource.message}", Snackbar.LENGTH_LONG).show()
                    pendingStagingDo = null
                }
            }
        }

        // Observe staging assignment result
        stagingViewModel.assignmentResult.observe(this) { resource ->
            if (resource == null) return@observe

            when (resource) {
                is Resource.Loading -> {
                    // Show loading
                }
                is Resource.Success -> {
                    Snackbar.make(binding.root, "✅ Staging dipilih: ${resource.data}", Snackbar.LENGTH_SHORT).show()
                    pendingStagingDo?.let { deliveryOrder ->
                        // Navigate to scan after staging assigned
                        val intent = Intent(this, ScanActivity::class.java).apply {
                            putExtra("EXTRA_DO_ID", deliveryOrder.dohId.toString())
                            putExtra("EXTRA_GUDANG_CODE", deliveryOrder.doDetCodeGudang) // Pass gudang code untuk filter scan
                        }
                        startActivity(intent)
                    }
                    pendingStagingDo = null
                    stagingViewModel.clearAssignmentResult()
                    // Refresh DO list to update staging info
                    viewModel.refreshDeliveryOrders()
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, "Gagal assign staging: ${resource.message}", Snackbar.LENGTH_LONG).show()
                    pendingStagingDo = null
                    stagingViewModel.clearAssignmentResult()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) { binding.swipeRefresh.isRefreshing = isLoading }

    private fun showEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) { Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show() }

    /**
     * Update statistics cards with actual data counts
     */
    private fun updateStatistics(deliveryOrders: List<DeliveryOrder>) {
        // Total DO count (all delivery orders in the current date filter)
        val totalCount = deliveryOrders.size

        // Scanning count - DOs with "scanning", "arrival", or "berlangsung" status
        val scanningCount = deliveryOrders.count { do_ ->
            val status = do_.dohStatus?.lowercase()
            status in listOf("arrival", "scanning", "berlangsung")
        }

        // Completed count - DOs with completed statuses
        val completedCount = deliveryOrders.count { do_ ->
            val status = do_.dohStatus?.lowercase()
            status in listOf("selesai", "scan_completed", "completed", "approved_staff", "approved_manager", "approved")
        }

        // Update UI
        binding.tvStatTotal.text = totalCount.toString()
        binding.tvStatScanning.text = scanningCount.toString()
        binding.tvStatCompleted.text = completedCount.toString()
    }

    private fun navigateToScan(deliveryOrder: DeliveryOrder) {
        // Cek apakah DO sudah completed
        val status = deliveryOrder.dohStatus?.lowercase()
        if (status == "selesai" || status == "scan_completed" || status == "completed") {
            // Tampilkan dialog informatif untuk DO yang sudah selesai
            AlertDialog.Builder(this)
                .setTitle("🔒 Scan Telah Selesai")
                .setMessage(
                    "Delivery Order: ${deliveryOrder.dohNodo}\n" +
                            "Status: ${deliveryOrder.getStatusDisplayText()}\n\n" +
                            "DO ini sudah diselesaikan dan tidak dapat dilakukan scan ulang.\n\n" +
                            "Total Scan: ${deliveryOrder.scannedItems} item\n" +
                            "Qty DO: ${deliveryOrder.qtyDo} item"
                )
                .setPositiveButton("OK", null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show()
            return
        }

        // Cek apakah DO sudah punya staging
        if (!deliveryOrder.hasStaging) {
            // Belum ada staging, tampilkan dialog pilih staging
            showStagingRequiredDialog(deliveryOrder)
            return
        }

        // DO sudah punya staging, langsung buka ScanActivity
        android.util.Log.d("MainActivity", "🏭 Opening ScanActivity with gudangCode: ${deliveryOrder.doDetCodeGudang ?: "NULL"}")
        val intent = Intent(this, ScanActivity::class.java).apply {
            putExtra("EXTRA_DO_ID", deliveryOrder.dohId.toString())
            putExtra("EXTRA_GUDANG_CODE", deliveryOrder.doDetCodeGudang) // Pass gudang code untuk filter scan
        }
        startActivity(intent)
    }

    /**
     * Show dialog informing user that staging must be selected first
     */
    private fun showStagingRequiredDialog(deliveryOrder: DeliveryOrder) {
        AlertDialog.Builder(this)
            .setTitle("📍 Pilih Staging Area")
            .setMessage(
                "Delivery Order: ${deliveryOrder.dohNodo}\n\n" +
                        "Sebelum melakukan scan, Anda harus memilih staging area terlebih dahulu.\n\n" +
                        "Pilih staging area sekarang?"
            )
            .setIcon(android.R.drawable.ic_menu_mylocation)
            .setPositiveButton("Pilih Staging") { _, _ ->
                pendingStagingDo = deliveryOrder
                stagingViewModel.loadStagingAreas(branchCode)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Show staging area selection dialog
     */
    private fun showStagingSelectionDialog(deliveryOrder: DeliveryOrder) {
        if (availableStagingAreas.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Tidak Ada Staging Tersedia")
                .setMessage(
                    "Semua staging area sedang penuh atau tidak tersedia.\n\n" +
                            "Silakan hubungi supervisor untuk membebaskan staging area."
                )
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("OK") { _, _ ->
                    pendingStagingDo = null
                }
                .show()
            return
        }

        val stagingOptions = availableStagingAreas.map { staging ->
            "${staging.stageCode} - ${staging.stageName}\n" +
                    "Zone: ${staging.zone ?: "-"} | Kapasitas: ${staging.currentOccupancy}/${staging.maxCapacity}"
        }.toTypedArray()

        var selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle("📍 Pilih Staging Area")
            .setIcon(android.R.drawable.ic_menu_mylocation)
            .setSingleChoiceItems(stagingOptions, 0) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Pilih") { _, _ ->
                val selectedStaging = availableStagingAreas[selectedIndex]
                assignStagingAndNavigate(deliveryOrder, selectedStaging)
            }
            .setNegativeButton("Batal") { _, _ ->
                pendingStagingDo = null
            }
            .show()
    }

    /**
     * Assign staging area to DO and then navigate to scan
     */
    private fun assignStagingAndNavigate(deliveryOrder: DeliveryOrder, staging: StagingArea) {
        lifecycleScope.launch {
            val userId = sessionManager.getUserId().first() ?: 0
            // Use dohId as String directly (must match API expectation)
            val doId = deliveryOrder.dohId.toString()
            stagingViewModel.assignDeliveryOrder(
                deliveryOrderId = doId,
                stagingAreaId = staging.id,
                userId = userId
            )
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // Agar jika Sidebar terbuka, tombol back akan menutup sidebar dulu bukan keluar aplikasi
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (branchCode.isNotEmpty()) viewModel.refreshDeliveryOrders()
        // Re-check app status on resume
        checkAppStatusOnStart()
    }

    // ========== APP STATUS & OFFLINE MODE ==========

    /**
     * Setup observers for network status and app status
     */
    private fun setupAppStatusObservers() {
        // Observe network status
        lifecycleScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                updateOfflineBanner(!isOnline)
            }
        }

        // Observe app status changes
        lifecycleScope.launch {
            appStatusManager.appStatus.collect { status ->
                updateAppStatusBanners(status)
            }
        }
    }

    /**
     * Setup update button click handler
     */
    private fun setupUpdateButton() {
        binding.btnUpdate.setOnClickListener {
            // Open update dialog when button clicked
            showUpdateDialog(mustUpdate = appStatusManager.statusDetails.value?.mustUpdate ?: false)
        }
    }

    /**
     * Setup notification button - show notifications/announcements
     */
    private fun setupNotificationButton() {
        binding.btnNotification.setOnClickListener {
            showNotificationDialog()
        }
    }

    /**
     * Setup navigation header buttons (Profile & Logout)
     */
    private fun setupNavigationHeaderButtons() {
        val headerView = binding.navView.getHeaderView(0)

        // Profile Button (Edit Profile)
        val btnEditProfile = headerView.findViewById<ImageButton>(R.id.btnEditProfile)
        btnEditProfile?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Logout Button
        val btnLogout = headerView.findViewById<ImageButton>(R.id.btnLogout)
        btnLogout?.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    /**
     * Show notification/announcement dialog
     */
    private fun showNotificationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("📢 Notifikasi & Pengumuman")

        val notificationList = listOf(
            "📦 Sistem Tallysheet" to "Gunakan menu ini untuk scan barcode pada proses penerimaan barang",
            "✅ Approval Status" to "DO yang sudah scan complete akan otomatis masuk ke history dan menunggu approval",
            "🔄 Sinkronisasi" to "Data akan otomatis sync ketika online. Pastikan koneksi internet stabil",
            "📊 Laporan" to "Akses menu History untuk melihat riwayat scan dan status approval",
            "💡 Tips" to "Gunakan filter tanggal untuk mempermudah pencarian DO spesifik"
        )

        val message = StringBuilder()
        message.append("Informasi Sistem:\n\n")
        notificationList.forEachIndexed { index, (title, desc) ->
            message.append("${index + 1}. $title\n")
            message.append("   $desc\n\n")
        }

        builder.setMessage(message.toString())
        builder.setPositiveButton("Mengerti") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    /**
     * Show settings information dialog
     */
    private fun showSettingsInfoDialog() {
        // Navigate to SettingsActivity
        startActivity(Intent(this, SettingActivity::class.java))
    }

    /**
     * Check app status on start
     */
    private fun checkAppStatusOnStart() {
        lifecycleScope.launch {
            // Check app status
            val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode
            val status = appStatusManager.checkAppStatus(currentVersionCode)

            when (status) {
                AppStatus.UPDATE_REQUIRED -> {
                    // WAJIB UPDATE - tidak ada tombol Nanti
                    showUpdateDialog(mustUpdate = true)
                }
                AppStatus.UPDATE_AVAILABLE -> {
                    // UPDATE OPSIONAL - ada tombol Nanti
                    showUpdateDialog(mustUpdate = false)
                }
                AppStatus.MAINTENANCE -> {
                    // Banner already shown via observer
                    // Sync master products for offline mode
                    syncMasterProductsIfNeeded()
                }
                AppStatus.OK -> {
                    // Normal mode - sync products if needed
                    syncMasterProductsIfNeeded()
                    // Check for scheduled maintenance (pengumuman sebelum maintenance)
                    if (appStatusManager.hasScheduledMaintenance()) {
                        showScheduledMaintenanceAlert()
                    }
                    // Schedule approval notification worker
                    scheduleApprovalNotificationWorker()
                    // Schedule new DO notification worker
                    scheduleNewDONotificationWorker()
                }
            }
        }
    }

    /**
     * Schedule background worker for approval notification checking
     */
    private fun scheduleApprovalNotificationWorker() {
        lifecycleScope.launch {
            val isEnabled = settingsManager.isApprovalNotificationEnabled().first()
            if (isEnabled) {
                val interval = settingsManager.getNotificationCheckInterval().first().toLong()
                com.example.handheldapp.worker.ApprovalNotificationWorker.schedule(
                    context = this@MainActivity,
                    intervalMinutes = interval
                )
                android.util.Log.d("MainActivity", "✅ Approval notification worker scheduled every $interval minutes")
            }
        }
    }

    /**
     * Schedule background worker for new DO notification checking
     */
    private fun scheduleNewDONotificationWorker() {
        lifecycleScope.launch {
            val isEnabled = settingsManager.isDoStatusNotificationEnabled().first()
            if (isEnabled) {
                val interval = settingsManager.getNotificationCheckInterval().first().toLong()
                com.example.handheldapp.worker.NewDONotificationWorker.schedule(
                    context = this@MainActivity,
                    intervalMinutes = interval
                )
                android.util.Log.d("MainActivity", "✅ New DO notification worker scheduled every $interval minutes")
            }
        }
    }

    /**
     * Show scheduled maintenance alert (pengumuman sebelum maintenance dimulai)
     */
    private fun showScheduledMaintenanceAlert() {
        val info = appStatusManager.getScheduledMaintenanceInfo() ?: return

        val typeEmoji = when (info.maintenanceType) {
            "critical" -> "🔴"
            "major" -> "🟠"
            else -> "🟡"
        }

        val typeText = when (info.maintenanceType) {
            "critical" -> "Maintenance Penting"
            "major" -> "Maintenance Besar"
            else -> "Maintenance Ringan"
        }

        val scheduleText = buildString {
            append("📅 ${info.startFormatted}")
            if (!info.endFormatted.isNullOrEmpty()) {
                append(" - ${info.endFormatted}")
            }
            if (!info.duration.isNullOrEmpty()) {
                append("\n⏱️ Durasi: ${info.duration}")
            }
            append("\n\n⏳ Dimulai ${info.timeUntilStart}")
        }

        val message = buildString {
            append(info.message ?: "Maintenance dijadwalkan.")
            append("\n\n")
            append(scheduleText)
            append("\n\n")
            append("Pastikan pekerjaan Anda tersimpan sebelum waktu maintenance dimulai.")
        }

        AlertDialog.Builder(this)
            .setTitle("$typeEmoji $typeText Dijadwalkan")
            .setMessage(message)
            .setPositiveButton("Mengerti") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    /**
     * Sync master products cache if needed (every 24 hours)
     */
    private fun syncMasterProductsIfNeeded() {
        lifecycleScope.launch {
            // Gunakan gudangCode jika tersedia, fallback ke branchCode
            val gudangCode = sessionManager.getGudangCode().first()
                ?: sessionManager.getBranchCode().first()
                ?: return@launch

            when (val result = productSyncRepository.syncMasterProducts(gudangCode)) {
                is ProductSyncRepository.SyncResult.Success -> {
                    android.util.Log.d("MainActivity", "✅ Product sync success: ${result.count} products")
                }
                is ProductSyncRepository.SyncResult.AlreadySynced -> {
                    android.util.Log.d("MainActivity", "⏳ Products already synced recently")
                }
                is ProductSyncRepository.SyncResult.Empty -> {
                    android.util.Log.d("MainActivity", "📭 No products to sync")
                }
                is ProductSyncRepository.SyncResult.Error -> {
                    android.util.Log.e("MainActivity", "❌ Product sync error: ${result.message}")
                }
            }
        }
    }

    /**
     * Update banners based on app status
     */
    private fun updateAppStatusBanners(status: AppStatus) {
        runOnUiThread {
            when (status) {
                AppStatus.MAINTENANCE -> {
                    binding.maintenanceBanner.visibility = View.VISIBLE
                    binding.updateBanner.visibility = View.GONE
                    val message = appStatusManager.getMaintenanceMessage()
                    if (!message.isNullOrEmpty()) {
                        binding.tvMaintenanceMessage.text = message
                    }
                }
                AppStatus.UPDATE_REQUIRED -> {
                    binding.maintenanceBanner.visibility = View.GONE
                    binding.updateBanner.visibility = View.VISIBLE
                    binding.tvUpdateMessage.text = "Update wajib tersedia. Silakan update sekarang."
                }
                AppStatus.UPDATE_AVAILABLE -> {
                    // Update opsional - tampilkan banner tapi tidak blocking
                    binding.maintenanceBanner.visibility = View.GONE
                    binding.updateBanner.visibility = View.VISIBLE
                    binding.tvUpdateMessage.text = "Versi baru tersedia. Ketuk untuk update."
                }
                AppStatus.OK -> {
                    binding.maintenanceBanner.visibility = View.GONE
                    binding.updateBanner.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Update offline banner
     */
    private fun updateOfflineBanner(isOffline: Boolean) {
        runOnUiThread {
            binding.offlineBanner.visibility = if (isOffline) View.VISIBLE else View.GONE
        }
    }

    /**
     * Show force update dialog (blocking)
     * Show update dialog
     * @param mustUpdate true = WAJIB UPDATE (tidak ada tombol Nanti), false = OPSIONAL (ada tombol Nanti)
     */
    private fun showUpdateDialog(mustUpdate: Boolean) {
        val updateUrl = appStatusManager.getUpdateUrl()
        val latestVersion = appStatusManager.statusDetails.value?.latestVersion ?: "latest"
        val hasDirectDownload = !updateUrl.isNullOrEmpty() &&
                (updateUrl.endsWith(".apk") || updateUrl.contains("/download/"))

        val message = appStatusManager.getReleaseNotes()
            ?: if (mustUpdate) {
                "Versi aplikasi Anda sudah tidak didukung.\n\nAnda harus mengupdate aplikasi ke versi terbaru untuk melanjutkan."
            } else {
                "Versi baru aplikasi tersedia.\n\nAnda dapat mengupdate sekarang atau nanti."
            }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (mustUpdate) "⚠️ Update Wajib" else "🔄 Update Tersedia")
            .setMessage(message)
            .setCancelable(false)
            .setIcon(android.R.drawable.ic_dialog_alert)

        if (hasDirectDownload) {
            builder.setPositiveButton("Download & Install") { _, _ ->
                startApkDownloadFromMain(updateUrl!!, latestVersion)
            }
        } else {
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                Snackbar.make(
                    binding.root,
                    "Silakan download APK dari admin",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        // Only show "Nanti" button if NOT must update
        if (!mustUpdate) {
            builder.setNegativeButton("Nanti") { dialog, _ ->
                dialog.dismiss()
                Snackbar.make(
                    binding.root,
                    "Update tersedia. Anda dapat mengupdate kapan saja.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        builder.show()
    }

    /**
     * Start APK download with progress dialog (from MainActivity)
     */
    private fun startApkDownloadFromMain(downloadUrl: String, version: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Downloading Update v$version")
            .setMessage("Memulai download...")
            .setCancelable(false)
            .setNegativeButton("Batal") { dialog, _ ->
                appUpdateManager.cancelDownload()
                dialog.dismiss()
            }
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            appUpdateManager.downloadApk(downloadUrl, version).collect { progress ->
                when (progress.status) {
                    AppUpdateManager.DownloadStatus.PENDING -> {
                        progressDialog.setMessage("Menunggu download dimulai...")
                    }
                    AppUpdateManager.DownloadStatus.RUNNING -> {
                        val downloaded = appUpdateManager.formatBytes(progress.downloadedBytes)
                        val total = appUpdateManager.formatBytes(progress.totalBytes)
                        progressDialog.setMessage(
                            "Downloading... ${progress.progress}%\n$downloaded / $total"
                        )
                    }
                    AppUpdateManager.DownloadStatus.PAUSED -> {
                        progressDialog.setMessage("Download dijeda...")
                    }
                    AppUpdateManager.DownloadStatus.SUCCESSFUL -> {
                        progressDialog.dismiss()
                        val installed = appUpdateManager.installApk(version)
                        if (!installed) {
                            Snackbar.make(
                                binding.root,
                                "Gagal install APK. Coba install manual dari folder Download.",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                    AppUpdateManager.DownloadStatus.FAILED -> {
                        progressDialog.dismiss()
                        Snackbar.make(
                            binding.root,
                            "Download gagal: ${progress.errorMessage}",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    AppUpdateManager.DownloadStatus.CANCELLED -> {
                        progressDialog.dismiss()
                        Snackbar.make(
                            binding.root,
                            "Download dibatalkan",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}