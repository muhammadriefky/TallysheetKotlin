package com.example.handheldapp.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handheldapp.R
import com.example.handheldapp.adapter.DeliveryOrderAdapter
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.databinding.ActivityMainBinding
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
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DeliveryOrderViewModel by viewModels()
    private val stagingViewModel: StagingAreaViewModel by viewModels()

    @Inject
    lateinit var sessionManager: SessionManager

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

        // 3. Load Data
        loadBranchAndUserInfo()
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
            binding.tvToolbarBranch.text = "$branchName"

            // Update Header Sidebar (Nama, Branch, & Role)
            val headerView = binding.navView.getHeaderView(0)
            headerView.findViewById<TextView>(R.id.tvNavName)?.text = userName
            headerView.findViewById<TextView>(R.id.tvNavEmail)?.text = "$branchName"

            // Update role chip (optional - bisa disesuaikan dengan role user dari session)
            val chipRole = headerView.findViewById<com.google.android.material.chip.Chip>(R.id.chipRole)
            chipRole?.text = "Warehouse Staff"

            val logout = headerView.findViewById<ImageView>(R.id.btnLogout)
            logout.setOnClickListener {
                showLogoutConfirmation()
            }

            // PENTING: Set date filter ke hari ini SEBELUM load data
            val todayDate = dateFormat.format(selectedDate.time)
            viewModel.setDateFilter(todayDate)

            // Load hanya tallysheet aktif (belum selesai)
            viewModel.filterByStatus("Berlangsung")
            viewModel.loadDeliveryOrders(branchCode, companyCode)
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
                // Sudah di halaman ini, refresh saja
                viewModel.filterByStatus("Berlangsung")
                binding.chipBerlangsung.isChecked = true
                Snackbar.make(binding.root, "Menampilkan Tallysheet Aktif", Snackbar.LENGTH_SHORT).show()
            }
            R.id.nav_tallysheet_history -> {
                // Navigate ke History Tallysheet Activity
                startActivity(Intent(this, TallysheetHistoryActivity::class.java))
            }

            // Warehouse Operations
            R.id.nav_putaway -> {
                Snackbar.make(binding.root, "Fitur Put Away/Rak segera hadir", Snackbar.LENGTH_LONG).show()
            }
            R.id.nav_stock_opname -> {
                Snackbar.make(binding.root, "Fitur Stock Opname segera hadir", Snackbar.LENGTH_LONG).show()
            }
            R.id.nav_mutasi -> {
                Snackbar.make(binding.root, "Fitur Mutasi Barang segera hadir", Snackbar.LENGTH_LONG).show()
            }
            R.id.nav_btb -> {
                Snackbar.make(binding.root, "Fitur BTB (Rusak/Reject) segera hadir", Snackbar.LENGTH_LONG).show()
            }

            // Reports
            R.id.nav_history -> {
                Snackbar.make(binding.root, "Fitur History Scan segera hadir", Snackbar.LENGTH_LONG).show()
            }
            R.id.nav_reports -> {
                Snackbar.make(binding.root, "Fitur Laporan Gudang segera hadir", Snackbar.LENGTH_LONG).show()
            }

            // Settings & Account
            R.id.nav_profile -> {
                Snackbar.make(binding.root, "Fitur Profil segera hadir", Snackbar.LENGTH_LONG).show()
            }
            R.id.nav_settings -> {
                Snackbar.make(binding.root, "Fitur Pengaturan segera hadir", Snackbar.LENGTH_LONG).show()
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

    // --- Fungsi Navigasi & UI (Tetap Sama) ---

    private fun setupRecyclerView() {
        adapter = DeliveryOrderAdapter { deliveryOrder -> navigateToScan(deliveryOrder) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFilterChips() {
        // MainActivity hanya untuk Tallysheet Aktif
        // Chip "Selesai" dihilangkan karena history punya halaman terpisah
        binding.chipSemua.isChecked = true
        binding.chipSemua.setOnClickListener {
            viewModel.filterByStatus("Berlangsung") // Tetap filter aktif saja
        }
        binding.chipBerlangsung.setOnClickListener {
            viewModel.filterByStatus("Berlangsung")
        }
        // Sembunyikan chip Selesai karena history di halaman terpisah
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
        if (allDeliveryOrders.isEmpty()) return

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
    }
}