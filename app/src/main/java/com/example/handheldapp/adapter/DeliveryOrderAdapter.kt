package com.example.handheldapp.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.handheldapp.R
import com.example.handheldapp.data.model.DeliveryOrder
import com.example.handheldapp.databinding.ItemDeliveryOrderBinding


/**
 * Adapter untuk RecyclerView di MainActivity
 * Tampilkan list Delivery Orders dengan expand/collapse
 */
class DeliveryOrderAdapter(
    private val onItemClick: (DeliveryOrder) -> Unit
) : ListAdapter<DeliveryOrder, DeliveryOrderAdapter.ViewHolder>(DiffCallback()) {

    // Track expanded items by DO ID (String)
    private val expandedItems = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDeliveryOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick, expandedItems)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, expandedItems.contains(item.dohId.toString()))
    }

    fun toggleExpand(doId: String) {
        if (expandedItems.contains(doId)) {
            expandedItems.remove(doId)
        } else {
            expandedItems.add(doId)
        }
        notifyDataSetChanged()
    }

    fun collapseAll() {
        expandedItems.clear()
        notifyDataSetChanged()
    }

    class ViewHolder(
        private val binding: ItemDeliveryOrderBinding,
        private val onItemClick: (DeliveryOrder) -> Unit,
        private val expandedItems: MutableSet<String>
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentDo: DeliveryOrder? = null

        fun bind(deliveryOrder: DeliveryOrder, isExpanded: Boolean) {
            currentDo = deliveryOrder
            val context = binding.root.context

            val isCompleted = deliveryOrder.dohStatus?.lowercase() in listOf("selesai", "scan_completed", "completed")

            // === COMPACT HEADER INFO ===
            binding.tvDoNumber.text = deliveryOrder.dohNodo ?: "-"
            binding.tvSupplier.text = deliveryOrder.dohSupplier ?: "-"

            // Status Chip
            binding.chipStatus.text = deliveryOrder.getStatusDisplayText()
            binding.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, deliveryOrder.getStatusColorRes())
            )
            binding.chipStatus.setTextColor(Color.WHITE)

            // Progress Bar & Percentage (in header)
            val progress = deliveryOrder.getProgressPercentage()
            binding.progressBar.progress = progress
            binding.tvProgress.text = when(progress) {
                100 -> "✓"
                else -> "$progress%"
            }

            val pColor = when {
                progress >= 100 -> android.R.color.holo_green_dark
                progress >= 75 -> android.R.color.holo_blue_dark
                progress >= 50 -> android.R.color.holo_orange_dark
                else -> android.R.color.darker_gray
            }
            binding.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, pColor)
            )
            binding.tvProgress.setTextColor(ContextCompat.getColor(context, pColor))

            // === EXPANDED CONTENT ===
            binding.tvSuratJalan.text = "SJ: ${deliveryOrder.dohNoSj ?: "-"}"

            // Staging Area Chip
            if (deliveryOrder.hasStaging && deliveryOrder.stagingCode != null) {
                binding.chipStaging.text = "📍 ${deliveryOrder.stagingCode}"
                binding.chipStaging.setChipBackgroundColorResource(R.color.success)
                binding.chipStaging.setTextColor(Color.WHITE)
                binding.iconStaging.setColorFilter(
                    ContextCompat.getColor(context, R.color.success)
                )
            } else {
                binding.chipStaging.text = "⚠️ Pilih Staging"
                binding.chipStaging.setChipBackgroundColorResource(R.color.warning)
                binding.chipStaging.setTextColor(Color.parseColor("#5D4037"))
                binding.iconStaging.setColorFilter(
                    ContextCompat.getColor(context, R.color.warning)
                )
            }

            // === QTY INFO ===
            binding.tvQtyDo.text = formatQtyCompact(
                deliveryOrder.qtyKartonTarget,
                deliveryOrder.qtyPcsTarget
            )

            binding.tvQtyScan.text = formatQtyCompact(
                deliveryOrder.qtyKartonScanned,
                deliveryOrder.qtyPcsScanned
            )

            // Format selisih
            binding.tvSelisih.text = formatSelisihCompact(
                deliveryOrder.selisihKarton,
                deliveryOrder.selisihPcs
            )

            // Warna selisih
            val totalSelisih = deliveryOrder.selisihKarton + deliveryOrder.selisihPcs
            binding.tvSelisih.setTextColor(
                when {
                    totalSelisih > 0 -> Color.parseColor("#1B5E20")
                    totalSelisih < 0 -> Color.parseColor("#C62828")
                    else -> Color.parseColor("#E65100")
                }
            )

            // Items label
            if (deliveryOrder.items.isNullOrEmpty()) {
                binding.tvItemsLabel.text = "Tidak ada detail item"
                binding.tvItemsLabel.setTextColor(
                    ContextCompat.getColor(context, android.R.color.darker_gray)
                )
                binding.btnDetailScan.visibility = View.GONE
            } else {
                binding.tvItemsLabel.text = "Total: ${deliveryOrder.items.size} SKU"
                binding.tvItemsLabel.setTextColor(
                    ContextCompat.getColor(context, R.color.primary)
                )
                binding.btnDetailScan.visibility = View.VISIBLE

                binding.btnDetailScan.setOnClickListener {
                    val intent = android.content.Intent(context, com.example.handheldapp.ui.ScanHistoryActivity::class.java).apply {
                        putExtra("EXTRA_DO_ID", deliveryOrder.dohId)
                        putExtra("EXTRA_DO_NUMBER", deliveryOrder.dohNodo)
                        putExtra("EXTRA_DO_ITEMS", ArrayList(deliveryOrder.items))
                    }
                    context.startActivity(intent)
                }
            }

            // === TOMBOL MULAI SCAN ===
            binding.btnMulaiScan.setOnClickListener {
                onItemClick(deliveryOrder)
            }

            // === EXPAND/COLLAPSE ===
            binding.layoutExpandedContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.btnExpand.rotation = if (isExpanded) 180f else 0f

            val doIdStr = deliveryOrder.dohId.toString()

            // Header click to toggle expand
            binding.layoutHeader.setOnClickListener {
                toggleExpand(doIdStr)
            }

            // Expand button click
            binding.btnExpand.setOnClickListener {
                toggleExpand(doIdStr)
            }

            // === VISUAL STYLING FOR COMPLETED ===
            if (isCompleted) {
                binding.root.alpha = 0.7f
                binding.root.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
                binding.chipStatus.chipIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_lock_lock)
                binding.chipStatus.isChipIconVisible = true
            } else {
                binding.root.alpha = 1.0f
                binding.root.setCardBackgroundColor(Color.WHITE)
                binding.chipStatus.isChipIconVisible = false
            }

            // Card main area click to navigate (only when expanded)
            binding.root.setOnClickListener {
                if (isExpanded) {
                    onItemClick(deliveryOrder)
                } else {
                    toggleExpand(doIdStr)
                }
            }
        }

        private fun toggleExpand(doId: String) {
            val wasExpanded = expandedItems.contains(doId)

            if (wasExpanded) {
                expandedItems.remove(doId)
                animateCollapse()
            } else {
                expandedItems.add(doId)
                animateExpand()
            }
        }

        private fun animateExpand() {
            binding.layoutExpandedContent.visibility = View.VISIBLE
            binding.layoutExpandedContent.alpha = 0f
            binding.layoutExpandedContent.animate()
                .alpha(1f)
                .setDuration(200)
                .start()

            binding.btnExpand.animate()
                .rotation(180f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        private fun animateCollapse() {
            binding.layoutExpandedContent.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(Runnable {
                    binding.layoutExpandedContent.visibility = View.GONE
                })
                .start()

            binding.btnExpand.animate()
                .rotation(0f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        private fun formatQtyCompact(karton: Int, pcs: Int): String {
            return when {
                karton > 0 && pcs > 0 -> "$karton Ktn\n$pcs PCS"
                karton > 0 -> "$karton Ktn"
                pcs > 0 -> "$pcs PCS"
                else -> "0"
            }
        }

        private fun formatSelisihCompact(karton: Int, pcs: Int): String {
            val parts = mutableListOf<String>()
            if (karton != 0) {
                val prefix = if (karton > 0) "+" else ""
                parts.add("$prefix$karton Ktn")
            }
            if (pcs != 0) {
                val prefix = if (pcs > 0) "+" else ""
                parts.add("$prefix$pcs PCS")
            }
            return if (parts.isNotEmpty()) parts.joinToString("\n") else "0"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DeliveryOrder>() {
        override fun areItemsTheSame(oldItem: DeliveryOrder, newItem: DeliveryOrder): Boolean {
            return oldItem.dohId == newItem.dohId
        }

        override fun areContentsTheSame(oldItem: DeliveryOrder, newItem: DeliveryOrder): Boolean {
            return oldItem == newItem
        }
    }
}
