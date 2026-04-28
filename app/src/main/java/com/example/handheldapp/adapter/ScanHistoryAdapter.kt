package com.example.handheldapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.handheldapp.data.model.ScanItem
import com.example.handheldapp.databinding.ItemScanHistoryBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * ★ UPDATED: Adapter dengan Total PCS hasil konversi
 */
class ScanHistoryAdapter : ListAdapter<ScanItem, ScanHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            // Menggunakan position + 1 agar nomor urut di list mulai dari #1
            holder.bind(item, position + 1)
        }
    }

    class ViewHolder(
        private val binding: ItemScanHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))

        fun bind(scanItem: ScanItem, number: Int) {
            binding.apply {
                // 1. Set Kode SKU
                tvSku.text = scanItem.skuOrder

                // 2. Set Nama Produk
                tvProductName.text = scanItem.productName ?: "Produk Tidak Dikenal"

                // 3. Set Qty Karton
                val qtyKarton = scanItem.qtyKarton ?: 0
                tvQtyKarton.text = qtyKarton.toString()

                // 4. Set Qty PCS
                val qtyPcs = scanItem.qtyPcs ?: 0
                tvQtyPcs.text = qtyPcs.toString()

                // 5. ★ Set Total PCS (hasil konversi)
                val totalPcs = scanItem.getDisplayTotalPcs()
                tvTotalPcs.text = numberFormat.format(totalPcs)

                // 6. Set Waktu Scan (jam saja atau full timestamp)
                val timeDisplay = if (!scanItem.scannedAt.isNullOrEmpty()) {
                    // Format: "2026-04-10 14:30:25" → ambil jam "14:30:25"
                    scanItem.scannedAt.substringAfter(" ").take(8)
                } else {
                    scanItem.createdAt?.takeLast(8) ?: "--:--:--"
                }
                tvTime.text = timeDisplay

                // 7. ★ Set Conversion Info
                val pcsPerKarton = if (scanItem.pcsPerKarton > 0) scanItem.pcsPerKarton else 1
                tvConversion.text = "1 Krt = $pcsPerKarton PCS"

                // 8. ★ Set Scanned By (User yang melakukan scan)
                tvScannedBy.text = if (!scanItem.scannedBy.isNullOrEmpty()) {
                    "📱 ${scanItem.scannedBy}"
                } else {
                    "📱 -"
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ScanItem>() {
        override fun areItemsTheSame(oldItem: ScanItem, newItem: ScanItem): Boolean {
            // Menggunakan ID unik database (Primary Key) agar animasi List lancar
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScanItem, newItem: ScanItem): Boolean {
            // Membandingkan seluruh isi objek untuk mendeteksi perubahan nilai qty
            return oldItem == newItem
        }
    }
}