package com.example.handheldapp.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.handheldapp.adapter.ScanHistoryAdapter
import com.example.handheldapp.databinding.FragmentScanListBinding
import com.example.handheldapp.utils.Resource
import com.example.handheldapp.viewmodel.ScanViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale

/**
 * Fragment untuk menampilkan RINGKASAN scan (1 baris per SKU dengan total qty)
 * ★ UPDATED: Now shows Total PCS per SKU and Grand Total
 */
@AndroidEntryPoint
class ScanSummaryFragment : Fragment() {

    private var _binding: FragmentScanListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by activityViewModels()
    private lateinit var adapter: ScanHistoryAdapter

    private val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        observeData()
        observeTotals()
    }

    private fun setupRecyclerView() {
        adapter = ScanHistoryAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshScanHistory()
        }
    }

    private fun observeData() {
        // Observe RINGKASAN data (GROUP BY SKU)
        viewModel.scanHistory.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    val data = resource.data ?: emptyList()
                    android.util.Log.d("ScanSummary", "📊 RINGKASAN - Total items: ${data.size}")
                    // ★ Force refresh adapter dengan list baru untuk trigger DiffUtil
                    adapter.submitList(null) // Clear first
                    adapter.submitList(data.toList()) // Submit new list (copy)
                    showEmptyState(data.isEmpty())
                }
                is Resource.Error -> {
                    showLoading(false)
                    android.util.Log.e("ScanSummary", "❌ Error: ${resource.message}")
                    showEmptyState(true)
                }
            }
        }
    }

    /**
     * ★ Observe totals for grand total display
     */
    private fun observeTotals() {
        viewModel.scanTotals.observe(viewLifecycleOwner) { historyData ->
            if (historyData != null && historyData.grandTotalPcs > 0) {
                binding.layoutTotalSummary.visibility = View.VISIBLE

                // Format grand total
                binding.tvGrandTotalPcs.text = "${numberFormat.format(historyData.grandTotalPcs)} PCS"

                // Format breakdown
                binding.tvTotalBreakdown.text = "(${numberFormat.format(historyData.totalKarton)} Krt + ${numberFormat.format(historyData.totalPcsOnly)} PCS)"
            } else {
                binding.layoutTotalSummary.visibility = View.GONE
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.swipeRefresh.isRefreshing = isLoading
    }

    private fun showEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE

        // Hide totals when empty
        if (isEmpty) {
            binding.layoutTotalSummary.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ScanSummaryFragment()
    }
}
