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

/**
 * Fragment untuk menampilkan RINGKASAN scan (1 baris per SKU dengan total qty)
 */
@AndroidEntryPoint
class ScanSummaryFragment : Fragment() {

    private var _binding: FragmentScanListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by activityViewModels()
    private lateinit var adapter: ScanHistoryAdapter

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
                    adapter.submitList(data)
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

    private fun showLoading(isLoading: Boolean) {
        binding.swipeRefresh.isRefreshing = isLoading
    }

    private fun showEmptyState(isEmpty: Boolean) {
        binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ScanSummaryFragment()
    }
}
