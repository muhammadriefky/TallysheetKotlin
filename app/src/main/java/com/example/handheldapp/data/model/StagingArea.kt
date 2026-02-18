package com.example.handheldapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class StagingArea(
    @SerializedName("id") val id: Int,
    @SerializedName("stage_code") val stageCode: String,
    @SerializedName("stage_name") val stageName: String,
    @SerializedName("zone") val zone: String?,
    @SerializedName("location_description") val locationDescription: String?,
    @SerializedName("stage_type") val stageType: String?,
    @SerializedName("max_capacity") val maxCapacity: Int,
    @SerializedName("current_occupancy") val currentOccupancy: Int,
    @SerializedName("remaining_capacity") val remainingCapacity: Int,
    @SerializedName("occupancy_percentage") val occupancyPercentage: Float,
    @SerializedName("status") val status: String,
    @SerializedName("is_available") val isAvailable: Boolean
) : Parcelable {

    fun getOccupancyColor(): Int {
        return when {
            occupancyPercentage >= 90 -> android.R.color.holo_red_dark
            occupancyPercentage >= 70 -> android.R.color.holo_orange_dark
            occupancyPercentage >= 50 -> android.R.color.holo_blue_dark
            else -> android.R.color.holo_green_dark
        }
    }

    fun getStatusText(): String {
        return if (isAvailable) {
            "Tersedia"
        } else if (currentOccupancy >= maxCapacity) {
            "Penuh"
        } else {
            "Tidak Tersedia"
        }
    }

    fun getCapacityText(): String {
        return "$currentOccupancy / $maxCapacity"
    }
}

@Parcelize
data class StagingAreaAssignment(
    @SerializedName("assignment_id") val assignmentId: Int,
    @SerializedName("staging_area_id") val stagingAreaId: Int,
    @SerializedName("staging_area_code") val stagingAreaCode: String,
    @SerializedName("staging_area_name") val stagingAreaName: String,
    @SerializedName("zone") val zone: String?,
    @SerializedName("status") val status: String,
    @SerializedName("assigned_at") val assignedAt: String?
) : Parcelable
