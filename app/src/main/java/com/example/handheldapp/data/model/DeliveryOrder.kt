package com.example.handheldapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Data class untuk Delivery Order yang disesuaikan dengan Laravel API
 * Field names HARUS match dengan response dari DeliveryOrderController@index
 */
@Parcelize
data class DeliveryOrder(
    @SerializedName("doh_id") val dohId: String,
    @SerializedName("doh_nodo") val dohNodo: String?,
    @SerializedName("doh_nosj") val dohNoSj: String?,
    @SerializedName("doh_supplier") val dohSupplier: String?,
    @SerializedName("doh_status") val dohStatus: String?,
    @SerializedName("staff_status") val staffStatus: String?,
    // manager_status removed - single level approval by Kepala Gudang only
    @SerializedName("total_items") val totalItems: Int,

    // Field lama (backward compatible, bisa null)
    @SerializedName("qty_do") val qtyDo: Int? = 0,
    @SerializedName("scanned_items") val scannedItems: Int? = 0,
    @SerializedName("selisih") val selisih: Int? = 0,

    // Field baru untuk pemisahan Karton dan PCS
    @SerializedName("qty_karton_target") val qtyKartonTarget: Int = 0,
    @SerializedName("qty_pcs_target") val qtyPcsTarget: Int = 0,
    @SerializedName("qty_karton_scanned") val qtyKartonScanned: Int = 0,
    @SerializedName("qty_pcs_scanned") val qtyPcsScanned: Int = 0,
    @SerializedName("selisih_karton") val selisihKarton: Int = 0,
    @SerializedName("selisih_pcs") val selisihPcs: Int = 0,

    // Staging Area info
    @SerializedName("staging_area_id") val stagingAreaId: Int? = null,
    @SerializedName("staging_code") val stagingCode: String? = null,
    @SerializedName("staging_name") val stagingName: String? = null,
    @SerializedName("has_staging") val hasStaging: Boolean = false,

    // Gudang info (untuk filter scan)
    @SerializedName("do_det_code_gudang") val doDetCodeGudang: String? = null,

    // Status flags from API
    @SerializedName("is_completed") val isCompleted: Boolean = false, // DO sudah selesai scan atau sudah approved
    @SerializedName("has_scans") val hasScans: Boolean = false, // DO sudah punya scan items
    @SerializedName("status_display") val statusDisplay: String? = null, // User-friendly status text from API

    @SerializedName("staff_name") val staffName: String?, // Tambahan untuk nama user
    @SerializedName("items") val items: List<DoItem>? = null // Detail SKU dalam DO
) : Parcelable {

    /**
     * Nested data class untuk item detail dalam DO
     */
    @Parcelize
    data class DoItem(
        @SerializedName("sku") val sku: String,
        @SerializedName("qty_target") val qtyTarget: Int,
        @SerializedName("unit") val unit: String, // karton/pcs/unknown
        @SerializedName("qty_karton_scanned") val qtyKartonScanned: Int = 0,
        @SerializedName("qty_pcs_scanned") val qtyPcsScanned: Int = 0,
        @SerializedName("in_do") val inDo: Boolean = true // Apakah SKU ada di DO
    ) : Parcelable {
        // Helper function untuk format tampilan
        fun getDisplayText(): String {
            val unitUpper = unit.uppercase()
            val scannedText = when {
                qtyKartonScanned > 0 && qtyPcsScanned > 0 ->
                    "Scan: $qtyKartonScanned Karton + $qtyPcsScanned PCS"
                qtyKartonScanned > 0 ->
                    "Scan: $qtyKartonScanned Karton"
                qtyPcsScanned > 0 ->
                    "Scan: $qtyPcsScanned PCS"
                else ->
                    "Belum scan"
            }
            return "• $sku: Target $qtyTarget $unitUpper | $scannedText"
        }
    }

    fun getProgressPercentage(): Int {
        return when (dohStatus?.lowercase()) {
            "arrival" -> 20
            "scanning" -> 50
            "selesai", "scan_completed", "completed" -> 75
            "approved_staff" -> 90
            "approved_manager", "approved" -> 100
            else -> 0
        }
    }

    fun getStatusDisplayText(): String {
        return when (dohStatus?.lowercase()) {
            "arrival" -> "Kedatangan"
            "scanning" -> "Scanning"
            "selesai", "scan_completed", "completed" -> "Selesai"
            "approved_staff" -> "Staff ✓"
            "approved_manager" -> "Manager ✓"
            else -> dohStatus?.replaceFirstChar { it.uppercase() } ?: "Pending"
        }
    }

    fun getStatusColorRes(): Int {
        return when (dohStatus?.lowercase()) {
            "scanning" -> android.R.color.holo_orange_dark
            "selesai", "scan_completed", "completed" -> android.R.color.holo_green_dark
            "approved_staff", "approved_manager" -> android.R.color.holo_blue_dark
            else -> android.R.color.darker_gray
        }
    }

    fun getSelisihColorRes(): Int {
        val totalSelisih = selisihKarton + selisihPcs
        return when {
            totalSelisih > 0 -> android.R.color.holo_green_dark
            totalSelisih < 0 -> android.R.color.holo_red_dark
            else -> android.R.color.darker_gray
        }
    }

    fun getSelisihText(): String {
        val totalSelisih = selisihKarton + selisihPcs
        return if (totalSelisih > 0) "+$totalSelisih" else "$totalSelisih"
    }

    // Helper untuk tampilan terpisah Karton dan PCS
    fun getTargetText(): String {
        val parts = mutableListOf<String>()
        if (qtyKartonTarget > 0) parts.add("$qtyKartonTarget Karton")
        if (qtyPcsTarget > 0) parts.add("$qtyPcsTarget PCS")
        return if (parts.isNotEmpty()) parts.joinToString(" + ") else "0"
    }

    fun getScannedText(): String {
        val parts = mutableListOf<String>()
        if (qtyKartonScanned > 0) parts.add("$qtyKartonScanned Karton")
        if (qtyPcsScanned > 0) parts.add("$qtyPcsScanned PCS")
        return if (parts.isNotEmpty()) parts.joinToString(" + ") else "Belum scan"
    }

    fun getSelisihDetailText(): String {
        val parts = mutableListOf<String>()
        if (qtyKartonTarget > 0 || qtyKartonScanned > 0) {
            val prefix = if (selisihKarton >= 0) "+" else ""
            parts.add("$prefix$selisihKarton Karton")
        }
        if (qtyPcsTarget > 0 || qtyPcsScanned > 0) {
            val prefix = if (selisihPcs >= 0) "+" else ""
            parts.add("$prefix$selisihPcs PCS")
        }
        return if (parts.isNotEmpty()) parts.joinToString(" | ") else "0"
    }

    // Helper untuk staging area
    fun getStagingDisplayText(): String {
        return if (hasStaging && !stagingCode.isNullOrEmpty()) {
            "$stagingCode - ${stagingName ?: ""}"
        } else {
            "Belum ada staging"
        }
    }

    fun needsStagingSelection(): Boolean {
        return !hasStaging && dohStatus?.lowercase() !in listOf("selesai", "scan_completed", "completed", "approved_staff", "approved_manager")
    }
}
