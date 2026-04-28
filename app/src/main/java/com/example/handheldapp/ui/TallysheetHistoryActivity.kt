package com.example.handheldapp.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handheldapp.adapter.DeliveryOrderAdapter
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.databinding.ActivityTallysheetHistoryBinding
import com.example.handheldapp.ui.base.BaseActivity
import com.example.handheldapp.utils.Resource
import com.example.handheldapp.viewmodel.DeliveryOrderViewModel
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

/**
 * Activity untuk menampilkan History Tallysheet
 * Hanya menampilkan DO yang sudah selesai (completed/approved)
 */
@AndroidEntryPoint
class TallysheetHistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityTallysheetHistoryBinding
    private val viewModel: DeliveryOrderViewModel by viewModels()

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var adapter: DeliveryOrderAdapter
    private var branchCode: String = ""
    private var companyCode: String = ""

    // Date filter
    private var selectedDate: Calendar? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

    // Search
    private var searchJob: Job? = null
    private var allDeliveryOrders: List<DeliveryOrder> = emptyList()
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTallysheetHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupDateFilter()
        setupSearch()
        setupSwipeRefresh()
        setupObservers()
        loadData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "History Tallysheet"
            subtitle = "DO yang sudah selesai"
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = DeliveryOrderAdapter { deliveryOrder ->
            showDetailDialog(deliveryOrder)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupDateFilter() {
        binding.btnDateFilter.text = "📅 Semua Tanggal"

        binding.btnDateFilter.setOnClickListener {
            showDatePicker()
        }

        binding.btnClearDateFilter.setOnClickListener {
            selectedDate = null
            binding.btnDateFilter.text = "📅 Semua Tanggal"
            binding.btnClearDateFilter.visibility = View.GONE
            viewModel.setDateFilter(null)
            viewModel.refreshDeliveryOrders()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    currentSearchQuery = s?.toString()?.trim() ?: ""
                    applyLocalFilter()
                }
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentSearchQuery = binding.etSearch.text?.toString()?.trim() ?: ""
                applyLocalFilter()
                binding.etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshDeliveryOrders()
        }
    }

    private fun setupObservers() {
        viewModel.deliveryOrders.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> binding.swipeRefresh.isRefreshing = true
                is Resource.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    allDeliveryOrders = resource.data ?: emptyList()
                    applyLocalFilter()
                }
                is Resource.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    Snackbar.make(binding.root, resource.message ?: "Gagal memuat data", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            branchCode = sessionManager.getBranchCode().first() ?: ""
            companyCode = sessionManager.getCompanyCode().first() ?: ""

            if (branchCode.isEmpty()) {
                finish()
                return@launch
            }

            // Load DO yang sudah selesai (history) - status: scan_completed atau completed
            viewModel.loadDeliveryOrders(branchCode, companyCode, "history")
        }
    }

    private fun showDatePicker() {
        val calendar = selectedDate ?: Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            selectedDate = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
            }
            binding.btnDateFilter.text = "📅 ${displayDateFormat.format(selectedDate!!.time)}"
            binding.btnClearDateFilter.visibility = View.VISIBLE

            val dateStr = dateFormat.format(selectedDate!!.time)
            viewModel.setDateFilter(dateStr)
            viewModel.refreshDeliveryOrders()
        }, year, month, day).show()
    }

    private fun applyLocalFilter() {
        if (allDeliveryOrders.isEmpty()) {
            showEmptyState(true)
            return
        }

        val query = currentSearchQuery.lowercase()

        val filtered = if (query.isEmpty()) {
            allDeliveryOrders
        } else {
            allDeliveryOrders.filter { do_ ->
                do_.dohNodo?.lowercase()?.contains(query) == true ||
                        do_.dohNoSj?.lowercase()?.contains(query) == true ||
                        do_.dohSupplier?.lowercase()?.contains(query) == true ||
                        do_.stagingCode?.lowercase()?.contains(query) == true
            }
        }

        adapter.collapseAll()
        adapter.submitList(filtered)
        showEmptyState(filtered.isEmpty())

        supportActionBar?.subtitle = if (query.isNotEmpty()) {
            "${filtered.size} hasil ditemukan"
        } else {
            "DO yang sudah selesai (${filtered.size})"
        }
    }

    private fun showEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * Show detail dialog for completed DO (read-only view)
     */
    private fun showDetailDialog(deliveryOrder: DeliveryOrder) {
        AlertDialog.Builder(this)
            .setTitle("📋 Detail Tallysheet")
            .setMessage(
                "DO Number: ${deliveryOrder.dohNodo}\n" +
                        "Surat Jalan: ${deliveryOrder.dohNoSj ?: "-"}\n" +
                        "Supplier: ${deliveryOrder.dohSupplier ?: "-"}\n" +
                        "Staging: ${deliveryOrder.stagingCode ?: "-"}\n\n" +
                        "Status: ${deliveryOrder.getStatusDisplayText()}\n\n" +
                        "═══════════════════\n" +
                        "📦 Target: ${deliveryOrder.qtyKartonTarget} Ktn, ${deliveryOrder.qtyPcsTarget} PCS\n" +
                        "✅ Scan: ${deliveryOrder.qtyKartonScanned} Ktn, ${deliveryOrder.qtyPcsScanned} PCS\n" +
                        "📊 Selisih: ${deliveryOrder.selisihKarton} Ktn, ${deliveryOrder.selisihPcs} PCS\n" +
                        "═══════════════════\n\n" +
                        "Total SKU: ${deliveryOrder.items?.size ?: 0}"
            )
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_menu_info_details)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (branchCode.isNotEmpty()) {
            viewModel.refreshDeliveryOrders()
        }
    }
}
