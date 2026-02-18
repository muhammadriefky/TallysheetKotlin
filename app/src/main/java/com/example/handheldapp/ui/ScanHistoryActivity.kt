package com.example.handheldapp.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.handheldapp.R
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.databinding.ActivityScanHistoryBinding
import com.google.android.material.card.MaterialCardView

class ScanHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanHistoryBinding
    private var doId: String = ""
    private var doNumber: String = ""
    private var items: ArrayList<DeliveryOrder.DoItem>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Get data dari intent
        doId = intent.getStringExtra("EXTRA_DO_ID") ?: ""
        doNumber = intent.getStringExtra("EXTRA_DO_NUMBER") ?: ""
        items = intent.getParcelableArrayListExtra("EXTRA_DO_ITEMS")

        supportActionBar?.title = "Detail Scan"
        supportActionBar?.subtitle = doNumber

        displayItems()
    }

    private fun displayItems() {
        if (items.isNullOrEmpty()) {
            binding.layoutContent.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "Tidak ada data scan untuk DO ini"
            return
        }

        binding.layoutContent.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        // Pisahkan items: SKU di DO vs SKU tambahan
        val skuInDo = items!!.filter { it.inDo }.sortedBy { it.sku }
        val skuNotInDo = items!!.filter { !it.inDo }.sortedBy { it.sku }

        // === SUMMARY CARD ===
        displaySummary(skuInDo, skuNotInDo)

        // === SKU DI DO ===
        if (skuInDo.isNotEmpty()) {
            binding.cardSkuDo.visibility = View.VISIBLE
            binding.tvSkuDoCount.text = "${skuInDo.size} SKU"
            binding.rvSkuDo.layoutManager = LinearLayoutManager(this)
            binding.rvSkuDo.adapter = SkuInDoAdapter(skuInDo)
        } else {
            binding.cardSkuDo.visibility = View.GONE
        }

        // === SKU TAMBAHAN (TIDAK ADA DI DO) ===
        if (skuNotInDo.isNotEmpty()) {
            binding.cardSkuTambahan.visibility = View.VISIBLE
            binding.tvSkuTambahanCount.text = "${skuNotInDo.size} SKU"
            binding.rvSkuTambahan.layoutManager = LinearLayoutManager(this)
            binding.rvSkuTambahan.adapter = SkuTambahanAdapter(skuNotInDo)
        } else {
            binding.cardSkuTambahan.visibility = View.GONE
        }
    }

    private fun displaySummary(skuInDo: List<DeliveryOrder.DoItem>, skuNotInDo: List<DeliveryOrder.DoItem>) {
        // Hitung statistik
        val totalSkuDo = skuInDo.size
        val skuSelesai = skuInDo.count { item ->
            val scanned = if (item.unit.lowercase() == "karton") item.qtyKartonScanned else item.qtyPcsScanned
            scanned >= item.qtyTarget
        }
        val skuKurang = skuInDo.count { item ->
            val scanned = if (item.unit.lowercase() == "karton") item.qtyKartonScanned else item.qtyPcsScanned
            scanned in 1 until item.qtyTarget
        }
        val skuBelum = skuInDo.count { item ->
            item.qtyKartonScanned == 0 && item.qtyPcsScanned == 0
        }
        val skuLebih = skuInDo.count { item ->
            val scanned = if (item.unit.lowercase() == "karton") item.qtyKartonScanned else item.qtyPcsScanned
            scanned > item.qtyTarget
        }

        binding.tvSummarySelesai.text = "$skuSelesai"
        binding.tvSummaryKurang.text = "$skuKurang"
        binding.tvSummaryBelum.text = "$skuBelum"
        binding.tvSummaryLebih.text = "$skuLebih"
        binding.tvSummaryTambahan.text = "${skuNotInDo.size}"

        // Update subtitle
        supportActionBar?.subtitle = "$doNumber • $totalSkuDo SKU di DO"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // =============================================
    // ADAPTER: SKU yang ada di DO
    // =============================================
    private class SkuInDoAdapter(
        private val items: List<DeliveryOrder.DoItem>
    ) : RecyclerView.Adapter<SkuInDoAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scan_history_detail, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvSku: TextView = view.findViewById(R.id.tvSku)
            private val tvTarget: TextView = view.findViewById(R.id.tvTarget)
            private val tvScanned: TextView = view.findViewById(R.id.tvScanned)
            private val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            private val cardView: MaterialCardView = view.findViewById(R.id.cardView)

            fun bind(item: DeliveryOrder.DoItem) {
                tvSku.text = item.sku

                val unitTarget = item.unit.uppercase()
                tvTarget.text = "Target: ${item.qtyTarget} $unitTarget"

                // Hasil scan
                val scanParts = mutableListOf<String>()
                if (item.qtyKartonScanned > 0) scanParts.add("${item.qtyKartonScanned} KRT")
                if (item.qtyPcsScanned > 0) scanParts.add("${item.qtyPcsScanned} PCS")

                tvScanned.text = if (scanParts.isNotEmpty()) {
                    "Scan: ${scanParts.joinToString(" + ")}"
                } else {
                    "Scan: -"
                }

                // Status comparison
                val totalScanned = if (item.unit.lowercase() == "karton") {
                    item.qtyKartonScanned
                } else {
                    item.qtyPcsScanned
                }

                val selisih = totalScanned - item.qtyTarget

                when {
                    totalScanned == 0 -> {
                        tvStatus.text = "⚪ Belum"
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        cardView.strokeColor = android.graphics.Color.parseColor("#E0E0E0")
                    }
                    selisih < 0 -> {
                        tvStatus.text = "🔵 Kurang ${-selisih}"
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#2196F3"))
                        cardView.strokeColor = android.graphics.Color.parseColor("#2196F3")
                    }
                    selisih == 0 -> {
                        tvStatus.text = "✅ Sesuai"
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        cardView.strokeColor = android.graphics.Color.parseColor("#4CAF50")
                    }
                    else -> {
                        tvStatus.text = "🟠 Lebih +$selisih"
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                        cardView.strokeColor = android.graphics.Color.parseColor("#FF9800")
                    }
                }
                cardView.strokeWidth = 3
            }
        }
    }

    // =============================================
    // ADAPTER: SKU tambahan (tidak ada di DO)
    // =============================================
    private class SkuTambahanAdapter(
        private val items: List<DeliveryOrder.DoItem>
    ) : RecyclerView.Adapter<SkuTambahanAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scan_history_detail, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvSku: TextView = view.findViewById(R.id.tvSku)
            private val tvTarget: TextView = view.findViewById(R.id.tvTarget)
            private val tvScanned: TextView = view.findViewById(R.id.tvScanned)
            private val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            private val cardView: MaterialCardView = view.findViewById(R.id.cardView)

            fun bind(item: DeliveryOrder.DoItem) {
                tvSku.text = item.sku
                tvTarget.text = "Target DO: -"

                // Hasil scan
                val scanParts = mutableListOf<String>()
                if (item.qtyKartonScanned > 0) scanParts.add("${item.qtyKartonScanned} KRT")
                if (item.qtyPcsScanned > 0) scanParts.add("${item.qtyPcsScanned} PCS")

                tvScanned.text = "Scan: ${scanParts.joinToString(" + ")}"

                // Status - selalu warning karena tidak ada di DO
                tvStatus.text = "⚠️ Tidak di DO"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
                cardView.strokeColor = android.graphics.Color.parseColor("#F44336")
                cardView.strokeWidth = 4
            }
        }
    }
}
