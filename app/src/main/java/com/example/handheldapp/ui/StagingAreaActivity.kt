package com.example.handheldapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handheldapp.adapter.StagingAreaAdapter
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.databinding.ActivityStagingAreaBinding
import com.example.handheldapp.ui.ScanActivity
import com.example.handheldapp.utils.Resource
import com.example.handheldapp.viewmodel.StagingAreaViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StagingAreaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStagingAreaBinding
    private val viewModel: StagingAreaViewModel by viewModels()

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var adapter: StagingAreaAdapter
    private var deliveryOrder: DeliveryOrder? = null
    private var branchCode: String = ""
    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStagingAreaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadUserData()
        setupRecyclerView()
        setupSwipeRefresh()
        setupObservers()
        loadDeliveryOrderInfo()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            branchCode = sessionManager.getBranchCode().first() ?: ""
            userId = sessionManager.getUserId().first() ?: 0
        }
    }

    private fun setupRecyclerView() {
        adapter = StagingAreaAdapter { stagingArea -> onStagingAreaSelected(stagingArea) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshStagingAreas()
        }
    }

    private fun setupObservers() {
        viewModel.stagingAreas.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    val data = resource.data ?: emptyList()
                    adapter.submitList(data)
                    showEmptyState(data.isEmpty())
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Gagal memuat staging areas")
                }
            }
        }

        viewModel.assignmentResult.observe(this) { resource ->
            if (resource == null) return@observe

            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    val message = resource.data ?: "Berhasil assign ke staging area"
                    showSuccessAndNavigate(message)
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Gagal assign staging area")
                }
            }
        }
    }

    private fun loadDeliveryOrderInfo() {
        deliveryOrder = intent.getParcelableExtra("EXTRA_DELIVERY_ORDER")

        deliveryOrder?.let { deliveryOrderData ->
            binding.tvDoNumber.text = deliveryOrderData.dohNodo ?: "-"
            binding.tvSupplier.text = deliveryOrderData.dohSupplier ?: "-"
            binding.tvTotalItems.text = "${deliveryOrderData.qtyDo} pcs"

            // Load staging areas for this branch
            viewModel.loadStagingAreas(branchCode)
        } ?: run {
            showError("Data delivery order tidak ditemukan")
            finish()
        }
    }

    private fun onStagingAreaSelected(stagingArea: StagingArea) {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Staging Area")
            .setMessage(
                "Assign DO ${deliveryOrder?.dohNodo} ke:\n\n" +
                        "${stagingArea.stageCode} - ${stagingArea.stageName}\n" +
                        "Zone: ${stagingArea.zone}\n" +
                        "Kapasitas: ${stagingArea.getCapacityText()}\n\n" +
                        "Lanjutkan?"
            )
            .setPositiveButton("Ya, Assign") { _, _ ->
                assignToStagingArea(stagingArea)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun assignToStagingArea(stagingArea: StagingArea) {
        deliveryOrder?.let { deliveryOrderData ->
            viewModel.assignDeliveryOrder(
                deliveryOrderId = deliveryOrderData.dohId,
                stagingAreaId = stagingArea.id,
                userId = userId
            )
        }
    }

    private fun showSuccessAndNavigate(message: String) {
        Snackbar.make(binding.root, "✓ Assigned: $message", Snackbar.LENGTH_LONG).show()

        // Navigate to ScanActivity
        deliveryOrder?.let { deliveryOrderData ->
            val intent = Intent(this, ScanActivity::class.java).apply {
                putExtra("EXTRA_DO_ID", deliveryOrderData.dohId.toString())
                putExtra("EXTRA_GUDANG_CODE", deliveryOrderData.doDetCodeGudang) // Pass gudang code untuk filter scan
            }
            startActivity(intent)
            finish()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.swipeRefresh.isRefreshing = isLoading
    }

    private fun showEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.clearAssignmentResult()
    }
}
