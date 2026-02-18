package com.example.handheldapp.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.handheldapp.adapter.ScanHistoryPagerAdapter
import com.example.handheldapp.databinding.ActivityScanBinding
import com.example.handheldapp.utils.Resource
import com.example.handheldapp.viewmodel.ScanViewModel
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity untuk Scan Barcode dan Tally Sheet
 *
 * Flow:
 * 1. Staging dipilih di MainActivity sebelum masuk ke sini
 * 2. User scan barcode (via hardware scanner atau manual input)
 * 3. System cek barcode di API (tabel tgu_ms_product_Business kolom SKU_Barcode_pcs)
 * 4. Jika ditemukan 1 SKU: langsung tampilkan form qty
 * 5. Jika ditemukan >1 SKU: tampilkan dialog pilihan SKU
 * 6. User input qty dan submit
 * 7. Refresh history list
 */
@AndroidEntryPoint
class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var pagerAdapter: ScanHistoryPagerAdapter

    private var selectedSku: String? = null
    private var selectedProductName: String? = null
    private var selectedPcsPerKarton: Int = 1
    private var isCompleted: Boolean = false // Track apakah scan sudah selesai

    // Manual mode state (untuk barcode rusak)
    private var manualModeBarcode: String? = null

    // Debounce handler untuk input manual
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private val DEBOUNCE_DELAY = 800L // 800ms delay untuk input manual

    // Staging Area info (untuk display saja, sudah dipilih di MainActivity)
    private var currentStagingCode: String? = null
    private var currentStagingName: String? = null

    // Honeywell Scanner Configuration
    private val HONEYWELL_ACTION = "com.honeywell.action.BARCODE_DATA"

    // BroadcastReceiver untuk menangkap data dari Scanner Hardware
    private val barcodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HONEYWELL_ACTION) {
                val barcode = intent.getStringExtra("data") ?: intent.getStringExtra("barcode")
                if (!barcode.isNullOrEmpty()) {
                    vibrate()
                    handleHardwareScan(barcode.trim())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get DO ID from intent
        val doId = intent.getStringExtra("EXTRA_DO_ID") ?: run {
            Toast.makeText(this, "DO ID tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        viewModel.setDeliveryOrderId(doId)

        // Get gudang code from intent for filtering scan results
        val gudangCode = intent.getStringExtra("EXTRA_GUDANG_CODE")
        android.util.Log.d("ScanActivity", "🏭 EXTRA_GUDANG_CODE from intent: ${gudangCode ?: "NULL"}")

        // Debug toast - hapus setelah fix
        Toast.makeText(this, "Gudang filter: ${gudangCode ?: "TIDAK ADA"}", Toast.LENGTH_LONG).show()

        viewModel.setGudangCode(gudangCode)

        setupUI()
        observeViewModel()
        observeStagingInfo()

        // Cek staging assignment untuk update info di toolbar
        viewModel.checkStagingAssignment()
    }

    // Registrasi Receiver saat Activity tampil
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(HONEYWELL_ACTION)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            barcodeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    // Unregister untuk mencegah memory leak
    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(barcodeReceiver)
        } catch (e: Exception) {
            // Receiver already unregistered
        }
    }

    private fun setupUI() {
        // Setup ViewPager2 untuk TabLayout (Ringkasan + Riwayat Scan)
        pagerAdapter = ScanHistoryPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // Connect TabLayout dengan ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Ringkasan"
                1 -> "Riwayat Scan"
                else -> ""
            }
        }.attach()

        // Barcode input listener dengan DEBOUNCE untuk input manual
        // Debounce mencegah API dipanggil terlalu cepat saat user mengetik
        binding.etBarcode.addTextChangedListener { text ->
            val barcode = text.toString().trim()

            // Cancel pending debounce
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }

            if (barcode.length >= 8) { // Minimum barcode length
                // Check if not completed
                if (!isCompleted) {
                    // Gunakan debounce untuk input manual (800ms)
                    // Ini memungkinkan user selesai mengetik sebelum API dipanggil
                    debounceRunnable = Runnable {
                        checkBarcode(barcode)
                    }
                    debounceHandler.postDelayed(debounceRunnable!!, DEBOUNCE_DELAY)
                }
            }
        }

        // Submit scan button
        binding.btnSubmitScan.setOnClickListener {
            submitScan()
        }

        // Refresh button - refresh both tabs
        binding.btnRefresh.setOnClickListener {
            viewModel.loadHistory()
            viewModel.loadActivityLog()
        }

        // Toggle History Button
        binding.btnToggleHistory.setOnClickListener {
            toggleHistoryVisibility()
        }

        // FAB Menu Toggle
        binding.fabMenu.setOnClickListener {
            toggleFabMenu()
        }

        // FAB View History
        binding.fabViewHistory.setOnClickListener {
            showHistoryDialog()
            toggleFabMenu()
        }

        // FAB Complete Scan
        binding.fabCompleteScan.setOnClickListener {
            showCompleteScanDialog()
            toggleFabMenu()
        }

        // FAB Summary
        binding.fabSummary.setOnClickListener {
            showSummaryDialog()
            toggleFabMenu()
        }
    }

    private fun observeViewModel() {
        // Observe DO completion status - PENTING: cek status completion saat activity dibuka
        viewModel.isDoCompleted.observe(this) { completed ->
            if (completed && !isCompleted) {
                isCompleted = true
                disableScanUI()

                // Scroll ke atas untuk melihat completed card
                binding.scrollView.post {
                    binding.scrollView.smoothScrollTo(0, 0)
                }
            }
        }

        // Observe barcode check result
        viewModel.barcodeCheckResult.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    handleBarcodeCheckResult(resource.data)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                    clearBarcodeInput()
                }
            }
        }

        // Observe submit scan result
        viewModel.scanSubmitResult.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.btnSubmitScan.isEnabled = false
                }
                is Resource.Success -> {
                    binding.btnSubmitScan.isEnabled = true
                    Toast.makeText(this, "Scan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    clearForm()
                }
                is Resource.Error -> {
                    binding.btnSubmitScan.isEnabled = true

                    // Cek apakah error karena DO sudah completed
                    if (resource.message?.contains("sudah selesai", ignoreCase = true) == true ||
                        resource.message?.contains("completed", ignoreCase = true) == true) {
                        isCompleted = true
                        disableScanUI()

                        // Scroll ke atas untuk melihat completed card
                        binding.scrollView.post {
                            binding.scrollView.smoothScrollTo(0, 0)
                        }
                    } else {
                        Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Observe scan history (untuk summary card saja, data ditampilkan via fragments)
        viewModel.scanHistory.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE

                    // Update summary card
                    val items = resource.data ?: emptyList()
                    var totalKarton = 0
                    var totalPcs = 0
                    val totalItems = items.size

                    items.forEach { item ->
                        totalKarton += item.qtyKarton ?: 0
                        totalPcs += item.qtyPcs ?: 0
                    }

                    binding.tvTotalKarton.text = totalKarton.toString()
                    binding.tvTotalPcs.text = totalPcs.toString()
                    binding.tvTotalItems.text = totalItems.toString()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observe complete scan result
        viewModel.completeScanResult.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.fabCompleteScan.isEnabled = false
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.fabCompleteScan.isEnabled = true
                    isCompleted = true
                    disableScanUI()

                    // Hapus toast, karena sudah ada visual card
                    // Scroll ke atas untuk melihat completed card
                    binding.scrollView.post {
                        binding.scrollView.smoothScrollTo(0, 0)
                    }

                    // Vibrate untuk feedback
                    vibrate()

                    // Kembali ke activity sebelumnya setelah 2.5 detik
                    binding.root.postDelayed({
                        finish()
                    }, 2500)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.fabCompleteScan.isEnabled = true

                    // Cek apakah error karena sudah completed sebelumnya
                    if (resource.message?.contains("sudah selesai", ignoreCase = true) == true) {
                        isCompleted = true
                        disableScanUI()
                        Toast.makeText(
                            this,
                            "Scan ini sudah diselesaikan sebelumnya.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /**
     * Observe staging assignment untuk update toolbar info saja
     * Staging selection sudah dilakukan di MainActivity
     */
    private fun observeStagingInfo() {
        viewModel.stagingAssignment.observe(this) { resource ->
            when (resource) {
                is Resource.Success -> {
                    val assignment = resource.data
                    if (assignment != null) {
                        currentStagingCode = assignment.stagingAreaCode
                        currentStagingName = assignment.stagingAreaName
                        supportActionBar?.subtitle = "📍 ${assignment.stagingAreaCode} - ${assignment.stagingAreaName}"
                    }
                }
                else -> {
                    // No action needed
                }
            }
        }
    }

    /**
     * Handle result dari cek barcode
     * Jika 1 SKU: langsung set & enable form
     * Jika >1 SKU: tampilkan dialog pilihan
     * Jika tidak ditemukan: tampilkan dialog pilih SKU manual (barcode rusak)
     */
    private fun handleBarcodeCheckResult(products: List<com.example.handheldapp.data.model.ScanItem.BarcodeProduct>?) {
        if (products.isNullOrEmpty()) {
            // Barcode tidak ditemukan - simpan untuk mode manual
            val inputBarcode = binding.etBarcode.text.toString().trim()
            manualModeBarcode = inputBarcode

            // Tampilkan dialog untuk pilih SKU manual
            showManualSkuSelectionDialog(inputBarcode)
            return
        }

        if (products.size == 1) {
            // Hanya 1 SKU ditemukan, langsung set
            val product = products[0]
            selectedSku = product.sku
            selectedProductName = product.productName
            selectedPcsPerKarton = product.pcsPerKarton

            binding.tvProductInfo.text = "${product.sku} - ${product.productName}\n" +
                    "Konversi: ${product.pcsPerKarton} pcs/karton"
            binding.etQtyKarton.requestFocus()

            Toast.makeText(this, "SKU ditemukan: ${product.sku}", Toast.LENGTH_SHORT).show()
        } else {
            // Multiple SKU ditemukan, tampilkan dialog pilihan
            showSkuSelectionDialog(products)
        }
    }

    /**
     * Dialog untuk input SKU manual ketika barcode tidak ditemukan (barcode rusak)
     * User bisa search berdasarkan nama produk atau SKU
     */
    private fun showManualSkuSelectionDialog(barcode: String) {
        val inputView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val editText = android.widget.EditText(this).apply {
            hint = "Ketik SKU atau nama produk..."
            setPadding(48, 32, 48, 32)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Barcode Tidak Ditemukan")
            .setMessage("Barcode: $barcode\n\nBarcode ini tidak terdaftar di database. Silakan cari SKU secara manual untuk barang dengan barcode rusak.\n\nMasukkan SKU atau nama produk:")
            .setView(editText)
            .setPositiveButton("Cari") { dialogInterface, _ ->
                val keyword = editText.text.toString().trim()
                if (keyword.length >= 2) {
                    searchProductsForManualMode(keyword, barcode)
                } else {
                    Toast.makeText(this, "Masukkan minimal 2 karakter", Toast.LENGTH_SHORT).show()
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("Batal") { dialogInterface, _ ->
                manualModeBarcode = null
                clearBarcodeInput()
                dialogInterface.dismiss()
            }
            .setCancelable(false)
            .create()

        dialog.show()
        editText.requestFocus()
    }

    /**
     * Search products untuk mode manual dan tampilkan hasil sebagai pilihan
     */
    private fun searchProductsForManualMode(keyword: String, originalBarcode: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE

        // Panggil API search products
        viewModel.searchProducts(keyword) { products ->
            binding.progressBar.visibility = android.view.View.GONE

            if (products.isNullOrEmpty()) {
                Toast.makeText(this, "Tidak ada produk yang cocok dengan '$keyword'", Toast.LENGTH_SHORT).show()
                showManualSkuSelectionDialog(originalBarcode) // Tampilkan dialog lagi
                return@searchProducts
            }

            // Tampilkan hasil sebagai pilihan
            val skuList = products.map { "${it.sku} - ${it.productName}" }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Pilih SKU (${products.size} ditemukan)")
                .setItems(skuList) { dialog, which ->
                    val selected = products[which]
                    selectedSku = selected.sku
                    selectedProductName = selected.productName
                    selectedPcsPerKarton = selected.pcsPerKarton

                    // Simpan barcode manual yang diinput user
                    manualModeBarcode = originalBarcode

                    binding.tvProductInfo.text = "${selected.sku} - ${selected.productName}\n" +
                            "⚠️ Mode Manual - Barcode: $originalBarcode"
                    binding.etQtyKarton.requestFocus()

                    Toast.makeText(this, "SKU dipilih: ${selected.sku} (Mode Manual)", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cari Lagi") { dialog, _ ->
                    dialog.dismiss()
                    showManualSkuSelectionDialog(originalBarcode)
                }
                .setNeutralButton("Batal") { dialog, _ ->
                    manualModeBarcode = null
                    clearBarcodeInput()
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }

    /**
     * Show dialog untuk pilih SKU jika barcode ditemukan di multiple produk
     */
    private fun showSkuSelectionDialog(products: List<com.example.handheldapp.data.model.ScanItem.BarcodeProduct>) {
        val skuList = products.map { "${it.sku} - ${it.productName}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih SKU (${products.size} ditemukan)")
            .setItems(skuList) { dialog, which ->
                val selected = products[which]
                selectedSku = selected.sku
                selectedProductName = selected.productName
                selectedPcsPerKarton = selected.pcsPerKarton

                binding.tvProductInfo.text = "${selected.sku} - ${selected.productName}\n" +
                        "Konversi: ${selected.pcsPerKarton} pcs/karton"
                binding.etQtyKarton.requestFocus()

                Toast.makeText(this, "SKU dipilih: ${selected.sku}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setOnCancelListener {
                clearBarcodeInput()
            }
            .show()
    }

    private fun checkBarcode(barcode: String) {
        // Cek apakah scan sudah selesai
        if (isCompleted) {
            // Vibrate untuk feedback
            vibrate()

            // Scroll ke completed card untuk menunjukkan status
            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, 0)
            }

            // Show dialog instead of toast untuk lebih informatif
            AlertDialog.Builder(this)
                .setTitle("⚠️ Scan Telah Selesai")
                .setMessage("Delivery Order ini sudah diselesaikan.\n\nAnda tidak dapat menambahkan scan baru.\n\nSilakan kembali ke daftar DO.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    clearBarcodeInput()
                }
                .setNeutralButton("Tutup Activity") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()

            return
        }

        viewModel.checkBarcode(barcode)
    }

    private fun submitScan() {
        // Cek apakah scan sudah selesai
        if (isCompleted) {
            // Scroll ke completed card
            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, 0)
            }

            Toast.makeText(
                this,
                "⚠️ Scan sudah selesai. Tidak dapat menambah data.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val sku = selectedSku
        val qtyKartonStr = binding.etQtyKarton.text.toString()
        val qtyPcsStr = binding.etQtyPcs.text.toString()

        if (sku.isNullOrBlank()) {
            Toast.makeText(this, "Scan barcode terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        val qtyKarton = qtyKartonStr.toIntOrNull() ?: 0
        val qtyPcs = qtyPcsStr.toIntOrNull() ?: 0

        if (qtyKarton <= 0 && qtyPcs <= 0) {
            Toast.makeText(this, "Masukkan minimal salah satu qty (Karton atau PCS)", Toast.LENGTH_SHORT).show()
            return
        }

        // Submit dengan barcode (manual atau dari scan)
        // Jika mode manual, gunakan manualModeBarcode, jika tidak gunakan barcode dari input
        val barcodeToSubmit = manualModeBarcode ?: binding.etBarcode.text.toString().trim()
        viewModel.submitScan(sku, qtyKarton = qtyKarton, qtyPcs = qtyPcs, barcode = barcodeToSubmit)
    }

    private fun clearForm() {
        binding.etBarcode.text?.clear()
        binding.etQtyKarton.text?.clear()
        binding.etQtyPcs.text?.clear()
        binding.tvProductInfo.text = "Scan barcode untuk memulai"
        selectedSku = null
        selectedPcsPerKarton = 1
        manualModeBarcode = null // Clear manual mode
        binding.etBarcode.requestFocus()
    }

    private fun clearBarcodeInput() {
        binding.etBarcode.text?.clear()
        manualModeBarcode = null // Clear manual mode
        binding.etBarcode.requestFocus()
    }

    /**
     * Handle barcode dari hardware scanner
     */
    private fun handleHardwareScan(barcode: String) {
        // Check if already completed
        if (isCompleted) {
            Toast.makeText(
                this,
                "Scan sudah selesai, tidak dapat menambah data baru",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.etBarcode.setText(barcode)
        checkBarcode(barcode)
    }

    /**
     * Vibrate untuk feedback scan
     */
    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150)
        }
    }

    /**
     * Toggle show/hide history detail
     */
    private fun toggleHistoryVisibility() {
        if (binding.layoutHistoryContainer.visibility == android.view.View.VISIBLE) {
            binding.layoutHistoryContainer.visibility = android.view.View.GONE
            binding.btnToggleHistory.text = "LIHAT DETAIL"
            binding.btnToggleHistory.icon = getDrawable(android.R.drawable.arrow_down_float)
        } else {
            binding.layoutHistoryContainer.visibility = android.view.View.VISIBLE
            binding.btnToggleHistory.text = "SEMBUNYIKAN"
            binding.btnToggleHistory.icon = getDrawable(android.R.drawable.arrow_up_float)
            // Refresh history saat dibuka
            viewModel.loadHistory()
        }
    }

    /**
     * Toggle FAB Menu
     */
    private fun toggleFabMenu() {
        if (binding.fabMenuOptions.visibility == android.view.View.VISIBLE) {
            binding.fabMenuOptions.visibility = android.view.View.GONE
            binding.fabMenu.setImageResource(android.R.drawable.ic_menu_more)
        } else {
            binding.fabMenuOptions.visibility = android.view.View.VISIBLE
            binding.fabMenu.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        }
    }

    /**
     * Show History Dialog
     */
    private fun showHistoryDialog() {
        viewModel.scanHistory.value?.data?.let { items ->
            if (items.isEmpty()) {
                Toast.makeText(this, "Belum ada scan", Toast.LENGTH_SHORT).show()
                return
            }

            val historyText = StringBuilder()
            historyText.append("=== HISTORY SCAN ===\n\n")

            var totalKarton = 0
            var totalPcs = 0

            items.forEachIndexed { index, item ->
                historyText.append("${index + 1}. ${item.skuOrder}\n")
                historyText.append("   ${item.productName}\n")
                historyText.append("   Karton: ${item.qtyKarton ?: 0} | PCS: ${item.qtyPcs ?: 0}\n")
                historyText.append("   Total: ${item.qty} PCS\n\n")

                totalKarton += item.qtyKarton ?: 0
                totalPcs += item.qty
            }

            historyText.append("===================\n")
            historyText.append("TOTAL: $totalKarton KRT | $totalPcs PCS\n")
            historyText.append("JUMLAH SKU: ${items.size}")

            AlertDialog.Builder(this)
                .setTitle("📋 History Scan DO")
                .setMessage(historyText.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Refresh") { _, _ ->
                    viewModel.loadHistory()
                }
                .show()
        } ?: run {
            Toast.makeText(this, "Loading history...", Toast.LENGTH_SHORT).show()
            viewModel.loadHistory()
        }
    }

    /**
     * Show Complete Scan Dialog
     */
    private fun showCompleteScanDialog() {
        viewModel.scanHistory.value?.data?.let { items ->
            val totalKarton = items.sumOf { it.qtyKarton ?: 0 }
            val totalPcs = items.sumOf { it.qty }
            val totalItems = items.size

            val message = """
                Anda akan menyelesaikan scan untuk DO ini.
                
                Total yang telah di-scan:
                • $totalKarton Karton
                • $totalPcs PCS
                • $totalItems SKU
                
                Lanjutkan?
            """.trimIndent()

            AlertDialog.Builder(this)
                .setTitle("✅ Selesai Scan")
                .setMessage(message)
                .setPositiveButton("Ya, Selesai") { _, _ ->
                    completeScan()
                }
                .setNegativeButton("Batal", null)
                .show()
        } ?: run {
            Toast.makeText(this, "Belum ada data scan", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Show Summary Dialog
     */
    private fun showSummaryDialog() {
        viewModel.scanHistory.value?.data?.let { items ->
            if (items.isEmpty()) {
                Toast.makeText(this, "Belum ada scan", Toast.LENGTH_SHORT).show()
                return
            }

            val totalKarton = items.sumOf { it.qtyKarton ?: 0 }
            val totalPcs = items.sumOf { it.qty }
            val totalItems = items.size

            // Group by SKU untuk summary
            val groupedItems = items.groupBy { it.skuOrder }

            val summaryText = StringBuilder()
            summaryText.append("=== RINGKASAN SCAN ===\n\n")
            summaryText.append("Total Karton: $totalKarton\n")
            summaryText.append("Total PCS: $totalPcs\n")
            summaryText.append("Jumlah SKU: $totalItems\n\n")
            summaryText.append("--- Detail per SKU ---\n\n")

            groupedItems.forEach { (sku, skuItems) ->
                val skuKarton = skuItems.sumOf { it.qtyKarton ?: 0 }
                val skuPcs = skuItems.sumOf { it.qty }
                summaryText.append("$sku\n")
                summaryText.append("${skuItems.first().productName}\n")
                summaryText.append("$skuKarton KRT | $skuPcs PCS\n\n")
            }

            AlertDialog.Builder(this)
                .setTitle("📊 Ringkasan Scan")
                .setMessage(summaryText.toString())
                .setPositiveButton("OK", null)
                .show()
        } ?: run {
            Toast.makeText(this, "Loading data...", Toast.LENGTH_SHORT).show()
            viewModel.loadHistory()
        }
    }

    /**
     * Complete Scan - Mark DO as completed
     */
    private fun completeScan() {
        viewModel.completeScan()
    }

    /**
     * Disable UI setelah scan completed
     */
    private fun disableScanUI() {
        // Show completed status card
        binding.cardCompletedStatus.visibility = android.view.View.VISIBLE
        binding.cardCompletedStatus.alpha = 0f
        binding.cardCompletedStatus.animate()
            .alpha(1f)
            .setDuration(500)
            .start()

        // Fade out and disable input card
        binding.cardInput.animate()
            .alpha(0.4f)
            .setDuration(300)
            .start()

        // Disable input fields
        binding.etBarcode.isEnabled = false
        binding.etBarcode.hint = ""
        binding.etQtyKarton.isEnabled = false
        binding.etQtyPcs.isEnabled = false

        // Disable and grey out submit button
        binding.btnSubmitScan.isEnabled = false
        binding.btnSubmitScan.text = "SCAN TELAH SELESAI"
        binding.btnSubmitScan.alpha = 0.5f
        binding.btnSubmitScan.icon = null

        // Disable and grey out complete FAB
        binding.fabCompleteScan.isEnabled = false
        binding.fabCompleteScan.alpha = 0.3f

        // Hide FAB menu
        binding.fabMenuOptions.visibility = android.view.View.GONE
        binding.fabMenu.isEnabled = false
        binding.fabMenu.alpha = 0.3f

        // Update product info
        binding.tvProductInfo.text = "✅ Scan telah diselesaikan. Tidak dapat menambah data baru."
        binding.tvProductInfo.setBackgroundColor(
            android.graphics.Color.parseColor("#E8F5E9")
        )
        binding.tvProductInfo.setTextColor(
            android.graphics.Color.parseColor("#2E7D32")
        )
    }
}
