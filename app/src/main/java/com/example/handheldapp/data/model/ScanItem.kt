package com.example.handheldapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Data class untuk Scan Item (wdms_tr_tallysheet_det)
 * ★ UPDATED: Ditambah pcsPerKarton dan totalPcs untuk konversi
 * ★ UPDATED: Ditambah scannedBy dan scannedAt untuk tracking user
 */
@Parcelize
data class ScanItem(
    @SerializedName("id") val id: Int,
    @SerializedName("skuOrder") val skuOrder: String,
    @SerializedName("productName") val productName: String?,
    @SerializedName("qty") val qty: Int,
    @SerializedName("qtyKarton") val qtyKarton: Int?,
    @SerializedName("qtyPcs") val qtyPcs: Int?,
    @SerializedName("pcsPerKarton") val pcsPerKarton: Int = 1, // ★ Konversi PCS per Karton
    @SerializedName("totalPcs") val totalPcs: Int = 0, // ★ Total PCS hasil konversi
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("scannedBy") val scannedBy: String? = null, // ★ User yang scan
    @SerializedName("scannedAt") val scannedAt: String? = null  // ★ Waktu scan lengkap (yyyy-MM-dd HH:mm:ss)
) : Parcelable {

    /**
     * Calculate total PCS locally if not provided by API
     * Formula: totalPcs = qtyPcs + (qtyKarton × pcsPerKarton)
     */
    fun calculateTotalPcs(): Int {
        val pcs = qtyPcs ?: 0
        val karton = qtyKarton ?: 0
        val conversion = if (pcsPerKarton > 0) pcsPerKarton else 1
        return pcs + (karton * conversion)
    }

    /**
     * Get display total (use API value or calculate locally)
     */
    fun getDisplayTotalPcs(): Int {
        return if (totalPcs > 0) totalPcs else calculateTotalPcs()
    }

    @Parcelize
    data class BarcodeProduct(
        @SerializedName("sku") val sku: String,
        @SerializedName("product_name") val productName: String,
        @SerializedName("conversion") val conversion: String? = null,
        @SerializedName("pcs_per_karton") val pcsPerKarton: Int = 1
    ) : Parcelable

    data class BarcodeResponse(
        val success: Boolean,
        val data: List<BarcodeProduct>,
        val count: Int = 0,
        val message: String = ""
    )

    data class SubmitResponse(
        val success: Boolean,
        val message: String,
        @SerializedName("do_status") val doStatus: String? = null // ✅ Status DO terbaru (scanning/scan_completed/completed)
    )
}
