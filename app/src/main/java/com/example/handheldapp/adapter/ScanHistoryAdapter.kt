package com.example.handheldapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.handheldapp.data.model.ScanItem
import com.example.handheldapp.databinding.ItemScanHistoryBinding

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

        fun bind(scanItem: ScanItem, number: Int) {
            binding.apply {
                // 1. Set Kode SKU
                tvSku.text = scanItem.skuOrder

                // 2. Set Nama Produk
                tvProductName.text = scanItem.productName ?: "Produk Tidak Dikenal"

                // 3. Set Qty Karton
                tvQtyKarton.text = (scanItem.qtyKarton ?: 0).toString()

                // 4. Set Qty PCS
                tvQtyPcs.text = (scanItem.qtyPcs ?: 0).toString()

                // 5. Set Waktu Scan
                tvTime.text = scanItem.createdAt?.takeLast(8) ?: "--:--:--"
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