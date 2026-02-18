package com.example.handheldapp.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.handheldapp.R
import com.example.handheldapp.data.model.StagingArea
import com.example.handheldapp.databinding.ItemStagingAreaBinding

class StagingAreaAdapter(
    private val onItemClick: (StagingArea) -> Unit
) : ListAdapter<StagingArea, StagingAreaAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStagingAreaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemStagingAreaBinding,
        private val onItemClick: (StagingArea) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(stagingArea: StagingArea) {
            binding.apply {
                // Basic Info
                tvStageCode.text = stagingArea.stageCode
                tvStageName.text = stagingArea.stageName
                tvZone.text = stagingArea.zone ?: "-"
                tvStageType.text = stagingArea.stageType ?: "-"

                // Location
                val locationText = if (!stagingArea.locationDescription.isNullOrEmpty()) {
                    "Lokasi: ${stagingArea.locationDescription}"
                } else {
                    "Lokasi: -"
                }
                tvLocation.text = locationText

                // Status Chip
                chipStatus.text = stagingArea.getStatusText()
                when {
                    !stagingArea.isAvailable -> {
                        chipStatus.setChipBackgroundColorResource(android.R.color.darker_gray)
                    }
                    stagingArea.occupancyPercentage >= 80 -> {
                        chipStatus.setChipBackgroundColorResource(android.R.color.holo_orange_light)
                    }
                    else -> {
                        chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_light)
                    }
                }

                // Capacity
                tvCapacity.text = stagingArea.getCapacityText()
                tvPercentage.text = "${stagingArea.occupancyPercentage.toInt()}%"
                progressCapacity.max = 100
                progressCapacity.progress = stagingArea.occupancyPercentage.toInt()

                // Progress bar color based on occupancy
                val progressColor = when {
                    stagingArea.occupancyPercentage >= 90 -> android.R.color.holo_red_dark
                    stagingArea.occupancyPercentage >= 70 -> android.R.color.holo_orange_dark
                    else -> android.R.color.holo_green_dark
                }
                tvPercentage.setTextColor(ContextCompat.getColor(root.context, progressColor))

                // Card state
                if (stagingArea.isAvailable) {
                    root.alpha = 1.0f
                    root.isEnabled = true
                    root.strokeWidth = 1
                    root.strokeColor = ContextCompat.getColor(root.context, R.color.success)
                } else {
                    root.alpha = 0.5f
                    root.isEnabled = false
                    root.strokeWidth = 2
                    root.strokeColor = Color.parseColor("#BDBDBD")
                }

                // Click listener
                root.setOnClickListener {
                    if (stagingArea.isAvailable) {
                        onItemClick(stagingArea)
                    }
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<StagingArea>() {
            override fun areItemsTheSame(oldItem: StagingArea, newItem: StagingArea): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: StagingArea, newItem: StagingArea): Boolean {
                return oldItem == newItem
            }
        }
    }
}
